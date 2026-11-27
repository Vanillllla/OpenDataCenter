package odc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Network-local read model. IDs are contiguous, zero-based, derived from ordered rack positions and bays.
 * UUIDs remain internal for task allocations and stale-click protection. */
final class DataOSView {
    static final int WINDOW=32;
    static List<Menus.Target> servers(Tiles.Owned tile){
        List<Menus.Target> result=new ArrayList<>();Network.Domain domain=Network.scan(tile.getWorld(),tile.getPos());
        if(domain!=null)for(Tiles.Owned device:domain.devices)if(device instanceof Tiles.Rack&&Objects.equals(device.owner,tile.owner)){
            Tiles.Rack rack=(Tiles.Rack)device;for(int bay=0;bay<rack.capacity();bay++)if(rack.isServer(bay))result.add(new Menus.Target(rack,bay));
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
    static NBTTagList poolRows(Tiles.Owned tile,TaskData d){NBTTagList list=new NBTTagList();for(Map.Entry<String,UUID> e:pools(tile,d).entrySet()){NBTTagCompound x=new NBTTagCompound();x.setString("Name",e.getKey());x.setUniqueId("Key",e.getValue());list.appendTag(x);}return list;}
    static String poolName(Tiles.Owned tile,TaskData d,UUID id){for(Map.Entry<String,UUID> e:pools(tile,d).entrySet())if(e.getValue().equals(id))return e.getKey();for(TaskData.Job j:d.jobs)if(Objects.equals(j.owner,tile.owner)&&Objects.equals(j.pool,id))return j.poolLabel;return id==null||TaskConsole.NONE.equals(id)?"":id.toString().substring(0,8);}
    static boolean hasPool(Tiles.Owned tile,TaskData d,UUID id){return id!=null&&pools(tile,d).containsValue(id);}
    static List<PoolResources.Device> catalog(Tiles.Owned tile,TaskData d,UUID pool){List<PoolResources.Device> result=new ArrayList<>();for(Menus.Target t:servers(tile)){UUID id=ClusterScheduler.serverId(t.item);if(pool==null||d.poolId(tile.owner,id).equals(pool))result.addAll(PoolResources.catalog(id,Hardware.spec(t.item)));}return result;}
    static int[] disksUsed(Tiles.Owned tile,TaskData d,Menus.Target t){
        int[] parts=Hardware.spec(t.item),used=new int[3];UUID id=ClusterScheduler.serverId(t.item);TaskConsole.Domain live=d.domains.get(ClusterScheduler.key((Tiles.Computer)tile));
        if(live!=null&&tile.getWorld().getTotalWorldTime()-live.tick<=20)for(int i=0;i<live.nodes.size();i++)if(id.equals(live.nodes.get(i).id)&&Arrays.equals(parts,live.nodes.get(i).parts)){
            for(int k=ServerSpec.SATA;k<ServerSpec.PSU;k++){Part part=Part.at(parts[k]);int type=part.kind==Part.Kind.HDD?2:part.kind==Part.Kind.SSD?1:part.kind==Part.Kind.NVME||part.kind==Part.Kind.PCIE?0:-1;if(type>=0)used[type]+=Math.max(0,part.gb-live.plan.freeSpace[i][k]);}return used;
        }
        // Physical reservations survive power loss and separation from their controller.
        int[] held=new int[ServerSpec.PSU];for(TaskData.Job j:d.reservingJobs())for(PoolResources.Binding binding:j.bindings)if(binding.storage()&&id.equals(binding.server)&&binding.slot>=ServerSpec.SATA&&binding.slot<ServerSpec.PSU&&parts[binding.slot]==binding.model)held[binding.slot]=(int)Math.min(Part.at(parts[binding.slot]).gb,(long)held[binding.slot]+binding.amount);
        for(int k=ServerSpec.SATA;k<ServerSpec.PSU;k++){Part part=Part.at(parts[k]);int type=part.kind==Part.Kind.HDD?2:part.kind==Part.Kind.SSD?1:part.kind==Part.Kind.NVME||part.kind==Part.Kind.PCIE?0:-1;if(type>=0)used[type]+=held[k];}return used;
    }
    static NBTTagCompound snapshot(PanelMenu menu){
        Tiles.Computer c=(Tiles.Computer)menu.tile;TaskData data=TaskData.get(c.getWorld());if(menu.tab==2)return menu.console.snapshot(menu,data);List<Menus.Target> servers=servers(c);List<TaskData.Job> jobs=jobs(c,data);
        NBTTagCompound out=new NBTTagCompound();out.setBoolean("Online",c.online);out.setInteger("Tab",menu.tab);if(menu.tab==7){EventSystem.snapshot(menu,data,out);return out;}if(menu.tab==3){out.setTag("Pools",poolRows(c,data));if(!TaskConsole.NONE.equals(menu.resourcePool)&&!hasPool(c,data,menu.resourcePool))menu.resourcePool=TaskConsole.NONE;out.setUniqueId("Pool",menu.resourcePool);out.setString("PoolName",TaskConsole.NONE.equals(menu.resourcePool)?"":poolName(c,data,menu.resourcePool));}
        long[] used=new long[8],total=new long[8];int racks=0;long wan=0,power=0,traffic=0,lan=0;Map<Tiles.Rack,Long> poolLinks=new IdentityHashMap<>();
        Network.Domain domain=Network.scan(c.getWorld(),c.getPos());if(domain!=null)for(Tiles.Owned t:domain.devices){if(!Objects.equals(t.owner,c.owner))continue;if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;racks++;power+=r.lastPower;if(r.switchOnline)lan+=Hardware.switchMbps(r.switchTier());}else if(t instanceof Tiles.Station){Tiles.Station s=(Tiles.Station)t;if(s.online){wan+=s.bandwidth();power+=s.idlePower();}}else if(t instanceof Tiles.Computer&&((Tiles.Computer)t).online)power+=5;}
        for(Menus.Target t:servers){if(menu.tab==3&&!TaskConsole.NONE.equals(menu.resourcePool)&&!data.poolId(c.owner,ClusterScheduler.serverId(t.item)).equals(menu.resourcePool))continue;int[] p=Hardware.spec(t.item);Tiles.Rack r=t.rack;int b=t.bay;
            total[0]+=ServerSpec.cpuCores(p);used[0]+=r.cpuUsed[b];total[1]+=ServerSpec.gpuTotal(p);used[1]+=Integer.bitCount(r.gpuUsed[b]);total[2]+=ServerSpec.ramGB(p);used[2]+=r.ramUsed[b];int[] disks=ServerSpec.storageGB(p),reserved=disksUsed(c,data,t);for(int i=0;i<3;i++){total[3+i]+=disks[i];used[3+i]+=reserved[i];}traffic+=r.netUsed[b];if(menu.tab==3&&r.switchOnline&&r.portOf(b)>=0&&(r.poweredMask&(1<<b))!=0)poolLinks.merge(r,(long)Math.min(ServerSpec.nic(p),Hardware.portMbps(r.switchTier())),Long::sum);}
        long poolNetwork=0;for(Map.Entry<Tiles.Rack,Long> e:poolLinks.entrySet())poolNetwork+=Math.min(e.getValue(),Hardware.switchMbps(e.getKey().switchTier()));used[6]=traffic;total[6]=menu.tab==3?Math.min(wan,poolNetwork):wan;used[7]=power;total[7]=lan;
        for(int i=0;i<8;i++){out.setLong("U"+i,used[i]);out.setLong("T"+i,total[i]);}out.setInteger("Racks",racks);out.setInteger("Servers",servers.size());out.setInteger("Tasks",jobs.size());
        UUID corp=ClusterScheduler.key(c);long[] balance=data.wallet(corp);long[] income=new long[2];for(TaskData.Job j:jobs)income[j.type<9?1:0]+=j.earned;
        for(int i=0;i<2;i++){out.setLong("Balance"+i,balance[i]);out.setLong("Income"+i,income[i]);}
        List<NBTTagCompound> listing=new ArrayList<>();
        if(menu.tab==1){out.setTag("Pools",poolRows(c,data));out.setString("Query",menu.serverQuery.encode());DataOSUiState.Query q=menu.serverQuery;
            for(int i=0;i<servers.size();i++){Menus.Target t=servers.get(i);NBTTagCompound summary=serverSummary(menu,c,data,t,i);if(q.accepts(summary.getString("Name"),i,summary.getString("Pool"),summary.getBoolean("Enabled"),summary.getBoolean("Powered")))listing.add(summary);}
            listing.sort(DataOSUiState.ordered(serverOrder(q.column),Comparator.comparingInt(r->r.getInteger("Id")),q.descending));
            if(!TaskConsole.NONE.equals(menu.detailServer))for(int i=0;i<servers.size();i++)if(menu.detailServer.equals(ClusterScheduler.serverId(servers.get(i).item))){out.setTag("ServerDetail",serverRow(menu,c,data,servers.get(i),i,corp,jobs));break;}out.setUniqueId("DetailRequest",menu.detailServer);
        }else if(menu.tab==0){for(TaskData.Job j:jobs){NBTTagCompound row=jobLink(j);row.setBoolean("Enabled",j.enabled);row.setInteger("Work",j.work);row.setLong("Progress",j.progress);row.setLong("Income",j.earned);listing.add(row);}}
        else if(menu.tab==3){for(int i=0;i<servers.size();i++){Menus.Target t=servers.get(i);UUID id=ClusterScheduler.serverId(t.item);if(!TaskConsole.NONE.equals(menu.resourcePool)&&!data.poolId(c.owner,id).equals(menu.resourcePool))continue;NBTTagCompound r=serverSummary(menu,c,data,t,i);r.setBoolean("ServerLink",true);listing.add(r);}for(TaskData.Job j:jobs)if(!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished")&&(TaskConsole.NONE.equals(menu.resourcePool)||Objects.equals(j.pool,menu.resourcePool)))listing.add(jobLink(j));}
        int count=listing.size(),start=Math.min(Math.max(0,menu.scrollRow),Math.max(0,count-1));out.setInteger("Count",count);out.setInteger("Start",start);
        NBTTagList rows=new NBTTagList();for(int i=start;i<Math.min(count,start+WINDOW);i++)rows.appendTag(listing.get(i));out.setTag("Rows",rows);return out;
    }
    private static NBTTagCompound jobLink(TaskData.Job j){NBTTagCompound r=new NBTTagCompound();r.setInteger("Id",j.id);r.setString("Name",j.name);r.setInteger("Type",j.type);r.setUniqueId("Key",j.terms.getUniqueId("Key"));return r;}
    private static NBTTagCompound serverSummary(PanelMenu menu,Tiles.Computer c,TaskData data,Menus.Target t,int i){NBTTagCompound row=new NBTTagCompound();Tiles.Rack r=t.rack;int b=t.bay;int[] p=Hardware.spec(t.item);UUID id=ClusterScheduler.serverId(t.item);row.setInteger("Id",i);row.setUniqueId("Key",id);row.setString("Name",t.item.getDisplayName());row.setString("Pool",data.poolName(id));row.setUniqueId("PoolKey",data.poolId(c.owner,id));row.setInteger("CPU",r.cpuUsed[b]);row.setInteger("Cores",ServerSpec.cpuCores(p));row.setInteger("GPU",Integer.bitCount(r.gpuUsed[b]));row.setInteger("GPUs",ServerSpec.gpuTotal(p));row.setInteger("RAM",r.ramUsed[b]);row.setInteger("RAMTotal",ServerSpec.ramGB(p));row.setInteger("DiskTotal",ServerSpec.diskGB(p));row.setInteger("Disk",Arrays.stream(disksUsed(c,data,t)).sum());row.setBoolean("Enabled",(r.enabledMask&(1<<b))!=0);row.setBoolean("Powered",(r.poweredMask&(1<<b))!=0);row.setBoolean("Editable",r.allows(menu.player));List<Integer> ids=r.activeJobs.getOrDefault(b,Collections.emptyList());row.setIntArray("Jobs",ids.stream().mapToInt(Integer::intValue).toArray());return row;}
    private static Comparator<NBTTagCompound> serverOrder(int column){switch(column){case 0:return Comparator.comparing(r->r.getString("Name"),String.CASE_INSENSITIVE_ORDER);case 1:return Comparator.comparingInt(r->r.getInteger("Id"));case 2:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("CPU")).thenComparingInt(r->r.getInteger("Cores"));case 3:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("GPU")).thenComparingInt(r->r.getInteger("GPUs"));case 4:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("RAM")).thenComparingInt(r->r.getInteger("RAMTotal"));case 5:return Comparator.comparingInt((NBTTagCompound r)->r.getInteger("Disk")).thenComparingInt(r->r.getInteger("DiskTotal"));case 6:return Comparator.comparingInt(r->r.getIntArray("Jobs").length);case 7:return Comparator.comparing(r->r.getString("Pool"),String.CASE_INSENSITIVE_ORDER);default:return Comparator.comparingInt(r->r.getBoolean("Powered")?2:r.getBoolean("Enabled")?1:0);}}
    private static NBTTagCompound serverRow(PanelMenu menu,Tiles.Computer c,TaskData data,Menus.Target t,int index,UUID corp,List<TaskData.Job> jobs){NBTTagCompound row=new NBTTagCompound();Tiles.Rack r=t.rack;int b=t.bay;int[] p=Hardware.spec(t.item);UUID id=ClusterScheduler.serverId(t.item);row.setInteger("Id",index);row.setUniqueId("Key",id);row.setInteger("CPU",r.cpuUsed[b]);row.setInteger("Cores",ServerSpec.cpuCores(p));row.setInteger("GPU",Integer.bitCount(r.gpuUsed[b]));row.setInteger("GPUs",ServerSpec.gpuTotal(p));row.setInteger("RAM",r.ramUsed[b]);row.setInteger("RAMTotal",ServerSpec.ramGB(p));row.setInteger("Disk",Arrays.stream(disksUsed(c,data,t)).sum());row.setInteger("DiskTotal",ServerSpec.diskGB(p));row.setString("Name",t.item.getDisplayName());row.setString("Pool",data.poolName(id));row.setUniqueId("PoolKey",data.poolId(c.owner,id));row.setIntArray("Parts",p);row.setIntArray("DiskUsed",disksUsed(c,data,t));row.setIntArray("DiskTypes",ServerSpec.storageGB(p));row.setInteger("Net",r.netUsed[b]);row.setInteger("NetTotal",ServerSpec.nic(p));row.setInteger("Power",r.powerUsed[b]);row.setInteger("GpuMask",r.gpuUsed[b]);TaskConsole.Domain live=data.domains.get(corp);if(live!=null&&c.getWorld().getTotalWorldTime()-live.tick<=20)for(int k=0;k<live.nodes.size();k++)if(id.equals(live.nodes.get(k).id)&&Arrays.equals(p,live.nodes.get(k).parts)){row.setByteArray("CoreMask",live.plan.usedCores[k].toByteArray());break;}row.setBoolean("Enabled",(r.enabledMask&(1<<b))!=0);row.setBoolean("Powered",(r.poweredMask&(1<<b))!=0);row.setBoolean("Editable",r.allows(menu.player));List<Integer> ids=r.activeJobs.getOrDefault(b,Collections.emptyList());int[] idsArray=new int[ids.size()];for(int k=0;k<ids.size();k++)idsArray[k]=ids.get(k);row.setIntArray("Jobs",idsArray);
        NBTTagList links=new NBTTagList();for(TaskData.Job j:jobs)if(!j.terms.hasKey("End")&&!j.terms.getBoolean("Finished")&&FixedResources.hosts(j.bindings).contains(id)){NBTTagCompound link=jobLink(j);links.appendTag(link);}row.setTag("JobLinks",links);return row;
    }
    static boolean action(PanelMenu m,UUID id,int action,int value,String text){
        if(m.type!=0||!m.canInteractWith(m.player))return false;TaskData d=TaskData.get(m.player.world);
        if(action==10&&m.tab==7){if(TaskConsole.NONE.equals(id)){m.detailEvent=id;return true;}for(EventJournal.Entry e:d.recentEvents(m.tile,d.journal.limit()))if(e.id.equals(id)){m.detailEvent=id;return true;}return false;}
        if(!((Tiles.Computer)m.tile).online)return false;
        if(action==5&&m.tab==1){if(TaskConsole.NONE.equals(id)){m.detailServer=id;return true;}for(Menus.Target t:servers(m.tile))if(id.equals(ClusterScheduler.serverId(t.item))){m.detailServer=id;return true;}return false;}
        if(action==6){if(m.console.assigning)return false;TaskData.Job j=TaskConsole.find(jobs(m.tile,d),id);if(j==null||j.terms.hasKey("End")||j.terms.getBoolean("Finished"))return false;m.setTab(2);m.console.reset(0);m.console.selected=id;m.console.type=j.type;m.console.revision++;return true;}
        if(action==7){if(m.console.assigning)return false;if(value<0||value>=6)return false;int type;try{type=Integer.parseInt(text);}catch(NumberFormatException e){return false;}m.setTab(2);m.console.reset(value);m.console.selected=id;m.console.type=type;if(!m.console.selectionExists(d,m.tile.owner,jobs(m.tile,d),d.now()))m.console.reset(value);m.console.revision++;return true;}
        if(action==8&&(TaskConsole.NONE.equals(id)||hasPool(m.tile,d,id))){m.setTab(3);m.resourcePool=id;m.scrollRow=0;return true;}
        if(action==9){for(Menus.Target t:servers(m.tile))if(id.equals(ClusterScheduler.serverId(t.item))){m.setTab(1);m.detailServer=id;return true;}return false;}
        if(m.tab==3&&action==3&&(TaskConsole.NONE.equals(id)||hasPool(m.tile,d,id))){m.resourcePool=id;return true;}
        if(m.tab!=1)return false;
        for(Menus.Target t:servers(m.tile))if(ClusterScheduler.serverId(t.item).equals(id)&&t.rack.allows(m.player)){
            if(action==1){boolean enabled=(t.rack.enabledMask&(1<<t.bay))!=0;return enabled==(value!=0)||t.rack.toggle(t.bay);}
            if(action==2)return d.setPool(t.rack,t.bay,text);
            if(action==4){String name=text==null?"":text.trim();if(name.isEmpty()||name.length()>32||name.chars().anyMatch(ch->ch<32||ch==167||ch==127))return false;t.item.setStackDisplayName(name);t.rack.changed();return true;}
        }return false;
    }
}
