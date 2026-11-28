package odc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Network-local read model. IDs are contiguous, zero-based, derived from ordered rack positions and bays.
 * UUIDs remain internal for task allocations and stale-click protection. */
final class DataOSView {
    static final int WINDOW=32;
    /** Pool membership follows the physical LAN patch, independent of power or WAN availability. */
    static List<Menus.Target> servers(Tiles.Owned tile){
        List<Menus.Target> result=new ArrayList<>();Network.Domain domain=Network.scan(tile.getWorld(),tile.getPos());
        if(domain!=null)for(Tiles.Owned device:domain.devices)if(device instanceof Tiles.Rack&&Objects.equals(device.owner,tile.owner)){
            Tiles.Rack rack=(Tiles.Rack)device;for(int bay=0;bay<rack.capacity();bay++)if(rack.networkConnected(bay))result.add(new Menus.Target(rack,bay));
        }return result;
    }
    static int id(Tiles.Rack rack,int bay){List<Menus.Target> all=servers(rack);for(int i=0;i<all.size();i++)if(all.get(i).rack==rack&&all.get(i).bay==bay)return i;return -1;}
    static List<TaskData.Job> jobs(Tiles.Owned tile,TaskData d){
        Set<UUID> anchors=new HashSet<>();Network.Domain domain=Network.scan(tile.getWorld(),tile.getPos());
        if(domain!=null)for(Tiles.Owned t:domain.devices)if(t instanceof Tiles.Computer&&Objects.equals(t.owner,tile.owner))anchors.add(((Tiles.Computer)t).id);
        List<TaskData.Job> result=new ArrayList<>();for(TaskData.Job j:d.jobs)if(Objects.equals(j.owner,tile.owner)&&anchors.contains(j.anchor))result.add(j);
        result.sort(Comparator.comparingInt(j->j.id));return result;
    }
    static SortedMap<String,UUID> pools(Tiles.Owned tile,TaskData d){
        SortedMap<String,UUID> result=new TreeMap<>();for(Menus.Target t:servers(tile)){UUID id=ClusterScheduler.serverId(t.item);String name=d.poolName(id);result.put(name,PoolResources.id(tile.owner,name));}return result;
    }
    static NBTTagList poolRows(Tiles.Owned tile,TaskData d){return poolRows(pools(tile,d));}
    static NBTTagList poolRows(SortedMap<String,UUID> pools){NBTTagList list=new NBTTagList();for(Map.Entry<String,UUID> e:pools.entrySet()){NBTTagCompound x=new NBTTagCompound();x.setString("Name",e.getKey());x.setUniqueId("Key",e.getValue());list.appendTag(x);}return list;}
    static String poolName(Tiles.Owned tile,TaskData d,UUID id){for(Map.Entry<String,UUID> e:pools(tile,d).entrySet())if(e.getValue().equals(id))return e.getKey();for(TaskData.Job j:d.jobs)if(Objects.equals(j.owner,tile.owner)&&Objects.equals(j.pool,id))return j.poolLabel;return id==null||TaskConsole.NONE.equals(id)?"":id.toString().substring(0,8);}
    static boolean hasPool(Tiles.Owned tile,TaskData d,UUID id){return id!=null&&pools(tile,d).containsValue(id);}
    static List<PoolResources.Device> catalog(Tiles.Owned tile,TaskData d,UUID pool){List<PoolResources.Device> result=new ArrayList<>();for(Menus.Target t:servers(tile)){UUID id=ClusterScheduler.serverId(t.item);if(pool==null||d.poolId(tile.owner,id).equals(pool))result.addAll(PoolResources.catalog(id,Hardware.functionalSpec(t.item)));}return result;}
    static NBTTagCompound snapshot(PanelMenu menu){
        Tiles.Computer c=(Tiles.Computer)menu.tile;TaskData data=TaskData.get(c.getWorld());if(menu.tab==2)return menu.console.snapshot(menu,data);
        NBTTagCompound out=new NBTTagCompound();out.setBoolean("Online",c.online);out.setInteger("Tab",menu.tab);if(menu.tab==7){EventSystem.snapshot(menu,data,out);return out;}if(menu.tab==5)return PowerMonitor.snapshot(menu,out);
        List<Menus.Target> servers=servers(c);List<TaskData.Job> jobs=jobs(c,data);DashboardView dashboard=new DashboardView(c,data,servers,jobs);
        UUID pool=null;if(menu.tab==3){out.setTag("Pools",poolRows(dashboard.pools));if(!TaskConsole.NONE.equals(menu.resourcePool)&&!dashboard.pools.containsValue(menu.resourcePool))menu.resourcePool=TaskConsole.NONE;out.setUniqueId("Pool",menu.resourcePool);if(!TaskConsole.NONE.equals(menu.resourcePool))pool=menu.resourcePool;out.setString("PoolName",pool==null?"":poolName(c,data,pool));}
        dashboard.statistics(out,pool);if(menu.tab==0||menu.tab==6)dashboard.finances(out);if(menu.tab==0)dashboard.recent(out);
        List<NBTTagCompound> listing=new ArrayList<>();
        if(menu.tab==1){out.setTag("Pools",poolRows(dashboard.pools));out.setString("Query",menu.serverQuery.encode());DataOSUiState.Query q=menu.serverQuery;
            for(int i=0;i<servers.size();i++){Menus.Target t=servers.get(i);if(!t.rack.networkConnected(t.bay))continue;NBTTagCompound summary=serverSummary(menu,c,data,t,i,dashboard);if(q.accepts(summary.getString("Name"),i,summary.getString("Pool"),summary.getBoolean("Enabled"),summary.getBoolean("Powered")))listing.add(summary);}
            listing.sort(DataOSUiState.ordered(serverOrder(q.column),Comparator.comparingInt(r->r.getInteger("Id")),q.descending));
            if(!TaskConsole.NONE.equals(menu.detailServer))for(int i=0;i<servers.size();i++)if(servers.get(i).rack.networkConnected(servers.get(i).bay)&&menu.detailServer.equals(ClusterScheduler.serverId(servers.get(i).item))){out.setTag("ServerDetail",serverRow(menu,c,data,servers.get(i),i,jobs,dashboard));break;}out.setUniqueId("DetailRequest",menu.detailServer);
        }else if(menu.tab==0){for(TaskData.Job j:jobs){if(!DashboardView.current(j)||!j.enabled&&!j.terms.hasKey("Start")&&!j.terms.getBoolean("Fault")&&j.bindings.isEmpty())continue;NBTTagCompound row=jobLink(j);row.setBoolean("Enabled",j.enabled);row.setInteger("Work",j.work);row.setLong("Progress",j.progress);row.setLong("Income",j.earned);listing.add(row);}}
        else if(menu.tab==3){for(int i=0;i<servers.size();i++){Menus.Target t=servers.get(i);UUID id=ClusterScheduler.serverId(t.item);if(!TaskConsole.NONE.equals(menu.resourcePool)&&!data.poolId(c.owner,id).equals(menu.resourcePool))continue;NBTTagCompound r=serverSummary(menu,c,data,t,i,dashboard);r.setBoolean("ServerLink",true);listing.add(r);}for(TaskData.Job j:jobs)if(!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished")&&(TaskConsole.NONE.equals(menu.resourcePool)||Objects.equals(j.pool,menu.resourcePool)))listing.add(jobLink(j));}
        else if(menu.tab==6)listing.addAll(dashboard.financialRows());
        int count=listing.size(),start=Math.min(Math.max(0,menu.scrollRow),Math.max(0,count-1));out.setInteger("Count",count);out.setInteger("Start",start);
        NBTTagList rows=new NBTTagList();for(int i=start;i<Math.min(count,start+WINDOW);i++)rows.appendTag(listing.get(i));out.setTag("Rows",rows);return out;
    }
    private static NBTTagCompound jobLink(TaskData.Job j){NBTTagCompound r=new NBTTagCompound();r.setInteger("Id",j.id);r.setString("Name",j.name);r.setInteger("Type",j.type);r.setUniqueId("Key",j.terms.getUniqueId("Key"));return r;}
    private static NBTTagCompound serverSummary(PanelMenu menu,Tiles.Computer c,TaskData data,Menus.Target t,int i,DashboardView dashboard){
        NBTTagCompound row=new NBTTagCompound();Tiles.Rack r=t.rack;int b=t.bay;UUID id=ClusterScheduler.serverId(t.item);DashboardStats.Totals s=dashboard.raw(t);
        row.setInteger("Id",i);row.setUniqueId("Key",id);row.setString("Name",t.item.getDisplayName());row.setString("Pool",data.poolName(id));row.setUniqueId("PoolKey",data.poolId(c.owner,id));
        row.setInteger("CPU",(int)s.used[0]);row.setInteger("Cores",(int)s.total[0]);row.setInteger("GPU",(int)s.used[1]);row.setInteger("GPUs",(int)s.total[1]);row.setInteger("RAM",(int)s.used[2]);row.setInteger("RAMTotal",(int)s.total[2]);row.setInteger("DiskTotal",(int)(s.total[3]+s.total[4]+s.total[5]));row.setInteger("Disk",(int)(s.used[3]+s.used[4]+s.used[5]));row.setBoolean("Enabled",(r.enabledMask&(1<<b))!=0);row.setBoolean("Powered",(r.poweredMask&(1<<b))!=0);row.setBoolean("Editable",r.allows(menu.player));row.setInteger("ThermalFault",HardwareDamage.fault(t.item));row.setBoolean("RestartAllowed",Hardware.state(t.item)==ServerSpec.READY&&ThermalRuntime.canRestart(r,b));List<Integer> ids=r.activeJobs.getOrDefault(b,Collections.emptyList());row.setIntArray("Jobs",ids.stream().mapToInt(Integer::intValue).toArray());return row;
    }
    private static Comparator<NBTTagCompound> serverOrder(int column){switch(column){case 0:return Comparator.comparing(r->r.getString("Name"),String.CASE_INSENSITIVE_ORDER);case 1:return Comparator.comparingInt(r->r.getInteger("Id"));case 2:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("CPU")).thenComparingInt(r->r.getInteger("Cores"));case 3:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("GPU")).thenComparingInt(r->r.getInteger("GPUs"));case 4:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("RAM")).thenComparingInt(r->r.getInteger("RAMTotal"));case 5:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("Disk")).thenComparingInt(r->r.getInteger("DiskTotal"));case 6:return Comparator.comparingInt(r->r.getIntArray("Jobs").length);case 7:return Comparator.comparing(r->r.getString("Pool"),String.CASE_INSENSITIVE_ORDER);default:return Comparator.comparingInt(r->r.getBoolean("Powered")?2:r.getBoolean("Enabled")?1:0);}}
    private static NBTTagCompound serverRow(PanelMenu menu,Tiles.Computer c,TaskData data,Menus.Target t,int index,List<TaskData.Job> jobs,DashboardView dashboard){
        NBTTagCompound row=serverSummary(menu,c,data,t,index,dashboard);Tiles.Rack r=t.rack;int b=t.bay;int[] p=Hardware.spec(t.item);UUID id=ClusterScheduler.serverId(t.item);DashboardStats.Totals s=dashboard.aggregate(null,id);DashboardView.write(row,s);row.setIntArray("Parts",p);row.setIntArray("DiskUsed",new int[]{(int)s.used[3],(int)s.used[4],(int)s.used[5]});row.setIntArray("DiskTypes",new int[]{(int)s.total[3],(int)s.total[4],(int)s.total[5]});row.setInteger("Net",(int)s.used[6]);row.setInteger("NetTotal",(int)s.total[6]);row.setInteger("Power",(r.poweredMask&(1<<b))!=0?r.powerUsed[b]:0);row.setInteger("PsuWatts",ServerSpec.psu(p));
        BitSet cores=new BitSet();int gpuMask=0,gpu=0;for(PoolResources.Device dev:PoolResources.catalog(id,p)){if(dev.kind==PoolResources.Kind.CPU&&dashboard.reserved.getOrDefault(dev.key(),0)>0)cores.set(dev.unit+(dev.slot==ServerSpec.CPU2?Part.at(p[ServerSpec.CPU]).cores:0));if(dev.kind==PoolResources.Kind.GPU){if(dashboard.reserved.getOrDefault(dev.key(),0)>0)gpuMask|=1<<gpu;gpu++;}}row.setInteger("GpuMask",gpuMask);row.setByteArray("CoreMask",cores.toByteArray());
        NBTTagList links=new NBTTagList();for(TaskData.Job j:jobs)if(!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished")&&FixedResources.hosts(j.bindings).contains(id)){NBTTagCompound link=jobLink(j);links.appendTag(link);}row.setTag("JobLinks",links);return row;
    }
    static boolean action(PanelMenu m,UUID id,int action,int value,String text){
        if(m.type!=0||!m.canOperate(m.player))return false;TaskData d=TaskData.get(m.player.world);
        if(action==12&&m.tab==5)return PowerMonitor.select(m,id,value);
        if(action==11&&(m.tab==0||m.tab==6||m.tab==7)){for(EventJournal.Entry e:d.recentEvents(m.tile,d.journal.limit()))if(e.id.equals(id)){m.setTab(7);m.detailEvent=id;return true;}return false;}
        if(action==10&&m.tab==7){if(TaskConsole.NONE.equals(id)){m.detailEvent=id;return true;}for(EventJournal.Entry e:d.recentEvents(m.tile,d.journal.limit()))if(e.id.equals(id)){m.detailEvent=id;return true;}return false;}
        if(action==8&&(TaskConsole.NONE.equals(id)||hasPool(m.tile,d,id))){m.setTab(3);m.resourcePool=id;m.scrollRow=0;return true;}
        if(m.tab==3&&action==3&&(TaskConsole.NONE.equals(id)||hasPool(m.tile,d,id))){m.resourcePool=id;return true;}
        if(!((Tiles.Computer)m.tile).online)return false;
        if(action==5&&m.tab==1){if(TaskConsole.NONE.equals(id)){m.detailServer=id;return true;}for(Menus.Target t:servers(m.tile))if(t.rack.networkConnected(t.bay)&&id.equals(ClusterScheduler.serverId(t.item))){m.detailServer=id;return true;}return false;}
        if(action==6){if(m.console.assigning)return false;TaskData.Job j=TaskConsole.find(jobs(m.tile,d),id);if(j==null||j.terms.hasKey("End")||j.terms.getBoolean("Finished"))return false;m.setTab(2);m.console.reset(0);m.console.selected=id;m.console.type=j.type;m.console.revision++;return true;}
        if(action==7){if(m.console.assigning)return false;if(value<0||value>=6)return false;int type;try{type=Integer.parseInt(text);}catch(NumberFormatException e){return false;}m.setTab(2);m.console.reset(value);m.console.selected=id;m.console.type=type;if(!m.console.selectionExists(d,m.tile.owner,jobs(m.tile,d),d.now()))m.console.reset(value);m.console.revision++;return true;}
        if(action==9){for(Menus.Target t:servers(m.tile))if(t.rack.networkConnected(t.bay)&&id.equals(ClusterScheduler.serverId(t.item))){m.setTab(1);m.detailServer=id;return true;}return false;}
        if(m.tab!=1)return false;
        for(Menus.Target t:servers(m.tile))if(t.rack.networkConnected(t.bay)&&ClusterScheduler.serverId(t.item).equals(id)&&t.rack.allows(m.player)){
            if(action==1){boolean enabled=(t.rack.enabledMask&(1<<t.bay))!=0;return enabled==(value!=0)||t.rack.toggle(t.bay);}
            if(action==2)return d.setPool(t.rack,t.bay,text);
            if(action==4){String name=text==null?"":text.trim();if(name.isEmpty()||name.length()>32||name.chars().anyMatch(ch->ch<32||ch==167||ch==127))return false;t.item.setStackDisplayName(name);t.rack.changed();return true;}
        }return false;
    }
}
