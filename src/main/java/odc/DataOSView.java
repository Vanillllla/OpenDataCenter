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
        if(domain!=null)for(Tiles.Owned device:domain.devices)if(device instanceof Tiles.Rack){
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
    static NBTTagCompound snapshot(PanelMenu menu){
        Tiles.Computer c=(Tiles.Computer)menu.tile;TaskData data=TaskData.get(c.getWorld());List<Menus.Target> servers=servers(c);List<TaskData.Job> jobs=jobs(c,data);
        NBTTagCompound out=new NBTTagCompound();out.setBoolean("Online",c.online);out.setInteger("Tab",menu.tab);
        long[] used=new long[8],total=new long[8];int racks=0;long wan=0,power=0,traffic=0,lan=0;
        Network.Domain domain=Network.scan(c.getWorld(),c.getPos());if(domain!=null)for(Tiles.Owned t:domain.devices){if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;racks++;power+=r.lastPower;if(r.switchOnline)lan+=Hardware.switchMbps(r.switchTier());}else if(t instanceof Tiles.Station){Tiles.Station s=(Tiles.Station)t;if(s.online){wan+=s.bandwidth();power+=s.idlePower();}}else if(t instanceof Tiles.Computer&&((Tiles.Computer)t).online)power+=5;}
        for(Menus.Target t:servers){int[] p=Hardware.spec(t.item);Tiles.Rack r=t.rack;int b=t.bay;
            total[0]+=ServerSpec.cpuCores(p);used[0]+=r.cpuUsed[b];total[1]+=ServerSpec.gpuTotal(p);used[1]+=Integer.bitCount(r.gpuUsed[b]);total[2]+=ServerSpec.ramGB(p);used[2]+=r.ramUsed[b];int[] disks=ServerSpec.storageGB(p),reserved=ServerSpec.allocatedStorage(p,r.diskUsed[b]);for(int i=0;i<3;i++){total[3+i]+=disks[i];used[3+i]+=reserved[i];}traffic+=r.netUsed[b];}
        used[6]=traffic;total[6]=wan;used[7]=power;total[7]=lan;
        for(int i=0;i<8;i++){out.setLong("U"+i,used[i]);out.setLong("T"+i,total[i]);}out.setInteger("Racks",racks);out.setInteger("Servers",servers.size());out.setInteger("Tasks",jobs.size());
        UUID corp=ClusterScheduler.key(c);long[] balance=data.wallet(corp);long[] income=new long[2];for(TaskData.Job j:jobs)income[j.type<3?1:0]+=j.earned;
        for(int i=0;i<2;i++){out.setLong("Balance"+i,balance[i]);out.setLong("Income"+i,income[i]);}
        int count=menu.tab==1?servers.size():menu.tab==0?jobs.size():0;int start=Math.min(Math.max(0,menu.scrollRow),Math.max(0,count-1));out.setInteger("Count",count);out.setInteger("Start",start);
        NBTTagList rows=new NBTTagList();for(int i=start;i<Math.min(count,start+WINDOW);i++){NBTTagCompound row=new NBTTagCompound();if(menu.tab==1){Menus.Target t=servers.get(i);Tiles.Rack r=t.rack;int b=t.bay;int[] p=Hardware.spec(t.item);UUID id=ClusterScheduler.serverId(t.item);row.setInteger("Id",i);row.setUniqueId("Key",id);row.setInteger("CPU",r.cpuUsed[b]);row.setInteger("Cores",ServerSpec.cpuCores(p));row.setInteger("GPU",Integer.bitCount(r.gpuUsed[b]));row.setInteger("GPUs",ServerSpec.gpuTotal(p));row.setInteger("RAM",r.ramUsed[b]);row.setInteger("RAMTotal",ServerSpec.ramGB(p));row.setInteger("Disk",r.diskUsed[b]);row.setInteger("DiskTotal",ServerSpec.diskGB(p));row.setInteger("Group",data.groups.getOrDefault(id,0));row.setBoolean("Enabled",(r.enabledMask&(1<<b))!=0);row.setBoolean("Powered",(r.poweredMask&(1<<b))!=0);row.setBoolean("Editable",r.allows(menu.player));List<Integer> ids=r.activeJobs.getOrDefault(b,Collections.emptyList());int[] idsArray=new int[ids.size()];for(int k=0;k<ids.size();k++)idsArray[k]=ids.get(k);row.setIntArray("Jobs",idsArray);}
            else{TaskData.Job j=jobs.get(i);row.setInteger("Id",j.id);row.setString("Name",j.name);row.setInteger("Type",j.type);row.setBoolean("Enabled",j.enabled);row.setInteger("Work",j.work);row.setLong("Progress",j.progress);row.setLong("Income",j.earned);}rows.appendTag(row);}
        out.setTag("Rows",rows);return out;
    }
    static boolean action(PanelMenu m,UUID id,int action,int value){if(m.type!=0||m.tab!=1||!m.canInteractWith(m.player)||!((Tiles.Computer)m.tile).online)return false;for(Menus.Target t:servers(m.tile))if(ClusterScheduler.serverId(t.item).equals(id)&&t.rack.allows(m.player)){if(action==1){boolean enabled=(t.rack.enabledMask&(1<<t.bay))!=0;return enabled==(value!=0)||t.rack.toggle(t.bay);}if(action==2&&value>=0){TaskData d=TaskData.get(m.player.world);if(value==0)d.groups.remove(id);else d.groups.put(id,value);d.markDirty();return true;}}return false;}
}
