package odc;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
/** Server-authoritative persistent jobs and wallets. Scope: task anchor controller + owner. */
public final class TaskData extends WorldSavedData {
    static final String KEY="odc_compute_v050"; static final String[] COINS={"NCoin","KriptoCoin"};static final String[] TYPES={"KriptoCoin / CoreHash","KriptoCoin / ParallelHash","KriptoCoin / MemoryHash","Bank service","AI training","AI inference"};
    static final class Job extends TaskEngine.Task { UUID anchor,owner; int work,usedCPU,usedGPU,usedRAM,usedDisk,usedNet;long remainder,earned,elapsed,notice,pending;
        final long[] payments=new long[4];
        Set<UUID> machines=Collections.emptySet();
        boolean served;
        final NBTTagCompound terms=new NBTTagCompound();
        long cachedRate,lastAccounting;
        int promised,paidCount;
        boolean periodic(){return Workload.service(type);}
        String name;boolean full; Job(int id,int type,UUID a,UUID o){super(id,type);anchor=a;owner=o;name="task.odc."+type;terms.setUniqueId("Key",UUID.randomUUID());} }
    final List<Job> jobs=new ArrayList<>();final Map<UUID,Integer> groups=new HashMap<>();final Map<UUID,long[]> wallets=new HashMap<>();final List<String> log=new ArrayList<>();int next=0;long ticks;int[] rate={1000,1500};
    public TaskData(){super(KEY);}public TaskData(String name){super(name);}
    static TaskData get(World w){net.minecraft.world.storage.MapStorage s=w.getPerWorldStorage();TaskData d=(TaskData)s.getOrLoadData(TaskData.class,KEY);if(d==null){d=new TaskData();s.setData(KEY,d);}return d;}
    long[] wallet(UUID a){return wallets.computeIfAbsent(a,k->new long[2]);}
    long credit(UUID a,int coin,long v){long paid=a==null?0:EconomyEngine.credit(wallet(a),coin,v);if(paid>0)markDirty();return paid;}
    long jobCredit(Job j,int currency,long amount){
        long repayment=0;
        if(currency==0){
            NBTTagCompound book=market.owner(j.owner);
            repayment=Math.min(amount,book.getLong("Debt"));
            book.setLong("Debt",book.getLong("Debt")-repayment);
        }
        return repayment+credit(j.anchor,currency,amount-repayment);
    }
    boolean transfer(UUID from,UUID to,int coin,long v){boolean ok=!from.equals(to)&&EconomyEngine.transfer(wallet(from),wallet(to),coin,v);if(ok)markDirty();return ok;}
    boolean exchange(UUID a,int coin,long amount){boolean ok=coin>=1&&coin<2&&EconomyEngine.exchange(wallet(a),coin,amount,rate[coin]);if(ok)markDirty();return ok;}
    final Map<UUID,TaskConsole.Domain> domains=new HashMap<>();
    final ContractMarket market=new ContractMarket();
    private final Map<UUID,AllocationCache> cache=new HashMap<>();
    private final Set<UUID> usedCaches=new HashSet<>();
    private long worldMillis=System.currentTimeMillis();
    long now(){return worldMillis+(lastNanos==0?0:Math.max(0,(System.nanoTime()-lastNanos)/1000000));}
    private long lastNanos; long elapsedMillis;
    TaskEngine.Plan plan(UUID anchor,List<TaskEngine.Node> nodes,List<Job> jobs,int[] caps,int wan,Set<Integer> excluded) {
        if(anchor==null) return TaskEngine.plan(nodes,jobs,caps,wan,groups,excluded);
        usedCaches.add(anchor);
        return cache.computeIfAbsent(anchor,id->new AllocationCache()).get(nodes,jobs,caps,wan,groups,excluded);
    }
    void clock(){
        ticks++;
        long now=System.nanoTime();
        if(lastNanos==0) lastNanos=now;
        elapsedMillis=(now-lastNanos)/1_000_000;
        lastNanos+=elapsedMillis*1_000_000;worldMillis+=elapsedMillis;
        if(ticks%60==0) rate[1]=EconomyEngine.rate(rate[1],ticks,1);
        long wall=now();for(Job j:jobs){j.earned=0;settle(j,wall);}market.tick(this,wall);
        for(Job j:jobs) j.served=false;
        markDirty();
    }
    void event(UUID owner,String s){log.add(owner+"|["+ticks+"s] "+s);if(log.size()>64)log.remove(0);markDirty();}
    List<String> eventsFor(UUID owner){List<String> out=new ArrayList<>();String prefix=owner+"|";for(String l:log)if(l.startsWith(prefix))out.add(l.substring(prefix.length()));return out;}
    // pending and remainder retain fractional milli-coins (or contract points) across pauses.
    void account(Job j,TaskEngine.Result allocation) {
        if(j.served || !j.enabled || allocation.rate<=0) return;
        if(j.periodic() && (allocation.units<j.promised || !allocation.machines.containsAll(j.machines))) {
            interrupt(j,false);
            return;
        }
        if(!j.terms.hasKey("Start"))j.terms.setLong("Start",now());
        if(Workload.contract(j.type)&&j.terms.getBoolean("Fixed")&&!j.terms.getBoolean("Locked")){
            j.strict=true;j.unitLimit=allocation.units;j.terms.setBoolean("Locked",true);
            for(TaskEngine.Slice slice:allocation.slices){
                TaskEngine.Limit lock=new TaskEngine.Limit(slice.cpu,slice.mask);lock.allowedCores=(BitSet)slice.cores.clone();
                lock.ram=slice.ram;lock.net=slice.net;lock.disk=slice.disk;
                lock.io=Workload.ALL[j.type].io*slice.cpu/Math.max(1,j.cpu);
                j.manual.put(slice.host,lock);
            }
            j.manual.keySet().retainAll(allocation.machines);j.auto=false;
        }
        j.served=true;
        settle(j,now());
        j.cachedRate=allocation.rate;j.promised=allocation.units;
        j.retained.clear();j.retained.putAll(allocation.placement);j.machines=allocation.machines;
        }
    void settle(Job j,long now){
        long from=j.lastAccounting;
        j.lastAccounting=now;
        if(from==0 || j.cachedRate<=0 || !j.enabled)return;
        long deadline=j.terms.getLong("Deadline");
        long remainingMs=Math.max(0,(deadline>0?Math.min(now,deadline):now)-from);
        long previous=j.cachedRate;
        long interval=j.periodic()?600_000:30_000;
        while(remainingMs>0 && j.enabled) {
            long step=Math.min(remainingMs,interval-j.elapsed);
            long period=Workload.contract(j.type)?1000:interval;
            double factor=j.periodic()?ContractMarket.serviceFactor:Workload.contract(j.type)?ContractMarket.scoreFactor:ContractMarket.mineFactor;
            long perSecond=Math.round(previous*factor*(Workload.contract(j.type)?1:1000));
            // Split off the whole part first to avoid multiplying a large cluster rate by a ten-minute period.
            long divisor=period*TaskEngine.SCALE;
            long scaled=(perSecond%divisor)*step+j.remainder;
            // 1 normalized reward = 1 milli-coin or one contract point before type-specific balancing.
            long whole=(perSecond/divisor)*step+scaled/divisor;
            j.remainder=scaled%(period*TaskEngine.SCALE);
            remainingMs-=step;
            if(Workload.contract(j.type)) {
                j.progress=Math.min(j.terms.getLong("Goal"),j.progress+whole);
                if(j.progress==j.terms.getLong("Goal")) {
                    j.earned=jobCredit(j,0,ContractMarket.payout(j,now()));
                    j.enabled=false;
                    j.machines=Collections.emptySet();
                    j.terms.setLong("Received",j.earned);market.end(this,j,"completed",0);
                }
                continue;
            }
            j.pending+=whole;
            j.elapsed+=step;
            if(j.elapsed==interval) {
                long amount=jobCredit(j,j.periodic()?0:1,j.pending);
                j.earned+=amount;j.terms.setLong("Received",j.terms.getLong("Received")+amount);
                if(j.periodic()) {
                    System.arraycopy(j.payments,1,j.payments,0,3);
                    j.payments[3]=amount;j.paidCount=Math.min(4,j.paidCount+1);
                }
                j.pending=0;
                j.elapsed=0;
            }
        }
    }
    void finishCycle() {
        for(Job j:jobs) if(!j.served && !j.machines.isEmpty()) {
            if(j.periodic()) interrupt(j,false);
            else {j.machines=Collections.emptySet();j.cachedRate=0;}
        }
        domains.keySet().retainAll(usedCaches);cache.keySet().retainAll(usedCaches);
        usedCaches.clear();
    }
    private void interrupt(Job j,boolean planned) {
        settle(j,now());
        if(!planned || j.notice==0 || now()<j.notice) {
            long penalty=Arrays.stream(j.payments).sum()+(4-j.paidCount)*Math.round(j.cachedRate*ContractMarket.serviceFactor*1000/TaskEngine.SCALE);
            j.terms.setBoolean("Fault",true);market.record(this,j,"interrupted",penalty);
            long paid=Math.min(Math.max(0,wallet(j.anchor)[0]),penalty);
            wallet(j.anchor)[0]-=paid;
            NBTTagCompound book=market.owner(j.owner);
            book.setLong("Debt",book.getLong("Debt")+penalty-paid);
            event(j.owner,"Service #"+j.id+" interrupted: -"+penalty+" milli-NCoin");
        }
        j.enabled=false;
        j.served=true;
        j.pending=j.elapsed=j.remainder=j.notice=j.cachedRate=j.promised=0;
        j.machines=Collections.emptySet();
        markDirty();
    }
    void serverStopped(UUID server,boolean planned) {
        for(Job j:jobs) if(j.enabled && j.periodic() && j.machines.contains(server)) interrupt(j,planned);
    }
    // -1 = no service, 0 = ready to stop, positive = remaining seconds, MAX = no notice.
    int maintenance(UUID server,boolean announce) {
        int wait=-1;
        long now=now();
        for(Job j:jobs) if(j.enabled && j.periodic() && j.machines.contains(server)) {
            if(announce && j.notice==0) { j.notice=now+300_000; markDirty(); }
            int seconds=j.notice==0?Integer.MAX_VALUE:(int)Math.max(0,(j.notice-now+999)/1000);
            wait=Math.max(wait,seconds);
        }
        return wait;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n.setLong("Time",now());n.setTag("Market",market.data);n.setInteger("Next",next);n.setLong("Ticks",ticks);n.setIntArray("Rate",rate);NBTTagList js=new NBTTagList();for(Job j:jobs){NBTTagCompound x=new NBTTagCompound();x.setInteger("Id",j.id);x.setInteger("Type",j.type);x.setString("Name",j.name);x.setUniqueId("Anchor",j.anchor);x.setUniqueId("Owner",j.owner);x.setIntArray("Req",new int[]{j.priority,j.cpu,j.gpus,j.ram,j.net,j.group,j.disk});x.setBoolean("Strict",j.strict);x.setBoolean("Auto",j.auto);x.setBoolean("Enabled",j.enabled);x.setLong("Progress",j.progress);x.setLong("Remainder",j.remainder);
        x.setLong("Elapsed",j.elapsed);x.setLong("Pending",j.pending);x.setLong("Notice",j.notice);
        NBTTagList stored=new NBTTagList();for(Map.Entry<UUID,int[]> h:j.retained.entrySet()){
            NBTTagCompound r=new NBTTagCompound();r.setUniqueId("Id",h.getKey());r.setIntArray("GB",h.getValue());stored.appendTag(r);}
        x.setTag("Storage",stored);x.setLong("Accounted",j.lastAccounting);x.setTag("Terms",j.terms.copy());x.setLong("Cached",j.cachedRate);x.setInteger("PaidCount",j.paidCount);x.setInteger("Promised",j.promised);x.setInteger("Units",j.unitLimit);x.setInteger("Base",j.base);
        for(int k=0;k<4;k++) x.setLong("Paid"+k,j.payments[k]);
        NBTTagList hosts=new NBTTagList();
        for(UUID id:j.machines){NBTTagCompound host=new NBTTagCompound();host.setUniqueId("Id",id);hosts.appendTag(host);}
        x.setTag("Hosts",hosts);NBTTagList ms=new NBTTagList();for(Map.Entry<UUID,TaskEngine.Limit> e:j.manual.entrySet()){NBTTagCompound m=new NBTTagCompound();m.setUniqueId("Id",e.getKey());m.setInteger("CPU",e.getValue().cpu);if(e.getValue().allowedCores!=null)m.setByteArray("Cores",e.getValue().allowedCores.toByteArray());m.setInteger("GPU",e.getValue().mask);m.setInteger("IO",e.getValue().io);m.setInteger("RAM",e.getValue().ram);m.setInteger("NET",e.getValue().net);m.setInteger("Disk",e.getValue().disk);m.setInteger("IO",e.getValue().io);ms.appendTag(m);}x.setTag("Manual",ms);js.appendTag(x);}n.setTag("Jobs",js);
        NBTTagList ws=new NBTTagList();for(Map.Entry<UUID,long[]> e:wallets.entrySet()){NBTTagCompound x=new NBTTagCompound();x.setUniqueId("Id",e.getKey());for(int i=0;i<2;i++)x.setLong("C"+i,e.getValue()[i]);ws.appendTag(x);}n.setTag("Wallets",ws);NBTTagList gs=new NBTTagList();for(Map.Entry<UUID,Integer> e:groups.entrySet()){NBTTagCompound x=new NBTTagCompound();x.setUniqueId("Id",e.getKey());x.setInteger("G",e.getValue());gs.appendTag(x);}n.setTag("Groups",gs);NBTTagList logs=new NBTTagList();for(String l:log)logs.appendTag(new NBTTagString(l));n.setTag("Log",logs);return n;}
    @Override public void readFromNBT(NBTTagCompound n){if(n.hasKey("Time"))worldMillis=n.getLong("Time");market.data=n.getCompoundTag("Market");next=Math.max(0,n.getInteger("Next"));ticks=n.getLong("Ticks");int[] rates=n.getIntArray("Rate");if(rates.length==2)rate=rates;jobs.clear();NBTTagList js=n.getTagList("Jobs",10);for(int i=0;i<js.tagCount();i++){NBTTagCompound x=js.getCompoundTagAt(i);int type=x.getInteger("Type");if(type<0||type>=Workload.ALL.length||!x.hasUniqueId("Anchor")||!x.hasUniqueId("Owner"))continue;Job j=new Job(x.getInteger("Id"),type,x.getUniqueId("Anchor"),x.getUniqueId("Owner"));NBTTagList storage=x.getTagList("Storage",10);for(int k=0;k<storage.tagCount();k++){NBTTagCompound h=storage.getCompoundTagAt(k);int[] gb=h.getIntArray("GB");if(gb.length==ServerSpec.PSU)j.retained.put(h.getUniqueId("Id"),gb);}
        j.lastAccounting=x.getLong("Accounted");j.terms.merge(x.getCompoundTag("Terms"));j.cachedRate=x.getLong("Cached");j.paidCount=x.getInteger("PaidCount");j.promised=x.getInteger("Promised");int[] q=x.getIntArray("Req");if(q.length==7){j.priority=q[0];j.cpu=q[1];j.gpus=q[2];j.ram=q[3];j.net=q[4];j.group=q[5];j.disk=q[6];}if(x.hasKey("Name"))j.name=x.getString("Name");j.strict=x.getBoolean("Strict");j.auto=x.getBoolean("Auto");j.enabled=x.getBoolean("Enabled");j.progress=x.getLong("Progress");j.remainder=Math.max(0,x.getLong("Remainder"))%((Workload.contract(type)?1000L:j.periodic()?600000L:30000L)*TaskEngine.SCALE);
        j.elapsed=Math.max(0,x.getLong("Elapsed"))%(j.periodic()?600_000:30_000);
        j.pending=Math.max(0,x.getLong("Pending"));j.notice=x.getLong("Notice");
        if(x.hasKey("Units"))j.unitLimit=Math.max(1,x.getInteger("Units"));
        if(x.hasKey("Base"))j.base=Math.max(1,x.getInteger("Base"));
        for(int k=0;k<4;k++)j.payments[k]=Math.max(0,x.getLong("Paid"+k));
        NBTTagList hosts=x.getTagList("Hosts",10);j.machines=new HashSet<>();
        for(int k=0;k<hosts.tagCount();k++)j.machines.add(hosts.getCompoundTagAt(k).getUniqueId("Id"));
        NBTTagList ms=x.getTagList("Manual",10);for(int k=0;k<ms.tagCount();k++){NBTTagCompound m=ms.getCompoundTagAt(k);if(m.hasUniqueId("Id")){TaskEngine.Limit l=new TaskEngine.Limit(m.getInteger("CPU"),m.getInteger("GPU"));if(m.hasKey("Cores"))l.allowedCores=BitSet.valueOf(m.getByteArray("Cores"));l.io=m.hasKey("IO")?m.getInteger("IO"):Integer.MAX_VALUE;l.ram=m.hasKey("RAM")?m.getInteger("RAM"):Integer.MAX_VALUE;l.net=m.hasKey("NET")?m.getInteger("NET"):Integer.MAX_VALUE;l.disk=m.hasKey("Disk")?m.getInteger("Disk"):Integer.MAX_VALUE;j.manual.put(m.getUniqueId("Id"),l);}}jobs.add(j);next=Math.max(next,j.id+1);}wallets.clear();NBTTagList ws=n.getTagList("Wallets",10);for(int i=0;i<ws.tagCount();i++){NBTTagCompound x=ws.getCompoundTagAt(i);if(x.hasUniqueId("Id")){long[] v=wallet(x.getUniqueId("Id"));for(int k=0;k<2;k++)v[k]=k==0?x.getLong("C"+k):Math.max(0,x.getLong("C"+k));}}groups.clear();NBTTagList gs=n.getTagList("Groups",10);for(int i=0;i<gs.tagCount();i++){NBTTagCompound x=gs.getCompoundTagAt(i);if(x.hasUniqueId("Id"))groups.put(x.getUniqueId("Id"),x.getInteger("G"));}log.clear();NBTTagList logs=n.getTagList("Log",8);for(int i=0;i<logs.tagCount();i++)log.add(logs.getStringTagAt(i));}
}
