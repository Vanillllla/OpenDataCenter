package odc;

import java.util.*;

/** Per-unit requests use cores, logical GPUs, GB and Mbit/s. Rates use 100000 subunits. */
public final class TaskEngine {
    static final int BANK=3, AI=4, INFERENCE=5, SCALE=100000;
    static final class Node {
        final UUID id,pool;
        final int rack,net,cores,ram,disk,ramMHz;
        final int[] parts,gpu;
        final ChassisSpec chassis;

        Node(UUID id,int rack,int net,int[] parts) {
            this(id,rack,net,parts,null);
        }
        Node(UUID id,int rack,int net,int[] parts,UUID pool) {
            this(id,rack,net,parts,pool,null);
        }
        Node(UUID id,int rack,int net,int[] parts,UUID pool,ChassisSpec chassis){
            this.id=id;this.pool=pool;this.chassis=chassis; this.rack=rack; this.net=net; this.parts=ServerSpec.upgradeParts(parts).clone();
            parts=this.parts;
            cores=ServerSpec.cpuCores(parts); ram=ServerSpec.ramGB(parts); disk=ServerSpec.diskGB(parts);
            ramMHz=ServerSpec.ramClock(parts); gpu=ServerSpec.gpuSlots(parts);
        }
        int cpuMHz(int core) {
            Part primary=Part.at(parts[ServerSpec.CPU]);
            return core<primary.cores?primary.mhz:Part.at(parts[ServerSpec.CPU2]).mhz;
        }
        int watts(int activeCores,int gpuMask){BitSet cores=new BitSet();cores.set(0,activeCores);return watts(cores,gpuMask);}
        int watts(BitSet allocated,int gpuMask) {
            int watts=0, firstGPU=0;
            for(int i=0;i<parts.length;i++) {
                Part model=Part.at(parts[i]);
                switch(model.kind) {
                    case PSU: break;
                    case CPU:
                        int offset=i==ServerSpec.CPU?0:Part.at(parts[ServerSpec.CPU]).cores;int used=allocated.get(offset,offset+model.cores).cardinality();
                        watts+=deviceWatts(model.watts,Math.min(model.cores,used),model.cores);
                        break;
                    case GPU:
                        int mask=(1<<model.chips)-1;
                        watts+=deviceWatts(model.watts,Integer.bitCount((gpuMask>>>firstGPU)&mask),model.chips);
                        firstGPU+=model.chips;
                        break;
                    default: watts+=model.watts;
                }
            }
            return watts;
        }
        private static int deviceWatts(int rated,int used,int capacity) {
            return (rated*(capacity+9*used)+10*capacity-1)/(10*capacity);
        }
        int inputWatts(int componentWatts){return ServerSpec.inputWatts(parts,componentWatts);}
        @Override public boolean equals(Object obj) {
            if(!(obj instanceof Node)) return false;
            Node other=(Node)obj;
            return rack==other.rack && net==other.net && Objects.equals(id,other.id) && Objects.equals(pool,other.pool) && chassis==other.chassis && Arrays.equals(parts,other.parts);
        }
        @Override public int hashCode(){return Objects.hashCode(id);}
    }

    static final class Limit {
        BitSet allowedCores;
        int cpu=Integer.MAX_VALUE, mask=-1, io=Integer.MAX_VALUE, ram=Integer.MAX_VALUE, net=Integer.MAX_VALUE, disk=Integer.MAX_VALUE;
        Limit() {}
        Limit(int cpu,int mask) { this.cpu=cpu; this.mask=mask; }
    }
    static class Task {
        final int id,type;
        int cpu=1,gpus,ram=1,disk,net=8,unitLimit=Integer.MAX_VALUE,base=1;
        boolean strict,auto=true,enabled;
        UUID pool;
        final List<PoolResources.Binding> bindings=new ArrayList<>();
        long progress;
        final Map<UUID,int[]> retained=new HashMap<>();
        final Map<UUID,Limit> manual=new HashMap<>();
        Task(int id,int type) {
            this.id=id; this.type=type;
            Workload w=Workload.ALL[type];
            cpu=w.cpu;gpus=w.gpu;ram=w.ram;disk=w.disk;net=(int)Math.round(w.net*80);

        }
    }
    static final class Slice {
        UUID host;BitSet cores;final int node,cpu,mask,ram,disk,net,work;
        Slice(int index,int units,int mask,long rate,Task task) {
            node=index; cpu=units*task.cpu; this.mask=mask; ram=units*task.ram;
            disk=units*task.disk; net=units*task.net; work=display(rate);
        }
    }
    static final class Result {
        final List<Slice> slices=new ArrayList<>();
        final Map<UUID,int[]> placement=new HashMap<>();
        final Set<UUID> machines=new HashSet<>();
        int cpu,gpus,ram,disk,net,work,units;
        long rate;
        boolean full;
    }
    static final class Plan {
        final Map<Integer,Result> tasks=new LinkedHashMap<>();
        final int[] cpu,gpu,ram,disk,net,power;
        BitSet[] usedCores;int[][] freeSpace,freeIO; final Set<Integer> overload=new HashSet<>();
        Plan(int n) {cpu=new int[n]; gpu=new int[n]; ram=new int[n]; disk=new int[n]; net=new int[n]; power=new int[n];}
    }
    // Whole units per second for the existing terminal. Accounting retains the full fixed-point rate.
    static int display(long rate) {return (int)Math.min(Integer.MAX_VALUE,rate/SCALE);}
    static int bound(int units,int available,int perUnit) {return perUnit==0?units:Math.min(units,Math.max(0,available)/perUnit);}

