package odc;

import java.util.*;

/** Authoritative physical reservations. No job chooses replacement hardware during a tick. */
final class FixedResources {
    static String key(PoolResources.Binding b){return b.server+":"+b.kind+":"+b.slot+":"+b.unit+":"+b.model;}
    static PoolResources.Binding binding(PoolResources.Device d,int amount){return new PoolResources.Binding(d.server,d.kind,d.slot,d.unit,d.model,amount);}
    static boolean storage(PoolResources.Kind k){return k==PoolResources.Kind.HDD||k==PoolResources.Kind.SSD||k==PoolResources.Kind.NVME;}
    static Map<String,Integer> amounts(Collection<PoolResources.Binding> bs){Map<String,Integer> m=new LinkedHashMap<>();for(PoolResources.Binding b:bs)m.merge(key(b),b.amount,(a,c)->(int)Math.min(Integer.MAX_VALUE,(long)a+c));return m;}
    static Set<UUID> hosts(Collection<PoolResources.Binding> bs){Set<UUID> s=new HashSet<>();for(PoolResources.Binding b:bs)s.add(b.server);return s;}
    interface NetworkRoom {int free(UUID server,Map<String,Integer> draft);}
    static final class Ledger {
        NetworkRoom networkRoom;boolean routedNetwork;
        final List<TaskEngine.Node> nodes;final int[] caps;final int wan;final Set<Integer> excluded;
        final Map<UUID,Integer> index=new HashMap<>();final Map<String,PoolResources.Device> devices=new LinkedHashMap<>();final Map<String,Integer> held=new HashMap<>(),heldIO=new HashMap<>();
        final List<PoolResources.Device> links=new ArrayList<>();
        Ledger(List<TaskEngine.Node> nodes,int[] caps,int wan,Set<Integer> excluded,Collection<? extends TaskEngine.Task> jobs,TaskEngine.Task ignore){
            this.nodes=nodes;this.caps=caps;this.wan=wan;this.excluded=excluded;
            for(int i=0;i<nodes.size();i++){TaskEngine.Node n=nodes.get(i);index.put(n.id,i);for(PoolResources.Device d:PoolResources.catalog(n.id,n.parts)){devices.put(d.key(),d);if(d.kind==PoolResources.Kind.NETWORK)links.add(d);}}
            for(TaskEngine.Task t:jobs)if(t!=ignore)for(PoolResources.Binding b:t.bindings)if(t.enabled||b.storage()){held.merge(key(b),b.amount,(a,c)->(int)Math.min(Integer.MAX_VALUE,(long)a+c));if(t.enabled&&b.storage())heldIO.merge(key(b),b.io,(a,c)->(int)Math.min(Integer.MAX_VALUE,(long)a+c));}
        }
        int capacity(PoolResources.Device d){int i=index.get(d.server);return d.kind==PoolResources.Kind.NETWORK?Math.min(d.capacity,nodes.get(i).net):d.capacity;}
        int free(PoolResources.Device d,UUID pool,Map<String,Integer> draft){
            int i=index.get(d.server);if(excluded.contains(i)||!Objects.equals(nodes.get(i).pool,pool))return 0;
            long free=(long)capacity(d)-held.getOrDefault(d.key(),0)-draft.getOrDefault(d.key(),0);
            if(d.kind==PoolResources.Kind.NETWORK&&routedNetwork)return Math.max(0,capacity(d)-draft.getOrDefault(d.key(),0));
            if(d.kind==PoolResources.Kind.NETWORK){long room=wan,rack=caps[nodes.get(i).rack];for(PoolResources.Device other:links){long n=(long)held.getOrDefault(other.key(),0)+draft.getOrDefault(other.key(),0);room-=n;if(nodes.get(index.get(other.server)).rack==nodes.get(i).rack)rack-=n;}free=Math.min(free,Math.min(room,rack));if(networkRoom!=null)free=Math.min(free,networkRoom.free(d.server,draft));}
            return (int)Math.max(0,free);
        }
        int freeIO(PoolResources.Device d){return Math.max(0,d.speed-heldIO.getOrDefault(d.key(),0));}
        boolean valid(UUID pool,Collection<PoolResources.Binding> bs){Map<String,Integer> draft=new HashMap<>();for(PoolResources.Binding b:bs){PoolResources.Device d=devices.get(key(b));if(d==null||draft.containsKey(d.key())||!b.matches(d)||b.amount>free(d,pool,draft))return false;draft.merge(d.key(),b.amount,Integer::sum);}return true;}
        List<PoolResources.Device> family(UUID pool,String family){List<PoolResources.Device> list=new ArrayList<>();for(PoolResources.Device d:devices.values())if(Objects.equals(nodes.get(index.get(d.server)).pool,pool)&&d.family().equals(family))list.add(d);return list;}
        int max(UUID pool,List<PoolResources.Device> list,Collection<PoolResources.Binding> current){Map<String,Integer> chosen=amounts(current);long total=0;for(PoolResources.Device d:list){int n=free(d,pool,chosen);total+=n+chosen.getOrDefault(d.key(),0);chosen.merge(d.key(),n,Integer::sum);}return (int)Math.min(Integer.MAX_VALUE,total);}
        /** Change total of identical devices, keeping existing physical choices whenever possible. */
        boolean adjust(UUID pool,List<PoolResources.Device> list,List<PoolResources.Binding> draft,int target){
            if(target<0||target>max(pool,list,draft))return false;
            Set<String> keys=new HashSet<>();for(PoolResources.Device d:list)keys.add(d.key());Map<String,Integer> chosen=amounts(draft);
            long current=0;for(String k:keys)current+=chosen.getOrDefault(k,0);if(current==target)return true;
            List<PoolResources.Device> sorted=new ArrayList<>(list);Map<UUID,Long> room=new HashMap<>();for(PoolResources.Device d:sorted)room.merge(d.server,(long)free(d,pool,chosen),Long::sum);
            // Prefer hosts already selected, then the largest free block of this family.
            Set<UUID> selected=hosts(draft);sorted.sort(Comparator.<PoolResources.Device>comparingInt(d->selected.contains(d.server)?0:1).thenComparing(d->-room.get(d.server)).thenComparing(d->d.server.toString()).thenComparingInt(d->d.slot).thenComparingInt(d->d.unit));
            if(current>target){Collections.reverse(sorted);long left=current-target;for(PoolResources.Device d:sorted){int old=chosen.getOrDefault(d.key(),0),n=(int)Math.min(left,old);chosen.put(d.key(),old-n);left-=n;if(left==0)break;}}
            else{long left=target-current;for(PoolResources.Device d:sorted){int n=(int)Math.min(left,free(d,pool,chosen));chosen.merge(d.key(),n,Integer::sum);left-=n;if(left==0)break;}if(left!=0)return false;}
            draft.removeIf(b->keys.contains(key(b)));for(PoolResources.Device d:list){int n=chosen.getOrDefault(d.key(),0);if(n>0)draft.add(binding(d,n));}return true;
        }
    }
    static final class Evaluation { final TaskEngine.Result result=new TaskEngine.Result();final List<PoolResources.Binding> committed=new ArrayList<>();String error="",detail=""; }
    static Evaluation evaluate(TaskEngine.Task job,Ledger ledger){return evaluate(job,ledger,false);}
    static Evaluation evaluate(TaskEngine.Task job,Ledger ledger,boolean draft){
        Evaluation e=new Evaluation();TaskEngine.Result r=e.result;Workload w=Workload.ALL[job.type];
        if(job.bindings.isEmpty()){e.error="empty";return e;}
        if(!ledger.valid(job.pool,job.bindings)){e.error="unavailable";return e;}
        if(!draft){e.committed.addAll(job.bindings);Map<String,Integer> usedIO=new HashMap<>();for(PoolResources.Binding b:job.bindings)if(b.storage()){PoolResources.Device d=ledger.devices.get(key(b));int used=usedIO.merge(key(b),b.io,Integer::sum);if(used>ledger.freeIO(d)){e.error="unavailable";e.detail="Disk I/O: "+b.server.toString().substring(0,8)+" / slot "+b.slot;return e;}}}
        Map<Integer,List<PoolResources.Binding>> byNode=new TreeMap<>();for(PoolResources.Binding b:job.bindings)byNode.computeIfAbsent(ledger.index.get(b.server),k->new ArrayList<>()).add(b);
        for(Map.Entry<Integer,List<PoolResources.Binding>> entry:byNode.entrySet()){
            int i=entry.getKey();TaskEngine.Node n=ledger.nodes.get(i);BitSet cores=new BitSet();int mask=0,ram=0,disk=0,net=0;int[] held=new int[ServerSpec.PSU];double ramClock=0,diskSpeed=0;int io=0;List<Integer> clocks=new ArrayList<>();List<Part> chips=new ArrayList<>();List<PoolResources.Binding> disks=new ArrayList<>();
            for(PoolResources.Binding b:entry.getValue()){
                PoolResources.Device d=ledger.devices.get(key(b));switch(b.kind){
                case CPU: int core=b.unit+(b.slot==ServerSpec.CPU2?Part.at(n.parts[ServerSpec.CPU]).cores:0);cores.set(core);clocks.add(d.speed);break;
                case GPU: int g=b.unit;for(int k=ServerSpec.PCIE;k<b.slot;k++)g+=Part.at(n.parts[k]).chips;mask|=1<<g;if(d.vram>=w.vram)chips.add(Part.at(b.model));break;
                case RAM: ram+=b.amount;ramClock+=b.amount*(double)d.speed;break;
                case NETWORK: net+=b.amount*10;break;
                default: disk+=b.amount;held[b.slot]+=b.amount;diskSpeed+=b.amount*(double)d.speed;disks.add(b);io+=draft?ledger.freeIO(d):b.io;break;
                }
            }
            int units=Integer.MAX_VALUE;units=TaskEngine.bound(units,cores.cardinality(),job.cpu);units=TaskEngine.bound(units,chips.size(),job.gpus);units=TaskEngine.bound(units,ram,job.ram);units=TaskEngine.bound(units,disk,job.disk);units=TaskEngine.bound(units,io,w.io);units=TaskEngine.bound(units,net,job.net);
            if(units==Integer.MAX_VALUE||units<=0){e.error="local requirements";e.detail=n.id.toString().substring(0,8)+": CPU "+cores.cardinality()+"/"+job.cpu+" GPU "+chips.size()+"/"+job.gpus+" RAM "+ram+"/"+job.ram+" GB; disk "+disk+"/"+job.disk+" GB; I/O "+io+"/"+w.io+" MB/s; NET "+net/10.0+"/"+job.net/10.0;continue;}
            // I/O is a separate, fixed reservation. A paused task holds disk space, not throughput.
            if(draft){int need=units*w.io;disks.sort(Comparator.comparingInt((PoolResources.Binding b)->ledger.devices.get(key(b)).speed).reversed().thenComparingInt(b->b.slot));for(PoolResources.Binding b:entry.getValue())if(!b.storage())e.committed.add(b);
                for(PoolResources.Binding b:disks){int amount=Math.min(need,ledger.freeIO(ledger.devices.get(key(b))));need-=amount;e.committed.add(new PoolResources.Binding(b.server,b.kind,b.slot,b.unit,b.model,b.amount,amount));}
            }
            // Existing score coefficients; only complete local units perform work.
            Collections.sort(clocks,Collections.reverseOrder());chips.sort(Comparator.comparingDouble((Part p)->1000*p.gb*w.weights[2]+20*p.mhz*w.weights[1]).reversed());
            long rate=0;for(int u=0;u<units;u++){double boost=TaskEngine.SCALE+(job.ram>0?ramClock/Math.max(1,ram)*w.weights[3]:0)+(job.disk>0?10*diskSpeed/Math.max(1,disk)*w.weights[4]:0);for(int c=0;c<job.cpu;c++)boost+=w.weights[0]*clocks.get(u*job.cpu+c)/job.cpu;for(int g=0;g<job.gpus;g++){Part p=chips.get(u*job.gpus+g);boost+=(1000*p.gb*w.weights[2]+20*p.mhz*w.weights[1])/job.gpus;}rate+=Math.round(boost*w.x*job.base);}
            TaskEngine.Slice slice=new TaskEngine.Slice(i,units,mask,rate,job);slice.host=n.id;slice.cores=cores;r.slices.add(slice);r.rate+=rate;r.units+=units;r.machines.add(n.id);r.placement.put(n.id,held);
        }
        for(PoolResources.Binding b:job.bindings)switch(b.kind){case CPU:r.cpu+=b.amount;break;case GPU:r.gpus+=b.amount;break;case RAM:r.ram+=b.amount;break;case NETWORK:r.net+=b.amount*10;break;default:r.disk+=b.amount;break;}
        r.work=TaskEngine.display(r.rate);r.full=r.units>0&&e.error.isEmpty();if(r.units<=0&&e.error.isEmpty())e.error="local requirements";return e;
    }
    /** Reservations apply globally, even across a split network; only local jobs may execute. */
    static TaskEngine.Plan plan(List<TaskEngine.Node> nodes,List<? extends TaskEngine.Task> jobs,Set<Integer> local,int[] caps,int wan,Set<Integer> excluded){
        return plan(nodes,jobs,local,caps,wan,excluded,null);
    }
    static TaskEngine.Plan plan(List<TaskEngine.Node> nodes,List<? extends TaskEngine.Task> jobs,Set<Integer> local,int[] caps,int wan,Set<Integer> excluded,Set<Integer> networkBlocked){
        TaskEngine.Plan p=new TaskEngine.Plan(nodes.size());p.usedCores=new BitSet[nodes.size()];p.freeSpace=new int[nodes.size()][ServerSpec.PSU];p.freeIO=new int[nodes.size()][ServerSpec.PSU];
        Ledger all=new Ledger(nodes,caps,wan,excluded,jobs,null);BitSet[] runningCores=new BitSet[nodes.size()];int[] runningGpu=new int[nodes.size()];
        for(int i=0;i<nodes.size();i++){p.usedCores[i]=new BitSet();runningCores[i]=new BitSet();for(int s=ServerSpec.SATA;s<ServerSpec.PSU;s++){Part d=Part.at(nodes.get(i).parts[s]);p.freeSpace[i][s]=d.speed>0?d.gb:0;p.freeIO[i][s]=d.speed;}}
        for(PoolResources.Device d:all.devices.values()){
            int i=all.index.get(d.server),used=Math.min(all.capacity(d),all.held.getOrDefault(d.key(),0));if(used<=0)continue;
            switch(d.kind){case CPU:p.usedCores[i].set(d.unit+(d.slot==ServerSpec.CPU2?Part.at(nodes.get(i).parts[ServerSpec.CPU]).cores:0));p.cpu[i]+=used;break;
            case GPU:int g=d.unit;for(int k=ServerSpec.PCIE;k<d.slot;k++)g+=Part.at(nodes.get(i).parts[k]).chips;p.gpu[i]|=1<<g;break;
            case RAM:p.ram[i]+=used;break;case NETWORK:p.net[i]+=used*10;break;default:p.disk[i]+=used;p.freeSpace[i][d.slot]-=used;p.freeIO[i][d.slot]=Math.max(0,d.speed-all.heldIO.getOrDefault(d.key(),0));break;}
        }
        for(TaskEngine.Task t:jobs)if(local.contains(t.id)){
            TaskEngine.Result r=new TaskEngine.Result();p.tasks.put(t.id,r);if(!t.enabled||networkBlocked!=null&&networkBlocked.contains(t.id))continue;
            Ledger jobLedger=new Ledger(nodes,caps,wan,excluded,jobs,t);jobLedger.routedNetwork=networkBlocked!=null;Evaluation e=evaluate(t,jobLedger);if(!e.error.isEmpty())continue;
            r=e.result;p.tasks.put(t.id,r);for(TaskEngine.Slice s:r.slices){runningCores[s.node].or(s.cores);runningGpu[s.node]|=s.mask;}
        }
        for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){TaskEngine.Node n=nodes.get(i);int watts=n.watts(runningCores[i],runningGpu[i]);int input=n.inputWatts(watts);if(input<0)p.overload.add(i);else p.power[i]=input;}
        return p;
    }
}
