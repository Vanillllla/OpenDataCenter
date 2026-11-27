package odc;
import java.util.*;
import net.minecraft.nbt.*;

/** Window-local draft. Only the authoritative scheduler and wallet can commit work. */
final class TaskConsole {
    static final UUID NONE=new UUID(0,0);
    int revision,section,type=-1,physicalOffset;
    UUID selected=NONE,draftPool=NONE,expanded=NONE;
    boolean assigning;
    final List<PoolResources.Binding> draft=new ArrayList<>();
    static final int PHYSICAL_WINDOW=128;
    String commitError="";
    static final class Domain {
        final List<TaskEngine.Node> nodes;final int[] caps;final int wan;final Set<Integer> excluded;final long tick;TaskEngine.Plan plan;
        Domain(List<TaskEngine.Node> n,int[] c,int w,Set<Integer> e,long t){nodes=n;caps=c;wan=w;excluded=e;tick=t;}
    }
    static TaskData.Job find(List<TaskData.Job> jobs,UUID id){
        for(TaskData.Job j:jobs)if(j.terms.getUniqueId("Key").equals(id))return j;
        return null;
    }
    static UUID rowId(String key){return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    Domain live(PanelMenu m,TaskData d,UUID anchor){
        Domain domain=d.domains.get(anchor);
        return domain!=null && m.player.world.getTotalWorldTime()-domain.tick<=20?domain:null;
    }
    void reset(int next){section=next;assigning=false;type=-1;selected=NONE;draftPool=NONE;expanded=NONE;physicalOffset=0;draft.clear();commitError="";}
    void openDraft(UUID pool,TaskData.Job j){draftPool=pool;assigning=true;expanded=NONE;physicalOffset=0;draft.clear();if(j!=null)draft.addAll(j.bindings);commitError="";}
    FixedResources.Ledger ledger(TaskData d,Domain domain,TaskData.Job current){return new FixedResources.Ledger(domain.nodes,domain.caps,domain.wan,domain.excluded,d.reservingJobs(),current);}
    TaskEngine.Task candidate(TaskData d,TaskData.Job current){TaskEngine.Task t=new TaskEngine.Task(current==null?d.next:current.id,type);t.pool=draftPool;t.enabled=true;t.auto=false;if(current!=null)t.base=current.base;t.bindings.addAll(locked(current)?current.bindings:draft);return t;}
    boolean locked(TaskData.Job j){return j!=null&&j.terms.hasKey("Start")&&j.terms.getBoolean("Fixed");}
    Map<String,List<PoolResources.Device>> families(FixedResources.Ledger l){Map<String,List<PoolResources.Device>> result=new TreeMap<>();for(PoolResources.Device device:l.devices.values())if(draftPool.equals(l.nodes.get(l.index.get(device.server)).pool))result.computeIfAbsent(device.family(),k->new ArrayList<>()).add(device);return result;}
    String restrictions(TaskData.Job j){
        if(j==null)return "";
        Map<String,Integer> old=FixedResources.amounts(j.bindings),next=FixedResources.amounts(draft);
        if(j.terms.getBoolean("Single")&&FixedResources.hosts(draft).size()!=1)return "single";
        if(j.terms.hasKey("Start")){
            if(j.terms.getBoolean("Fixed")&&!old.equals(next))return "fixed";
            if(j.terms.getBoolean("Pinned")&&!FixedResources.hosts(draft).equals(FixedResources.hosts(j.bindings)))return "pinned";
            for(PoolResources.Binding b:j.bindings)if(b.storage()&&next.getOrDefault(FixedResources.key(b),0)<old.get(FixedResources.key(b)))return "retained";
        }
        return "";
    }
    boolean canEdit(TaskData.Job j,long now){return j==null||!j.enabled||Workload.contract(j.type)&&!locked(j)||j.periodic()&&j.notice>0&&now>=j.notice;}
    boolean physicalCurrent(PanelMenu m,TaskData d,Domain domain){
        Network.Domain topology=Network.scan(m.tile.getWorld(),m.tile.getPos());if(topology==null)return false;int wan=0;boolean online=false;
        for(Tiles.Owned device:topology.devices)if(Objects.equals(device.owner,m.tile.owner)){if(device instanceof Tiles.Station){Tiles.Station st=(Tiles.Station)device;if(st.online&&st.energy.fed())wan=ClusterScheduler.add(wan,st.bandwidth());}else if(device instanceof Tiles.Computer){Tiles.Computer c=(Tiles.Computer)device;if(c.online&&c.energy.fed())online=true;}}
        if(!online||domain.wan!=wan)return false;
        Map<UUID,Menus.Target> live=new HashMap<>();for(Menus.Target target:DataOSView.servers(m.tile))live.put(ClusterScheduler.serverId(target.item),target);
        for(PoolResources.Binding b:draft){Menus.Target t=live.get(b.server);Integer i=null;for(int k=0;k<domain.nodes.size();k++)if(domain.nodes.get(k).id.equals(b.server)){i=k;break;}
            if(t==null||i==null||!t.rack.allows(m.player)||!t.rack.energy.fed()||(t.rack.enabledMask&(1<<t.bay))==0||!Arrays.equals(Hardware.spec(t.item),domain.nodes.get(i).parts)||!draftPool.equals(d.poolId(m.tile.owner,b.server)))return false;
            if(b.kind==PoolResources.Kind.NETWORK&&(!t.rack.switchOnline||t.rack.portOf(t.bay)<0||Math.min(Hardware.nic(t.item),Hardware.portMbps(t.rack.switchTier()))!=domain.nodes.get(i).net||Hardware.switchMbps(t.rack.switchTier())!=domain.caps[domain.nodes.get(i).rack]))return false;
        }return true;
    }
    NBTTagCompound marketRow(TaskData d,UUID owner,int sec,UUID key){
        if(owner==null||sec!=2&&sec!=5||NONE.equals(key))return null;
        NBTTagList rows=ContractMarket.list(d.market.owner(owner),sec==2?"Offers":"History");
        for(int i=0;i<rows.tagCount();i++){NBTTagCompound r=rows.getCompoundTagAt(i);if(key.equals(r.getUniqueId("Key")))return r;}return null;
    }
    boolean visibleJob(TaskData.Job j){return j!=null&&(section==0||section==3&&Workload.contract(j.type)||section==4&&j.periodic());}
    boolean catalog(int t){return selected.equals(NONE)&&((section==1&&t>=0&&t<9)||(section==4&&t>=21&&t<Workload.ALL.length));}
    boolean selectionExists(TaskData d,UUID owner,List<TaskData.Job> jobs,long now){
        if(type<0||type>=Workload.ALL.length)return false;
        if(catalog(type))return true;
        TaskData.Job j=find(jobs,selected);
        if(visibleJob(j))return j.type==type&&!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished")&&(j.terms.getLong("Deadline")==0||now<j.terms.getLong("Deadline"));
        NBTTagCompound r=marketRow(d,owner,section,selected);
        return r!=null&&r.getInteger("Type")==type&&(section!=2||now<r.getLong("Expires"));
    }
    int actionMask(PanelMenu m,TaskData d,List<TaskData.Job> jobs,long now){
        if(assigning)return 0;
        TaskData.Job j=find(jobs,selected);boolean live=visibleJob(j);
        NBTTagCompound offer=marketRow(d,m.tile.owner,2,selected);
        boolean affordable=jobs.size()<256&&offer!=null&&offer.getLong("Fee")<=d.wallet(ClusterScheduler.key((Tiles.Computer)m.tile))[0];
        int mask=TaskActions.mask(((Tiles.Computer)m.tile).online&&m.tile.allows(m.player),section,selectionExists(d,m.tile.owner,jobs,now),live,live&&j.enabled,live&&j.periodic(),live&&Workload.contract(j.type)&&ContractMarket.CONTINUOUS[j.terms.getInteger("Rule")],live?j.notice:0,now,affordable);
        return live&&!canEdit(j,now)?mask&~(1<<3):mask;
    }
    String validate(TaskData d,Domain domain,List<TaskData.Job> jobs,TaskData.Job j,long now){
        if(!assigning||type<0||domain==null||!canEdit(j,now))return "offline";
        if(j==null&&(!selected.equals(NONE)||Workload.contract(type)||jobs.size()>=256))return "selection";
        String restricted=restrictions(j);if(!restricted.isEmpty())return restricted;
        FixedResources.Evaluation e=FixedResources.evaluate(candidate(d,j),ledger(d,domain,j),!locked(j));
        return e.error;
    }
    void command(PanelMenu m,TaskData d,TaskCommand cmd){
        UUID wallet=ClusterScheduler.key((Tiles.Computer)m.tile),owner=m.tile.owner;
        if(owner==null||!m.tile.allows(m.player))return;
        List<TaskData.Job> jobs=DataOSView.jobs(m.tile,d);TaskData.Job j=find(jobs,cmd.key),current=find(jobs,selected);
        Domain domain=live(m,d,wallet);long now=d.now();commitError="";
        if(cmd.op==11){
            if(!DataOSView.hasPool(m.tile,d,cmd.key)||!(cmd.value==2||cmd.value==3)||!TaskActions.has(actionMask(m,d,jobs,now),cmd.value))return;
            if(cmd.value==2){if(jobs.size()<256&&d.market.accept(d,owner,wallet,selected,now,cmd.key,DataOSView.poolName(m.tile,d,cmd.key))){TaskData.Job accepted=find(jobs,selected);if(accepted==null)accepted=find(DataOSView.jobs(m.tile,d),selected);if(accepted!=null){section=0;openDraft(cmd.key,accepted);}else reset(0);}return;}
            if(Workload.contract(type)||!selected.equals(NONE))return;openDraft(cmd.key,null);return;
        }
        if(cmd.op>=12&&cmd.op<=15){
            if(!assigning||domain==null||!selectionExists(d,owner,jobs,now)||!canEdit(current,now))return;
            FixedResources.Ledger l=ledger(d,domain,current);Map<String,List<PoolResources.Device>> fs=families(l);
            if(cmd.op==13){expanded=NONE;physicalOffset=0;for(String family:fs.keySet())if(rowId(family).equals(cmd.key)){expanded=cmd.key;break;}return;}
            if(cmd.op==14){if(expanded.equals(cmd.key))physicalOffset=Math.max(0,cmd.value);return;}
            if(locked(current))return;
            if(cmd.op==15){boolean retain=current!=null&&current.terms.hasKey("Start");draft.removeIf(b->!retain||!b.storage());return;}
            if(cmd.value<0)return;
            for(Map.Entry<String,List<PoolResources.Device>> entry:fs.entrySet()){
                if(rowId(entry.getKey()).equals(cmd.key)){if(!l.adjust(draftPool,entry.getValue(),draft,cmd.value))commitError="unavailable";return;}
                if(expanded.equals(rowId(entry.getKey())))for(PoolResources.Device device:entry.getValue())if(rowId(device.key()).equals(cmd.key)){if(!l.adjust(draftPool,Collections.singletonList(device),draft,cmd.value))commitError="unavailable";return;}
            }return;
        }
        if(cmd.op==6){
            if(!selected.equals(cmd.key)||!selectionExists(d,owner,jobs,now))return;
            if(current!=null){d.settle(current,now);if(current.terms.hasKey("End"))return;}
            String error=validate(d,domain,jobs,current,now);if(!error.isEmpty()){commitError=error;return;}
            if(!physicalCurrent(m,d,domain)){commitError="unavailable";return;}
            FixedResources.Evaluation accepted=FixedResources.evaluate(candidate(d,current),ledger(d,domain,current),!locked(current));
            if(current==null){current=new TaskData.Job(d.next++,type,wallet,owner);current.pool=draftPool;current.poolLabel=DataOSView.poolName(m.tile,d,draftPool);d.jobs.add(current);}
            current.bindings.clear();current.bindings.addAll(accepted.committed);current.auto=false;current.strict=false;current.manual.clear();current.retained.clear();current.unitLimit=Integer.MAX_VALUE;
            current.enabled=true;current.notice=0;current.cachedRate=0;current.lastAccounting=now;current.promised=0;current.machines=Collections.emptySet();current.terms.setBoolean("Fault",false);
            selected=current.terms.getUniqueId("Key");assigning=false;section=0;draft.clear();d.markDirty();return;
        }
        if(cmd.op<0||cmd.op>10||cmd.op==2||cmd.op==4||cmd.op==5)return;
        if(cmd.op==3||cmd.op>=7){
            if(!TaskActions.authorized(selected,cmd.key,actionMask(m,d,jobs,now),cmd.op))return;
            if(j!=null){d.settle(j,now);if(!TaskActions.has(actionMask(m,d,jobs,now),cmd.op))return;}
        }
        switch(cmd.op){
        case 0: if(cmd.value>=0&&cmd.value<6)reset(cmd.value);break;
        case 1: reset(section);if(visibleJob(j)&&j.type==cmd.value){selected=cmd.key;type=j.type;}
            else if(cmd.key.equals(NONE)&&catalog(cmd.value))type=cmd.value;
            else {NBTTagCompound r=marketRow(d,owner,section,cmd.key);if(r!=null&&r.getInteger("Type")==cmd.value){selected=cmd.key;type=cmd.value;}}
            if(!selectionExists(d,owner,jobs,now))reset(section);break;
        case 3: if(j!=null&&canEdit(j,now)&&j.pool!=null)openDraft(j.pool,j);break;
        case 8: if(j!=null&&j.enabled&&j.periodic()&&j.notice==0)j.notice=now+300000;break;
        case 9: if(j!=null&&now<j.notice)j.notice=0;break;
        case 10: if(j!=null){d.market.end(d,j,"cancelled",0);reset(section);}break;
        case 7: if(j!=null&&j.enabled&&(!j.periodic()||j.notice>0&&now>=j.notice)&&(!Workload.contract(j.type)||!ContractMarket.CONTINUOUS[j.terms.getInteger("Rule")])){j.enabled=false;j.cachedRate=0;j.machines=Collections.emptySet();j.notice=0;}break;
        }
        d.markDirty();
    }
    static NBTTagCompound deviceRow(PoolResources.Device d,UUID id,int amount,int max){NBTTagCompound r=new NBTTagCompound();r.setUniqueId("Key",id);r.setString("Kind",d.kind.name());r.setInteger("Model",d.model);r.setInteger("Speed",d.speed);r.setInteger("VRAM",d.vram);r.setInteger("Amount",amount);r.setInteger("Max",max);return r;}
    void draftView(NBTTagCompound out,PanelMenu m,TaskData d,Domain domain,List<TaskData.Job> jobs,TaskData.Job current,long now){
        NBTTagList rows=new NBTTagList(),physical=new NBTTagList();out.setTag("Rows",rows);out.setTag("Physical",physical);out.setUniqueId("Expanded",expanded);
        if(current!=null)out.setTag("Contract",current.terms.copy());
        int[] total=new int[PoolResources.Kind.values().length];for(PoolResources.Binding b:draft)total[b.kind.ordinal()]+=b.amount;out.setIntArray("Totals",total);
        if(domain==null){out.setString("Error","offline");return;}
        FixedResources.Ledger l=ledger(d,domain,current);Map<String,Integer> chosen=FixedResources.amounts(draft);Map<String,List<PoolResources.Device>> fs=families(l);
        Map<UUID,String> names=new HashMap<>();List<Menus.Target> targets=DataOSView.servers(m.tile);for(int i=0;i<targets.size();i++){Menus.Target t=targets.get(i);names.put(ClusterScheduler.serverId(t.item),"#"+i+" "+t.item.getDisplayName());}
        for(Map.Entry<String,List<PoolResources.Device>> entry:fs.entrySet()){
            List<PoolResources.Device> devices=entry.getValue();PoolResources.Device example=devices.get(0);UUID key=rowId(entry.getKey());int amount=0;for(PoolResources.Device device:devices)amount+=chosen.getOrDefault(device.key(),0);
            rows.appendTag(deviceRow(example,key,amount,l.max(draftPool,devices,draft)));
            if(!expanded.equals(key))continue;
            devices.sort(Comparator.comparing((PoolResources.Device x)->names.getOrDefault(x.server,x.server.toString())).thenComparingInt(x->x.slot).thenComparingInt(x->x.unit));
            int start=Math.min(Math.max(0,physicalOffset),Math.max(0,devices.size()-1));out.setInteger("PhysicalCount",devices.size());out.setInteger("PhysicalStart",start);
            for(int i=start;i<Math.min(devices.size(),start+PHYSICAL_WINDOW);i++){PoolResources.Device dev=devices.get(i);int n=chosen.getOrDefault(dev.key(),0);NBTTagCompound r=deviceRow(dev,rowId(dev.key()),n,n+l.free(dev,draftPool,chosen));r.setString("Host",names.getOrDefault(dev.server,dev.server.toString().substring(0,8)));r.setInteger("Slot",dev.slot);r.setInteger("Unit",dev.unit);physical.appendTag(r);}
        }
        FixedResources.Evaluation e=FixedResources.evaluate(candidate(d,current),l,!locked(current));TaskEngine.Result p=e.result;
        out.setInteger("CountUnits",p.units);out.setLong("Rate",p.rate);out.setInteger("CPU",p.cpu);out.setInteger("GPU",p.gpus);out.setInteger("RAM",p.ram);out.setInteger("Disk",p.disk);out.setInteger("Net",p.net);
        String error=validate(d,domain,jobs,current,now);out.setString("Error",commitError.isEmpty()?error:commitError);out.setString("Diagnostic",e.detail);out.setBoolean("DraftAvailable",canEdit(current,now)&&!locked(current));out.setBoolean("CanCommit",error.isEmpty());
    }
    NBTTagCompound snapshot(PanelMenu m,TaskData d){
        List<TaskData.Job> jobs=DataOSView.jobs(m.tile,d);long now=d.now();
        if(type>=0&&!selectionExists(d,m.tile.owner,jobs,now)){reset(section);revision++;}
        NBTTagCompound out=new NBTTagCompound();out.setInteger("Tab",2);out.setTag("Pools",DataOSView.poolRows(m.tile,d));out.setUniqueId("DraftPool",draftPool);out.setString("DraftPoolName",DataOSView.poolName(m.tile,d,draftPool));out.setInteger("Actions",actionMask(m,d,jobs,now));out.setInteger("Revision",revision);out.setLong("Balance",d.wallet(ClusterScheduler.key((Tiles.Computer)m.tile))[0]);out.setInteger("Section",section);out.setInteger("SelectedType",type);out.setUniqueId("Selected",selected);out.setBoolean("Assign",assigning);
        out.setBoolean("Online",((Tiles.Computer)m.tile).online);out.setLong("Now",d.now()/1000*1000);
        NBTTagList rows=new NBTTagList();
        UUID anchor=ClusterScheduler.key((Tiles.Computer)m.tile);Domain domain=live(m,d,anchor);
        if(assigning){draftView(out,m,d,domain,jobs,find(jobs,selected),now);out.setDouble("Factor",Workload.contract(type)?ContractMarket.scoreFactor:Workload.service(type)?ContractMarket.serviceFactor:ContractMarket.mineFactor);return out;}
        else if(section==2||section==5)rows=ContractMarket.list(d.market.owner(m.tile.owner),section==2?"Offers":"History").copy();
        else{
            if(section==1||section==4)for(int i=section==1?0:21;i<(section==1?9:38);i++){NBTTagCompound r=new NBTTagCompound();r.setInteger("Type",i);r.setUniqueId("Key",NONE);rows.appendTag(r);}
            for(TaskData.Job j:jobs)if(!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished")&&visibleJob(j)){
                NBTTagCompound r=j.terms.copy();r.setInteger("Type",j.type);r.setInteger("Job",j.id);if(j.pool!=null){r.setUniqueId("Pool",j.pool);r.setString("PoolName",j.poolLabel);}r.setLong("Progress",j.progress);r.setBoolean("Enabled",j.enabled);r.setLong("Notice",j.notice);r.setLong("Rate",j.cachedRate);r.setLong("Elapsed",j.elapsed);r.setInteger("CountUnits",j.promised);r.setInteger("CPU",j.usedCPU);r.setInteger("RAM",j.usedRAM);r.setInteger("GPU",j.usedGPU);r.setInteger("Disk",j.usedDisk);r.setInteger("Net",j.usedNet);r.setInteger("Hosts",FixedResources.hosts(j.bindings).size());if(selected.equals(j.terms.getUniqueId("Key"))){NBTTagList binds=TaskData.writeBindings(j.bindings);Map<UUID,String> labels=new HashMap<>();List<Menus.Target> hosts=DataOSView.servers(m.tile);for(int i=0;i<hosts.size();i++)labels.put(ClusterScheduler.serverId(hosts.get(i).item),"#"+i+" "+hosts.get(i).item.getDisplayName());for(int i=0;i<binds.tagCount();i++){NBTTagCompound b=binds.getCompoundTagAt(i);UUID host=b.getUniqueId("Server");b.setString("Host",labels.getOrDefault(host,host.toString().substring(0,8)));}r.setTag("Bindings",binds);}
                StringBuilder names=new StringBuilder();for(UUID id:FixedResources.hosts(j.bindings))names.append(id.toString().substring(0,8)).append(" ");r.setString("Machines",names.toString());
                for(int k=0;k<4;k++)r.setLong("Paid"+k,j.payments[k]);
                r.setLong("Penalty",Arrays.stream(j.payments).sum()+(4-j.paidCount)*Math.round(j.cachedRate*ContractMarket.serviceFactor*1000/TaskEngine.SCALE));rows.appendTag(r);}
        }
        out.setDouble("Factor",type<0?0:Workload.contract(type)?ContractMarket.scoreFactor:Workload.service(type)?ContractMarket.serviceFactor:ContractMarket.mineFactor);
        out.setTag("Rows",rows);return out;
    }
}
