package odc;
import static odc.EventJournal.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
/** Server-authoritative persistent jobs and wallets. Scope: task anchor controller + owner. */
public final class TaskData extends WorldSavedData {
    static final String KEY="odc_compute_v053"; static final String[] COINS={"NCoin","KriptoCoin"};static final String[] TYPES={"KriptoCoin / CoreHash","KriptoCoin / ParallelHash","KriptoCoin / MemoryHash","Bank service","AI training","AI inference"};
    static final class Job extends TaskEngine.Task { UUID anchor,owner; int work,usedCPU,usedGPU,usedRAM,usedDisk,usedNet;long remainder,earned,elapsed,notice,pending;
        final long[] payments=new long[4];
        Set<UUID> machines=Collections.emptySet();
        boolean served;
        final NBTTagCompound terms=new NBTTagCompound();
        long cachedRate,lastAccounting;
        int promised,paidCount;
        boolean periodic(){return Workload.service(type);}
        String name,poolLabel=PoolResources.DEFAULT;boolean full; Job(int id,int type,UUID a,UUID o){super(id,type);anchor=a;owner=o;name="task.odc."+type;terms.setUniqueId("Key",UUID.randomUUID());} }
    final Map<UUID,String> pools=new HashMap<>();
    String poolName(UUID server){return pools.getOrDefault(server,PoolResources.DEFAULT);}
    UUID poolId(UUID owner,UUID server){return PoolResources.id(owner,poolName(server));}
    boolean setPool(Tiles.Rack rack,int bay,String raw){
        String name=PoolResources.name(raw);if(name==null||!rack.isServer(bay))return false;
        UUID server=ClusterScheduler.serverId(rack.slots.get(bay));if(name.equals(poolName(server)))return true;
        // Do not move hardware with running work or retained data into another pool.
        for(Job j:jobs)if(!j.terms.hasKey("End")&&((j.retained.containsKey(server)&&Arrays.stream(j.retained.get(server)).anyMatch(v->v>0))||j.enabled&&(j.manual.containsKey(server)||j.machines.contains(server))||j.bindings.stream().anyMatch(b->b.server.equals(server)&&(j.enabled||b.storage()))))return false;
        EventSystem.rack(rack,bay,Level.INFO,Category.POOL,"pool_changed","Pool: "+poolName(server)+" -> "+name);
        if(name.equals(PoolResources.DEFAULT))pools.remove(server);else pools.put(server,name);
        OfflineState.invalidateAt(rack.getWorld(),rack.getPos());domains.clear();cache.clear();markDirty();rack.changed();return true;
    }
    final List<Job> jobs=new ArrayList<>();final Map<UUID,long[]> wallets=new HashMap<>();final EventJournal journal=new EventJournal(ODC.journalLimit); Map<UUID,EventSystem.Topology> eventTopology=new HashMap<>(); final Map<UUID,Boolean> routeFaults=new HashMap<>(); final Map<UUID,Boolean> resourceFaults=new HashMap<>(),networkState=new HashMap<>();int next=0;long ticks;int[] rate={1000,1500};
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
        long credited=credit(j.anchor,currency,amount-repayment);if(credited>0)jobEvent(j,Level.SUCCESS,Category.FINANCE,"payment","Payment credited",currency,credited);if(repayment>0)jobEvent(j,Level.INFO,Category.FINANCE,"payment","Debt repaid from earnings",currency,repayment);return repayment+credited;
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
    List<Job> reservingJobs(){List<Job> result=new ArrayList<>();for(Job j:jobs)if(!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished"))result.add(j);return result;}
    TaskEngine.Plan plan(UUID anchor,List<TaskEngine.Node> nodes,List<Job> jobs,int[] caps,int wan,Set<Integer> excluded) {
        return plan(anchor,nodes,jobs,caps,wan,excluded,null);
    }
    TaskEngine.Plan plan(UUID anchor,List<TaskEngine.Node> nodes,List<Job> jobs,int[] caps,int wan,Set<Integer> excluded,Set<Integer> networkBlocked) {
        if(anchor!=null)usedCaches.add(anchor);
        Set<Integer> local=new HashSet<>();for(Job j:jobs)if(!j.terms.hasKey("End"))local.add(j.id);
        List<Job> reservations=reservingJobs();return anchor==null?FixedResources.plan(nodes,reservations,local,caps,wan,excluded,networkBlocked):cache.computeIfAbsent(anchor,id->new AllocationCache()).get(nodes,reservations,local,caps,wan,excluded,networkBlocked);
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
    void record(UUID owner,UUID scope,UUID source,Level level,Category category,Target target,String code,String message,int currency,long amount){journal.add(ticks,owner,scope,source,level,category,target,code,message,currency,amount);markDirty();}
    void jobEvent(Job j,Level l,Category c,String code,String message){jobEvent(j,l,c,code,message,-1,0);}
    void jobEvent(Job j,Level l,Category c,String code,String message,int coin,long amount){record(j.owner,j.anchor,j.terms.getUniqueId("Key"),l,c,Target.TASK,code,"#"+j.id+" "+message,coin,amount);}
    void resourceState(Job j,boolean fault){UUID id=j.terms.getUniqueId("Key");Boolean before=resourceFaults.put(id,fault);if(fault&&!Boolean.TRUE.equals(before))jobEvent(j,Level.ERROR,Category.RESOURCE,"resources_unavailable","Reserved resources unavailable");else if(!fault&&Boolean.TRUE.equals(before))jobEvent(j,Level.SUCCESS,Category.RESOURCE,"resources_restored","Reserved resources available");}
    private final Map<UUID,Map<UUID,PhysicalTopology.Type>> discoveredNetwork=new HashMap<>();
    void observeNetwork(Tiles.Computer controller,UUID anchor){if(anchor==null||controller.owner==null)return;PhysicalTopology.View view=PhysicalNetwork.view(controller.getWorld(),controller.getPos());Map<UUID,PhysicalTopology.Type> current=new HashMap<>();for(PhysicalTopology.State state:view.devices.values()){PhysicalTopology.Device device=state.device;if(device.type==PhysicalTopology.Type.SERVER||device.type==PhysicalTopology.Type.SWITCH)continue;current.put(device.id,device.type);}Map<UUID,PhysicalTopology.Type> old=discoveredNetwork.getOrDefault(anchor,Collections.emptyMap());for(Map.Entry<UUID,PhysicalTopology.Type> e:current.entrySet())if(!old.containsKey(e.getKey()))record(controller.owner,anchor,e.getKey(),Level.INFO,Category.NETWORK,Target.NONE,e.getValue()==PhysicalTopology.Type.TERMINAL?"network_terminal_connected":"antenna_discovered",e.getValue()==PhysicalTopology.Type.TERMINAL?"Network terminal discovered":"Optical antenna discovered",-1,0);if(view.complete)for(Map.Entry<UUID,PhysicalTopology.Type> e:old.entrySet())if(!current.containsKey(e.getKey()))record(controller.owner,anchor,e.getKey(),Level.WARNING,Category.NETWORK,Target.NONE,e.getValue()==PhysicalTopology.Type.TERMINAL?"network_terminal_disconnected":"antenna_disconnected",e.getValue()==PhysicalTopology.Type.TERMINAL?"Network terminal disconnected":"Optical antenna disconnected",-1,0);if(!view.complete)for(Map.Entry<UUID,PhysicalTopology.Type> e:old.entrySet())current.putIfAbsent(e.getKey(),e.getValue());discoveredNetwork.put(anchor,current);}
    void routeState(Job j,boolean ok){UUID id=j.terms.getUniqueId("Key");Boolean old=routeFaults.put(id,!ok);if(!ok&&!Boolean.TRUE.equals(old))jobEvent(j,Level.WARNING,Category.NETWORK,"bandwidth_lost","Reserved network bandwidth unavailable");else if(ok&&Boolean.TRUE.equals(old))jobEvent(j,Level.SUCCESS,Category.NETWORK,"bandwidth_restored","Reserved network bandwidth restored");}
    void networkState(Tiles.Rack rack,int bay,boolean online){UUID id=ClusterScheduler.serverId(rack.slots.get(bay));Boolean before=networkState.put(id,online);if(Boolean.TRUE.equals(before)&&!online)EventSystem.rack(rack,bay,Level.WARNING,Category.NETWORK,"network_lost","Server network connection lost");else if(Boolean.FALSE.equals(before)&&online)EventSystem.rack(rack,bay,Level.SUCCESS,Category.NETWORK,"network_restored","Server network connection restored");}
    List<Entry> recentEvents(Tiles.Owned tile,int count){return EventSystem.recent(tile,this,count);}
    int errorCount(Tiles.Owned tile){return EventSystem.errors(tile,this);}
    List<Entry> financialEvents(Tiles.Owned tile){return EventSystem.financial(tile,this);}
    // pending and remainder retain fractional milli-coins (or contract points) across pauses.
    void account(Job j,TaskEngine.Result allocation) {
        if(j.served || !j.enabled || allocation.rate<=0) return;
        if(j.periodic() && (allocation.units<j.promised || !allocation.machines.containsAll(j.machines))) {
            interrupt(j,false);
            return;
        }
        if(!j.terms.hasKey("Start"))j.terms.setLong("Start",now());
        if(Workload.contract(j.type)&&j.terms.getBoolean("Fixed"))j.terms.setBoolean("Locked",true);
        j.served=true;
        settle(j,now());
        j.cachedRate=allocation.rate;j.promised=allocation.units;
        j.machines=allocation.machines;
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
        for(Job j:jobs)if(j.enabled&&!j.served&&!j.bindings.isEmpty())resourceState(j,true);
        for(Job j:jobs) if(!j.served && !j.machines.isEmpty()) {
            if(j.periodic()) interrupt(j,false);
            else {j.machines=Collections.emptySet();j.cachedRate=0;}
        }
        Set<UUID> liveJobs=new HashSet<>();for(Job j:jobs)if(j.enabled&&!j.terms.hasKey("End")){liveJobs.add(j.terms.getUniqueId("Key"));}resourceFaults.keySet().retainAll(liveJobs);routeFaults.keySet().retainAll(liveJobs);
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
            jobEvent(j,Level.ERROR,Category.TASK,"service_interrupted","Service interrupted");if(paid>0)jobEvent(j,Level.WARNING,Category.FINANCE,"penalty","Penalty debited",0,-paid);if(penalty>paid)jobEvent(j,Level.WARNING,Category.FINANCE,"debt","Penalty added to debt",0,-(penalty-paid));
        }
        jobEvent(j,Level.INFO,Category.RESOURCE,"compute_released","Compute reservations released; storage retained");if(planned&&j.notice>0)jobEvent(j,Level.INFO,Category.TASK,"maintenance_finished","Maintenance stop completed");
        j.enabled=false;
        j.served=true;
        j.pending=j.elapsed=j.remainder=j.notice=j.cachedRate=j.promised=0;
        j.machines=Collections.emptySet();
        markDirty();
    }
    void serverStopped(UUID server,boolean planned) {
        for(Job j:jobs) if(j.enabled && j.periodic() && (j.machines.contains(server)||FixedResources.hosts(j.bindings).contains(server))) interrupt(j,planned);
    }
    // -1 = no service, 0 = ready to stop, positive = remaining seconds, MAX = no notice.
    int maintenance(UUID server,boolean announce) {
        int wait=-1;
        long now=now();
        for(Job j:jobs) if(j.enabled && j.periodic() && (j.machines.contains(server)||FixedResources.hosts(j.bindings).contains(server))) {
            if(announce && j.notice==0) { j.notice=now+300_000; jobEvent(j,Level.INFO,Category.TASK,"maintenance_started","Maintenance notice announced");markDirty(); }
            int seconds=j.notice==0?Integer.MAX_VALUE:(int)Math.max(0,(j.notice-now+999)/1000);
            wait=Math.max(wait,seconds);
        }
        return wait;
    }
    static NBTTagList writeBindings(List<PoolResources.Binding> bindings){NBTTagList list=new NBTTagList();for(PoolResources.Binding b:bindings){NBTTagCompound x=new NBTTagCompound();x.setUniqueId("Server",b.server);x.setString("Kind",b.kind.name());x.setInteger("Slot",b.slot);x.setInteger("Unit",b.unit);x.setInteger("Model",b.model);x.setInteger("Amount",b.amount);x.setInteger("IO",b.io);list.appendTag(x);}return list;}
    static void readBindings(NBTTagList list,List<PoolResources.Binding> bindings){bindings.clear();for(int i=0;i<list.tagCount();i++){NBTTagCompound x=list.getCompoundTagAt(i);if(!x.hasUniqueId("Server"))continue;try{bindings.add(new PoolResources.Binding(x.getUniqueId("Server"),PoolResources.Kind.valueOf(x.getString("Kind")),x.getInteger("Slot"),x.getInteger("Unit"),x.getInteger("Model"),x.getInteger("Amount"),x.getInteger("IO")));}catch(IllegalArgumentException ignored){}}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n.setLong("Time",now());n.setTag("Market",market.data);n.setInteger("Next",next);n.setLong("Ticks",ticks);n.setIntArray("Rate",rate);NBTTagList js=new NBTTagList();for(Job j:jobs){NBTTagCompound x=new NBTTagCompound();x.setInteger("Id",j.id);x.setInteger("Type",j.type);x.setString("Name",j.name);x.setUniqueId("Anchor",j.anchor);x.setUniqueId("Owner",j.owner);x.setIntArray("Req",new int[]{0,j.cpu,j.gpus,j.ram,j.net,0,j.disk});if(j.pool!=null)x.setUniqueId("Pool",j.pool);x.setString("PoolLabel",j.poolLabel);x.setTag("Bindings",writeBindings(j.bindings));x.setBoolean("Strict",j.strict);x.setBoolean("Auto",j.auto);x.setBoolean("Enabled",j.enabled);x.setLong("Progress",j.progress);x.setLong("Remainder",j.remainder);
        x.setLong("Elapsed",j.elapsed);x.setLong("Pending",j.pending);x.setLong("Notice",j.notice);
        NBTTagList stored=new NBTTagList();for(Map.Entry<UUID,int[]> h:j.retained.entrySet()){
            NBTTagCompound r=new NBTTagCompound();r.setUniqueId("Id",h.getKey());r.setIntArray("GB",h.getValue());stored.appendTag(r);}
        x.setTag("Storage",stored);x.setLong("Accounted",j.lastAccounting);x.setTag("Terms",j.terms.copy());x.setLong("Cached",j.cachedRate);x.setInteger("PaidCount",j.paidCount);x.setInteger("Promised",j.promised);x.setInteger("Units",j.unitLimit);x.setInteger("Base",j.base);
        for(int k=0;k<4;k++) x.setLong("Paid"+k,j.payments[k]);
        NBTTagList hosts=new NBTTagList();
        for(UUID id:j.machines){NBTTagCompound host=new NBTTagCompound();host.setUniqueId("Id",id);hosts.appendTag(host);}
        x.setTag("Hosts",hosts);NBTTagList ms=new NBTTagList();for(Map.Entry<UUID,TaskEngine.Limit> e:j.manual.entrySet()){NBTTagCompound m=new NBTTagCompound();m.setUniqueId("Id",e.getKey());m.setInteger("CPU",e.getValue().cpu);if(e.getValue().allowedCores!=null)m.setByteArray("Cores",e.getValue().allowedCores.toByteArray());m.setInteger("GPU",e.getValue().mask);m.setInteger("IO",e.getValue().io);m.setInteger("RAM",e.getValue().ram);m.setInteger("NET",e.getValue().net);m.setInteger("Disk",e.getValue().disk);m.setInteger("IO",e.getValue().io);ms.appendTag(m);}x.setTag("Manual",ms);js.appendTag(x);}n.setTag("Jobs",js);
        NBTTagList ws=new NBTTagList();for(Map.Entry<UUID,long[]> e:wallets.entrySet()){NBTTagCompound x=new NBTTagCompound();x.setUniqueId("Id",e.getKey());for(int i=0;i<2;i++)x.setLong("C"+i,e.getValue()[i]);ws.appendTag(x);}n.setTag("Wallets",ws);NBTTagList ps=new NBTTagList();for(Map.Entry<UUID,String> e:pools.entrySet()){NBTTagCompound x=new NBTTagCompound();x.setUniqueId("Id",e.getKey());x.setString("Name",e.getValue());ps.appendTag(x);}n.setTag("Pools",ps);n.setTag("Events",EventSystem.write(journal));return n;}
    @Override public void readFromNBT(NBTTagCompound n){if(n.hasKey("Time"))worldMillis=n.getLong("Time");market.data=n.getCompoundTag("Market");next=Math.max(0,n.getInteger("Next"));ticks=n.getLong("Ticks");int[] rates=n.getIntArray("Rate");if(rates.length==2)rate=rates;jobs.clear();NBTTagList js=n.getTagList("Jobs",10);for(int i=0;i<js.tagCount();i++){NBTTagCompound x=js.getCompoundTagAt(i);int type=x.getInteger("Type");if(type<0||type>=Workload.ALL.length||!x.hasUniqueId("Anchor")||!x.hasUniqueId("Owner"))continue;Job j=new Job(x.getInteger("Id"),type,x.getUniqueId("Anchor"),x.getUniqueId("Owner"));NBTTagList storage=x.getTagList("Storage",10);for(int k=0;k<storage.tagCount();k++){NBTTagCompound h=storage.getCompoundTagAt(k);int[] gb=h.getIntArray("GB");if(gb.length==ServerSpec.PSU)j.retained.put(h.getUniqueId("Id"),gb);}
        j.pool=x.hasUniqueId("Pool")?x.getUniqueId("Pool"):PoolResources.id(j.owner,PoolResources.DEFAULT);if(x.hasKey("PoolLabel"))j.poolLabel=x.getString("PoolLabel");readBindings(x.getTagList("Bindings",10),j.bindings);j.lastAccounting=0;j.terms.merge(x.getCompoundTag("Terms"));j.cachedRate=0;j.paidCount=x.getInteger("PaidCount");j.promised=x.getInteger("Promised");int[] q=x.getIntArray("Req");if(q.length==7){j.cpu=q[1];j.gpus=q[2];j.ram=q[3];j.net=q[4];j.disk=q[6];}if(x.hasKey("Name"))j.name=x.getString("Name");j.strict=x.getBoolean("Strict");j.auto=x.getBoolean("Auto");j.enabled=x.getBoolean("Enabled")&&!j.bindings.isEmpty();j.progress=x.getLong("Progress");j.remainder=Math.max(0,x.getLong("Remainder"))%((Workload.contract(type)?1000L:j.periodic()?600000L:30000L)*TaskEngine.SCALE);
        j.elapsed=Math.max(0,x.getLong("Elapsed"))%(j.periodic()?600_000:30_000);
        j.pending=Math.max(0,x.getLong("Pending"));j.notice=x.getLong("Notice");
        if(x.hasKey("Units"))j.unitLimit=Math.max(1,x.getInteger("Units"));
        if(x.hasKey("Base"))j.base=Math.max(1,x.getInteger("Base"));
        for(int k=0;k<4;k++)j.payments[k]=Math.max(0,x.getLong("Paid"+k));
        NBTTagList hosts=x.getTagList("Hosts",10);j.machines=new HashSet<>();
        for(int k=0;k<hosts.tagCount();k++)j.machines.add(hosts.getCompoundTagAt(k).getUniqueId("Id"));
        NBTTagList ms=x.getTagList("Manual",10);for(int k=0;k<ms.tagCount();k++){NBTTagCompound m=ms.getCompoundTagAt(k);if(m.hasUniqueId("Id")){TaskEngine.Limit l=new TaskEngine.Limit(m.getInteger("CPU"),m.getInteger("GPU"));if(m.hasKey("Cores"))l.allowedCores=BitSet.valueOf(m.getByteArray("Cores"));l.io=m.hasKey("IO")?m.getInteger("IO"):Integer.MAX_VALUE;l.ram=m.hasKey("RAM")?m.getInteger("RAM"):Integer.MAX_VALUE;l.net=m.hasKey("NET")?m.getInteger("NET"):Integer.MAX_VALUE;l.disk=m.hasKey("Disk")?m.getInteger("Disk"):Integer.MAX_VALUE;j.manual.put(m.getUniqueId("Id"),l);}}jobs.add(j);next=Math.max(next,j.id+1);}wallets.clear();NBTTagList ws=n.getTagList("Wallets",10);for(int i=0;i<ws.tagCount();i++){NBTTagCompound x=ws.getCompoundTagAt(i);if(x.hasUniqueId("Id")){long[] v=wallet(x.getUniqueId("Id"));for(int k=0;k<2;k++)v[k]=k==0?x.getLong("C"+k):Math.max(0,x.getLong("C"+k));}}pools.clear();NBTTagList ps=n.getTagList("Pools",10);for(int i=0;i<ps.tagCount();i++){NBTTagCompound x=ps.getCompoundTagAt(i);String name=PoolResources.name(x.getString("Name"));if(x.hasUniqueId("Id")&&name!=null)pools.put(x.getUniqueId("Id"),name);}EventSystem.load(journal,n.getTagList("Events",10));}
}
