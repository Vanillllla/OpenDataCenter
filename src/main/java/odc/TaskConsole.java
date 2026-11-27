package odc;
import java.util.*;
import net.minecraft.nbt.*;

/** Window-local draft. Only the authoritative scheduler and wallet can commit work. */
final class TaskConsole {
    static final UUID NONE=new UUID(0,0);
    int revision,section,type=-1,units=Integer.MAX_VALUE;
    UUID selected=NONE;
    boolean strict,assigning;
    private int previewRevision=-1;
    private TaskEngine.Plan previewPlan;
    private TaskEngine.Result previewResult;
    final Map<UUID,TaskEngine.Limit> limits=new LinkedHashMap<>();
    static final class Domain {
        final List<TaskEngine.Node> nodes;final int[] caps;final int wan;final Set<Integer> excluded;final long tick;TaskEngine.Plan plan;
        Domain(List<TaskEngine.Node> n,int[] c,int w,Set<Integer> e,long t){nodes=n;caps=c;wan=w;excluded=e;tick=t;}
    }
    static TaskData.Job find(List<TaskData.Job> jobs,UUID id){
        for(TaskData.Job j:jobs)if(j.terms.getUniqueId("Key").equals(id))return j;
        return null;
    }
    static boolean sameLimits(Map<UUID,TaskEngine.Limit> a,Map<UUID,TaskEngine.Limit> b){
        if(!a.keySet().equals(b.keySet()))return false;
        for(UUID id:a.keySet()){TaskEngine.Limit x=a.get(id),y=b.get(id);
            if(!Objects.equals(x.allowedCores,y.allowedCores)||x.cpu!=y.cpu||x.mask!=y.mask||x.ram!=y.ram||x.disk!=y.disk||x.io!=y.io||x.net!=y.net)return false;}
        return true;
    }
    static void copyLimits(TaskEngine.Task job,Map<UUID,TaskEngine.Limit> limits){
        job.manual.clear();job.manual.putAll(limits);job.auto=false;
    }
    TaskEngine.Result preview(TaskData d,Domain domain,List<TaskData.Job> jobs,TaskData.Job current){
        if(type<0 || domain==null)return new TaskEngine.Result();
        if(previewPlan==domain.plan && previewRevision==revision)return previewResult;
        TaskEngine.Task draft=new TaskEngine.Task(current==null?d.next:current.id,type);
        draft.enabled=true;draft.unitLimit=units;draft.strict=strict;if(assigning)copyLimits(draft,limits);
        List<TaskEngine.Task> candidates=new ArrayList<>(jobs);candidates.remove(current);candidates.add(draft);
        TaskEngine.Plan p=TaskEngine.plan(domain.nodes,candidates,domain.caps,domain.wan,d.groups,domain.excluded);
        previewPlan=domain.plan;previewRevision=revision;previewResult=p.overload.isEmpty()?p.tasks.get(draft.id):new TaskEngine.Result();return previewResult;
    }
    static int freeSwitch(Domain d,TaskEngine.Plan p,int rack){
        int free=d.caps[rack]*10;
        for(int i=0;i<d.nodes.size();i++)if(d.nodes.get(i).rack==rack)free-=p.net[i];
        return free;
    }
    Domain live(PanelMenu m,TaskData d,UUID anchor){
        Domain domain=d.domains.get(anchor);
        return domain!=null && m.player.world.getTotalWorldTime()-domain.tick<=20?domain:null;
    }
    void command(PanelMenu m,TaskData d,TaskCommand cmd){
        UUID wallet=ClusterScheduler.key((Tiles.Computer)m.tile),owner=m.tile.owner;
        if(owner==null || !m.tile.allows(m.player))return;
        List<TaskData.Job> jobs=DataOSView.jobs(m.tile,d);
        TaskData.Job j=find(jobs,cmd.key);
        Domain domain=live(m,d,wallet);
        long now=d.now();
        if(j!=null){if(j.terms.getLong("Deadline")>0 && now>=j.terms.getLong("Deadline"))return;d.settle(j,now);if(j.terms.hasKey("End"))return;}
        switch(cmd.op){
        case 0: if(cmd.value>=0 && cmd.value<6){section=cmd.value;assigning=false;type=-1;selected=NONE;}break;
        case 1: // Select an existing job, an offer, or a catalog row.
            assigning=false;selected=cmd.key;type=-1;
            if(j!=null){type=j.type;break;}
            if(section==2){NBTTagList offers=ContractMarket.list(d.market.owner(owner),"Offers");for(int i=0;i<offers.tagCount();i++){NBTTagCompound o=offers.getCompoundTagAt(i);if(o.getUniqueId("Key").equals(selected))type=o.getInteger("Type");}}
            if(cmd.key.equals(NONE) && ((section==1&&cmd.value>=0&&cmd.value<9)||(section==4&&cmd.value>=21&&cmd.value<38)))type=cmd.value;
            break;
        case 2: if(section==2 && selected.equals(cmd.key))d.market.accept(d,owner,wallet,selected,now);break;
        case 3: if(type<0 || !selected.equals(cmd.key) || (Workload.contract(type)&&j==null))return;
            if(j!=null && j.periodic() && j.enabled && (j.notice==0||now<j.notice))return;
            assigning=true;limits.clear();if(j!=null)limits.putAll(j.manual);strict=j!=null&&j.strict;units=j==null?Integer.MAX_VALUE:j.unitLimit;break;
        case 4: if(!assigning || domain==null)return;
            for(TaskEngine.Node n:domain.nodes)if(n.id.equals(cmd.key)){
                if(cmd.value==0)limits.remove(n.id);else{
                    TaskEngine.Limit l=new TaskEngine.Limit();
                    if(cmd.value==2){for(int k=0;k<6;k++)if(k!=1 && cmd.limits[k]<0)return;
                        BitSet cores=new BitSet();for(int c=0;c<256;c++)if((cmd.limits[6+c/32]&(1<<(c%32)))!=0)cores.set(c);
                        if(!cores.isEmpty())l.allowedCores=cores;
                        l.cpu=cmd.limits[0];l.mask=cmd.limits[1];l.ram=cmd.limits[2];l.disk=cmd.limits[3];l.io=cmd.limits[4];l.net=(int)Math.min(Integer.MAX_VALUE,80L*cmd.limits[5]);}
                    limits.put(n.id,l);
                }break;
            }break;
        case 5: if(assigning){units=cmd.value<=0?Integer.MAX_VALUE:cmd.value;strict=cmd.limits[0]!=0;}break;
        case 6: if(!assigning || type<0 || !selected.equals(cmd.key) || limits.isEmpty() || domain==null)return;
            if((!selected.equals(NONE)&&j==null)||(j==null&&Workload.contract(type)))return;
            if(j!=null && (j.terms.getBoolean("Single")&&limits.size()!=1 || j.terms.hasKey("Start") &&
                (j.terms.getBoolean("Fixed") && (units!=j.unitLimit || strict!=j.strict || !sameLimits(j.manual,limits)) || j.terms.getBoolean("Pinned")&&!limits.keySet().equals(j.manual.keySet()))))return;
            if(preview(d,domain,jobs,j).units<=0)return;
            if(j==null && jobs.size()>=256)return;
            if(j==null){j=new TaskData.Job(d.next++,type,wallet,owner);d.jobs.add(j);}
            if(j.periodic()&&j.notice>0&&now>=j.notice){j.promised=0;j.machines=Collections.emptySet();}
            copyLimits(j,limits);j.unitLimit=units;j.strict=strict;j.enabled=true;j.notice=0;j.terms.setBoolean("Fault",false);
            selected=j.terms.getUniqueId("Key");assigning=false;section=0;break;
        case 8: if(j!=null && j.enabled && j.periodic() && j.notice==0)j.notice=now+300000;break;
        case 9: if(j!=null && now<j.notice)j.notice=0;break;
        case 10: if(j!=null && (!j.periodic()||!j.enabled||j.notice>0&&now>=j.notice))d.market.end(d,j,"cancelled",0);break;
        case 7: if(j!=null && j.enabled && (!j.periodic() || j.notice>0&&now>=j.notice) &&
                (!Workload.contract(j.type)||!ContractMarket.CONTINUOUS[j.terms.getInteger("Rule")])){j.enabled=false;j.cachedRate=0;j.machines=Collections.emptySet();}break;

        }
        d.markDirty();
    }
    NBTTagCompound snapshot(PanelMenu m,TaskData d){
        NBTTagCompound out=new NBTTagCompound();out.setInteger("Tab",2);out.setInteger("Revision",revision);out.setLong("Balance",d.wallet(ClusterScheduler.key((Tiles.Computer)m.tile))[0]);out.setInteger("Section",section);out.setInteger("SelectedType",type);out.setUniqueId("Selected",selected);out.setBoolean("Assign",assigning);out.setBoolean("Strict",strict);out.setInteger("Units",units);
        out.setBoolean("Online",((Tiles.Computer)m.tile).online);out.setLong("Now",d.now()/1000*1000);
        NBTTagList rows=new NBTTagList();List<TaskData.Job> jobs=DataOSView.jobs(m.tile,d);
        UUID anchor=ClusterScheduler.key((Tiles.Computer)m.tile);Domain domain=live(m,d,anchor);
        if(assigning && domain!=null){
            Map<UUID,String> names=new HashMap<>();
            for(Menus.Target target:DataOSView.servers(m.tile))names.put(ClusterScheduler.serverId(target.item),target.item.getDisplayName());
            for(int i=0;i<domain.nodes.size();i++){
                TaskEngine.Node n=domain.nodes.get(i);TaskEngine.Plan plan=domain.plan;
                NBTTagCompound r=new NBTTagCompound();r.setUniqueId("Key",n.id);r.setString("Name",names.getOrDefault(n.id,""));
                r.setInteger("CPU",n.cores);r.setInteger("RAM",n.ram);r.setInteger("Disk",n.disk);r.setInteger("Net",n.net);r.setInteger("GPU",n.gpu.length);r.setBoolean("Chosen",limits.containsKey(n.id));
                r.setInteger("FreeCPU",n.cores-plan.cpu[i]);r.setInteger("FreeRAM",n.ram-plan.ram[i]);r.setInteger("FreeDisk",n.disk-plan.disk[i]);r.setInteger("FreeNet",Math.max(0,Math.min(n.net*10-plan.net[i],Math.min(domain.wan*10-Arrays.stream(plan.net).sum(),freeSwitch(domain,plan,n.rack)))));r.setInteger("FreeIO",Arrays.stream(plan.freeIO[i]).sum());
                StringBuilder chips=new StringBuilder();for(int k=0;k<n.gpu.length;k++)if((plan.gpu[i]&(1<<k))==0)chips.append(k).append("=").append(Part.at(n.gpu[k]).gb).append(" ");
                Workload w=Workload.ALL[type];int fit=Integer.MAX_VALUE,eligible=0;
                for(int k=0;k<n.gpu.length;k++)if((plan.gpu[i]&(1<<k))==0 && Part.at(n.gpu[k]).gb>=w.vram)eligible++;
                fit=TaskEngine.bound(fit,r.getInteger("FreeCPU"),w.cpu);fit=TaskEngine.bound(fit,eligible,w.gpu);
                fit=TaskEngine.bound(fit,r.getInteger("FreeRAM"),w.ram);fit=TaskEngine.bound(fit,r.getInteger("FreeNet"),(int)Math.round(w.net*80));
                if(w.disk>0 || w.io>0){double slots=0;for(int k=ServerSpec.SATA;k<ServerSpec.PSU;k++)slots+=Math.min(w.disk==0?Double.POSITIVE_INFINITY:(double)plan.freeSpace[i][k]/w.disk,w.io==0?Double.POSITIVE_INFINITY:(double)plan.freeIO[i][k]/w.io);fit=Math.min(fit,(int)slots);}
                r.setInteger("Fit",domain.excluded.contains(i)?0:fit);r.setString("VRAM",chips.toString());r.setInteger("FreeGPU",n.gpu.length-Integer.bitCount(plan.gpu[i]));r.setBoolean("Online",!domain.excluded.contains(i));rows.appendTag(r);
            }
            TaskEngine.Result p=preview(d,domain,jobs,find(jobs,selected));TaskData.Job current=find(jobs,selected);
            if(current!=null)out.setTag("Contract",current.terms.copy());out.setInteger("CountUnits",p.units);out.setLong("Rate",p.rate);out.setInteger("CPU",p.cpu);out.setInteger("RAM",p.ram);out.setInteger("Disk",p.disk);out.setInteger("GPU",p.gpus);out.setInteger("Net",p.net);
        }else if(section==2||section==5)rows=ContractMarket.list(d.market.owner(m.tile.owner),section==2?"Offers":"History").copy();
        else{
            if(section==1||section==4)for(int i=section==1?0:21;i<(section==1?9:38);i++){NBTTagCompound r=new NBTTagCompound();r.setInteger("Type",i);r.setUniqueId("Key",NONE);rows.appendTag(r);}
            for(TaskData.Job j:jobs)if(section==0 || section==3&&Workload.contract(j.type)||section==4&&j.periodic()){
                NBTTagCompound r=j.terms.copy();r.setInteger("Type",j.type);r.setInteger("Job",j.id);r.setLong("Progress",j.progress);r.setBoolean("Enabled",j.enabled);r.setLong("Notice",j.notice);r.setLong("Rate",j.cachedRate);r.setLong("Elapsed",j.elapsed);r.setInteger("CountUnits",j.promised);r.setInteger("CPU",j.usedCPU);r.setInteger("RAM",j.usedRAM);r.setInteger("GPU",j.usedGPU);r.setInteger("Disk",j.usedDisk);r.setInteger("Net",j.usedNet);r.setInteger("Hosts",j.manual.size());
                StringBuilder names=new StringBuilder();for(UUID id:j.manual.keySet())names.append(id.toString().substring(0,8)).append(" ");r.setString("Machines",names.toString());
                for(int k=0;k<4;k++)r.setLong("Paid"+k,j.payments[k]);
                r.setLong("Penalty",Arrays.stream(j.payments).sum()+(4-j.paidCount)*Math.round(j.cachedRate*ContractMarket.serviceFactor*1000/TaskEngine.SCALE));rows.appendTag(r);}
        }
        if(!assigning && section==2 && type>=0 && domain!=null)out.setLong("Capacity",preview(d,domain,jobs,null).rate);
        out.setDouble("Factor",type<0?0:Workload.contract(type)?ContractMarket.scoreFactor:Workload.service(type)?ContractMarket.serviceFactor:ContractMarket.mineFactor);
        out.setTag("Rows",rows);return out;
    }
}
