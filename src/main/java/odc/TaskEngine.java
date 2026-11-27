package odc;

import java.util.*;

/** Per-unit requests use cores, logical GPUs, GB and Mbit/s. Rates use 100000 subunits. */
public final class TaskEngine {
    static final int BANK=3, AI=4, INFERENCE=5, SCALE=100000;
    static final class Node {
        final UUID id;
        final int rack,net,cores,ram,disk,ramMHz;
        final int[] parts,gpu;

        Node(UUID id,int rack,int net,int[] parts) {
            this.id=id; this.rack=rack; this.net=net; this.parts=parts.clone();
            cores=ServerSpec.cpuCores(parts); ram=ServerSpec.ramGB(parts); disk=ServerSpec.diskGB(parts);
            ramMHz=ServerSpec.ramClock(parts); gpu=ServerSpec.gpuSlots(parts);
        }
        int cpuMHz(int core) {
            Part primary=Part.at(parts[ServerSpec.CPU]);
            return core<primary.cores?primary.mhz:Part.at(parts[ServerSpec.CPU2]).mhz;
        }
        int watts(int activeCores,int gpuMask) {
            int watts=0, firstGPU=0;
            for(int i=0;i<parts.length;i++) {
                Part model=Part.at(parts[i]);
                switch(model.kind) {
                    case PSU: break;
                    case CPU:
                        int used=i==ServerSpec.CPU?activeCores:Math.max(0,activeCores-Part.at(parts[ServerSpec.CPU]).cores);
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
        int fe(int watts) {
            int capacity=ServerSpec.psu(parts),draw=0;
            for(int slot=ServerSpec.PSU;slot<ServerSpec.CPU2;slot++) draw+=Part.at(parts[slot]).fe;
            return capacity==0?0:(int)(((long)watts*draw+capacity-1)/capacity);
        }
        @Override public boolean equals(Object obj) {
            if(!(obj instanceof Node)) return false;
            Node other=(Node)obj;
            return rack==other.rack && net==other.net && id.equals(other.id) && Arrays.equals(parts,other.parts);
        }
        @Override public int hashCode(){return id.hashCode();}
    }

    static final class Limit {
        int cpu=Integer.MAX_VALUE, mask=-1, ram=Integer.MAX_VALUE, net=Integer.MAX_VALUE, disk=Integer.MAX_VALUE;
        Limit() {}
        Limit(int cpu,int mask) { this.cpu=cpu; this.mask=mask; }
    }
    static class Task {
        final int id,type;
        int priority=5,cpu=1,gpus,ram=1,disk,net=8,group,unitLimit=Integer.MAX_VALUE,base=1;
        boolean strict,auto=true,enabled;
        long progress;
        final Map<UUID,Limit> manual=new HashMap<>();
        Task(int id,int type) {
            this.id=id; this.type=type;
            if(type==1 || type==2 || type==AI || type==INFERENCE) gpus=1;
            if(type==BANK) { ram=4; disk=8; net=16; }
            if(type==AI || type==INFERENCE) { ram=8; disk=type==AI?32:16; net=32; }
        }
    }
    static final class Slice {
        final int node,cpu,mask,ram,disk,net,work;
        Slice(int index,int units,int mask,long rate,Task task) {
            node=index; cpu=units*task.cpu; this.mask=mask; ram=units*task.ram;
            disk=units*task.disk; net=units*task.net; work=display(rate);
        }
    }
    static final class Result {
        final List<Slice> slices=new ArrayList<>();
        final Set<UUID> machines=new HashSet<>();
        int cpu,gpus,ram,disk,net,work,units;
        long rate;
        boolean full;
    }
    static final class Plan {
        final Map<Integer,Result> tasks=new LinkedHashMap<>();
        final int[] cpu,gpu,ram,disk,net,power;
        final Set<Integer> overload=new HashSet<>();
        Plan(int n) {cpu=new int[n]; gpu=new int[n]; ram=new int[n]; disk=new int[n]; net=new int[n]; power=new int[n];}
    }
    // Whole units per second for the existing terminal. Accounting retains the full fixed-point rate.
    static int display(long rate) {return (int)Math.min(Integer.MAX_VALUE,rate/SCALE);}
    static int bound(int units,int available,int perUnit) {return perUnit==0?units:Math.min(units,Math.max(0,available)/perUnit);}

    static Plan plan(List<Node> nodes,List<? extends Task> jobs,int[] switchCap,int wan,Map<UUID,Integer> groups,Set<Integer> excluded) {
        Plan plan=new Plan(nodes.size());
        int[] local=switchCap.clone();
        List<Task> sorted=new ArrayList<>(jobs);
        sorted.sort(Comparator.<Task>comparingInt(t->t.priority).reversed().thenComparingInt(t->t.id));
        Limit unlimited=new Limit();
        for(Task job:sorted) {
            Result result=new Result();
            plan.tasks.put(job.id,result);
            if(!job.enabled || job.cpu<0 || job.gpus<0 || job.ram<0 || job.disk<0 || job.net<0 ||
                    (job.cpu==0 && job.gpus==0) || job.base<=0) continue;
            for(int i=0;i<nodes.size() && result.units<job.unitLimit;i++) {
                Node node=nodes.get(i);
                Limit limit=job.auto?unlimited:job.manual.get(node.id);
                if(limit==null || excluded.contains(i) || node.net<=0 || (job.group!=0 && groups.getOrDefault(node.id,0)!=job.group)) continue;
                int units=job.unitLimit-result.units;
                units=bound(units,Math.min(limit.cpu,node.cores-plan.cpu[i]),job.cpu);
                units=bound(units,Math.min(limit.ram,node.ram-plan.ram[i]),job.ram);
                units=bound(units,Math.min(limit.disk,node.disk-plan.disk[i]),job.disk);
                units=bound(units,Math.min(Math.min(wan,local[node.rack]),Math.min(limit.net,node.net-plan.net[i])),job.net);
                int availableMask=0;
                for(int g=0;g<node.gpu.length;g++) {
                    int bit=1<<g;
                    if((plan.gpu[i]&bit)==0 && (limit.mask&bit)!=0 &&
                            (job.type!=2 && job.type!=AI && job.type!=INFERENCE || Part.at(node.gpu[g]).gb>=8)) availableMask|=bit;
                }
                units=bound(units,Integer.bitCount(availableMask),job.gpus);
                if(units<=0) continue;
                int mask=0;
                long rate=0;
                for(int unit=0;unit<units;unit++) {
                    long boost=SCALE+(job.ram>0?node.ramMHz:0);
                    int clocks=0;
                    for(int c=0;c<job.cpu;c++) clocks+=node.cpuMHz(plan.cpu[i]+unit*job.cpu+c);
                    if(job.cpu>0) boost+=clocks/job.cpu;
                    for(int g=0;g<job.gpus;g++) {
                        int bit=Integer.lowestOneBit(availableMask);
                        Part chip=Part.at(node.gpu[Integer.numberOfTrailingZeros(bit)]);
                        boost+=1000L*chip.gb+20L*chip.mhz;
                        mask|=bit; availableMask^=bit;
                    }
                    rate+=boost*job.base;
                }
                Slice slice=new Slice(i,units,mask,rate,job);
                result.rate+=rate; result.units+=units; result.machines.add(node.id); result.slices.add(slice);
                result.cpu+=slice.cpu; result.gpus+=Integer.bitCount(mask); result.ram+=slice.ram; result.disk+=slice.disk; result.net+=slice.net;
                plan.cpu[i]+=slice.cpu; plan.gpu[i]|=mask; plan.ram[i]+=slice.ram; plan.disk[i]+=slice.disk; plan.net[i]+=slice.net;
                local[node.rack]-=slice.net; wan-=slice.net;
            }
            result.work=display(result.rate);
            result.full=result.units>0 && (job.unitLimit==Integer.MAX_VALUE || result.units==job.unitLimit);
            if(job.strict && !result.full) {
                for(Slice s:result.slices) {
                    plan.cpu[s.node]-=s.cpu; plan.gpu[s.node]&=~s.mask; plan.ram[s.node]-=s.ram; plan.disk[s.node]-=s.disk; plan.net[s.node]-=s.net;
                    local[nodes.get(s.node).rack]+=s.net; wan+=s.net;
                }
                plan.tasks.put(job.id,new Result());
            }
        }
        for(int i=0;i<nodes.size();i++) if(!excluded.contains(i)) {
            Node node=nodes.get(i);
            int watts=node.watts(plan.cpu[i],plan.gpu[i]);
            if(watts>ServerSpec.psu(node.parts)) plan.overload.add(i);
            plan.power[i]=node.fe(watts);
        }
        return plan;
    }
}