    static Plan plan(List<Node> nodes,List<? extends Task> jobs,int[] switchCap,int wan,Map<UUID,Integer> ignored,Set<Integer> excluded) {return plan(nodes,jobs,switchCap,wan,excluded);}
    static Plan plan(List<Node> nodes,List<? extends Task> jobs,int[] switchCap,int wan,Set<Integer> excluded) {
        Plan plan=new Plan(nodes.size());
        plan.usedCores=new BitSet[nodes.size()];for(int i=0;i<nodes.size();i++)plan.usedCores[i]=new BitSet();
        int[] local=switchCap.clone();
        for(int i=0;i<local.length;i++) local[i]*=10;
        wan*=10;
        int[][] space=new int[nodes.size()][ServerSpec.PSU], io=new int[nodes.size()][ServerSpec.PSU];
        for(int i=0;i<nodes.size();i++) for(int slot=ServerSpec.SATA;slot<ServerSpec.PSU;slot++){
            Part drive=Part.at(nodes.get(i).parts[slot]);
            if(drive.speed>0){space[i][slot]=drive.gb;io[i][slot]=drive.speed;}
        }
        List<Task> sorted=new ArrayList<>(jobs);
        // Legacy autonomous reference planner used by regression tests; live execution uses FixedResources.
        sorted.sort(Comparator.comparingInt(t->t.id));
        Limit unlimited=new Limit();
        for(Task task:sorted) if(!task.enabled) for(int i=0;i<nodes.size();i++){
            int[] held=task.retained.get(nodes.get(i).id);
            if(held!=null)for(int k=ServerSpec.SATA;k<ServerSpec.PSU;k++){
                int keep=Math.min(space[i][k],held[k]);space[i][k]-=keep;plan.disk[i]+=keep;
            }
        }
        for(Task job:sorted) {
            Workload spec=Workload.ALL[job.type];
            Map<Integer,int[][]> rollback=new HashMap<>();
            Result result=new Result();
            plan.tasks.put(job.id,result);
            if(!job.enabled || job.cpu<0 || job.gpus<0 || job.ram<0 || job.disk<0 || job.net<0 ||
                    (job.cpu==0 && job.gpus==0) || job.base<=0) continue;
            for(int i=0;i<nodes.size() && result.units<job.unitLimit;i++) {
                Node node=nodes.get(i);
                Limit limit=job.auto?unlimited:job.manual.get(node.id);
                if(limit==null || excluded.contains(i) || node.net<=0 || (job.pool!=null && !job.pool.equals(node.pool))) continue;
                int units=job.unitLimit-result.units;
                BitSet availableCores=new BitSet();availableCores.set(0,node.cores);availableCores.andNot(plan.usedCores[i]);
                if(limit.allowedCores!=null)availableCores.and(limit.allowedCores);
                BitSet chosenCores=new BitSet();
                units=bound(units,Math.min(limit.cpu,availableCores.cardinality()),job.cpu);
                units=bound(units,Math.min(limit.ram,node.ram-plan.ram[i]),job.ram);
                units=bound(units,Math.min(limit.disk,node.disk-plan.disk[i]),job.disk);
                units=bound(units,Math.min(Math.min(wan,local[node.rack]),Math.min(limit.net,10*node.net-plan.net[i])),job.net);
                int availableMask=0;
                for(int g=0;g<node.gpu.length;g++) {
                    int bit=1<<g;
                    if((plan.gpu[i]&bit)==0 && (limit.mask&bit)!=0 &&
                            Part.at(node.gpu[g]).gb>=spec.vram) availableMask|=bit;
                }
                units=bound(units,limit.io,spec.io);
                units=bound(units,Integer.bitCount(availableMask),job.gpus);
                if(units<=0) continue;
                int mask=0;int[] held=new int[ServerSpec.PSU];
                if(job.strict) rollback.put(i,new int[][]{space[i].clone(),io[i].clone()});
                long rate=0;
                for(int unit=0;unit<units;unit++) {
                    double[] weights=spec.weights;
                    double boost=SCALE+(job.ram>0?node.ramMHz*weights[3]:0);
                    if(job.disk>0 || spec.io>0){
                        int drive=-1;
                        for(int slot=ServerSpec.SATA;slot<ServerSpec.PSU;slot++)
                            if(space[i][slot]>=job.disk && io[i][slot]>=spec.io &&
                                (drive<0 || Part.at(node.parts[slot]).speed>Part.at(node.parts[drive]).speed)) drive=slot;
                        if(drive>=0){
                            held[drive]+=job.disk;space[i][drive]-=job.disk;io[i][drive]-=spec.io;
                            boost+=10*Part.at(node.parts[drive]).speed*weights[4];
                        }else{
                            // Split data proportionally to the throughput each actual drive can supply.
                            int[] pieces=new int[ServerSpec.PSU];int left=job.disk;
                            for(int k=ServerSpec.SATA;k<ServerSpec.PSU && left>0;k++){
                                int bySpeed=spec.io==0?left:(int)((long)io[i][k]*job.disk/spec.io);
                                pieces[k]=Math.min(left,Math.min(space[i][k],bySpeed));left-=pieces[k];
                            }
                            if(left>0 || job.disk==0){units=unit;break;}
                            for(int k=ServerSpec.SATA;k<ServerSpec.PSU;k++)if(pieces[k]>0){
                                held[k]+=pieces[k];space[i][k]-=pieces[k];
                                io[i][k]-=(int)(((long)spec.io*pieces[k]+job.disk-1)/job.disk);
                                boost+=10.0*Part.at(node.parts[k]).speed*weights[4]*pieces[k]/job.disk;
                            }
                        }
                    }
                    int clocks=0;
                    for(int c=0;c<job.cpu;c++){int core=availableCores.nextSetBit(0);availableCores.clear(core);chosenCores.set(core);clocks+=node.cpuMHz(core);}
                    if(job.cpu>0) boost+=weights[0]*clocks/job.cpu;
                    for(int g=0;g<job.gpus;g++) {
                        int bit=Integer.lowestOneBit(availableMask);
                        Part chip=Part.at(node.gpu[Integer.numberOfTrailingZeros(bit)]);
                        boost+=(1000*chip.gb*weights[2]+20*chip.mhz*weights[1])/job.gpus;
                        mask|=bit; availableMask^=bit;
                    }
                    rate+=Math.round(boost*spec.x*job.base);
                }
                if(units==0)continue;
                Slice slice=new Slice(i,units,mask,rate,job);
                slice.host=node.id;slice.cores=chosenCores;plan.usedCores[i].or(chosenCores);result.rate+=rate; result.units+=units; result.machines.add(node.id);result.placement.put(node.id,held); result.slices.add(slice);
                result.cpu+=slice.cpu; result.gpus+=Integer.bitCount(mask); result.ram+=slice.ram; result.disk+=slice.disk; result.net+=slice.net;
                plan.cpu[i]+=slice.cpu; plan.gpu[i]|=mask; plan.ram[i]+=slice.ram; plan.disk[i]+=slice.disk; plan.net[i]+=slice.net;
                local[node.rack]-=slice.net; wan-=slice.net;
            }
            result.work=display(result.rate);
            result.full=result.units>0 && (job.unitLimit==Integer.MAX_VALUE || result.units==job.unitLimit);
            if(job.strict && !result.full) {
                for(Map.Entry<Integer,int[][]> e:rollback.entrySet()){space[e.getKey()]=e.getValue()[0];io[e.getKey()]=e.getValue()[1];}
                for(Slice s:result.slices) {
                    plan.usedCores[s.node].andNot(s.cores);plan.cpu[s.node]-=s.cpu; plan.gpu[s.node]&=~s.mask; plan.ram[s.node]-=s.ram; plan.disk[s.node]-=s.disk; plan.net[s.node]-=s.net;
                    local[nodes.get(s.node).rack]+=s.net; wan+=s.net;
                }
                plan.tasks.put(job.id,new Result());
            }
        }
        for(int i=0;i<nodes.size();i++) if(!excluded.contains(i)) {
            Node node=nodes.get(i);
            int watts=node.watts(plan.usedCores[i],plan.gpu[i]);
            int input=node.inputWatts(watts);
            if(input<0)plan.overload.add(i);
            else plan.power[i]=input;
        }
        plan.freeSpace=space;plan.freeIO=io;return plan;
    }
}
