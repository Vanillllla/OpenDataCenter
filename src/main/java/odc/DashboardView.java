package odc;

import java.util.*;
import net.minecraft.nbt.*;

/** Single read model for the dashboard, pool statistics, server rows and wallet. */
final class DashboardView {
    final Tiles.Computer c;final TaskData data;final List<Menus.Target> servers;final List<TaskData.Job> jobs;
    final Map<UUID,DashboardStats.Totals> inventory=new HashMap<>();
    final Map<Tiles.Rack,Long> caps=new IdentityHashMap<>(),heldByRack=new IdentityHashMap<>();
    final Map<String,Integer> reserved;final SortedMap<String,UUID> pools=new TreeMap<>();
    long wan,power,lan,traffic,heldNet,actualNet;int racks,powered,active,paused;boolean controller;
    final long[] income=new long[2];
    DashboardView(Tiles.Computer c,TaskData data,List<Menus.Target> servers,List<TaskData.Job> jobs){this.c=c;this.data=data;this.servers=servers;this.jobs=jobs;
        Network.Domain domain=Network.scan(c.getWorld(),c.getPos());if(domain!=null)for(Tiles.Owned t:domain.devices){if(!Objects.equals(t.owner,c.owner))continue;if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;racks++;power+=r.lastPower;long cap=r.switchOnline?Hardware.switchMbps(r.switchTier()):0;lan+=cap;caps.put(r,cap);}else if(t instanceof Tiles.Station){Tiles.Station s=(Tiles.Station)t;if(s.online&&s.energy.fed()){wan+=s.bandwidth();power+=s.idlePower();}}else if(t instanceof Tiles.Computer&&((Tiles.Computer)t).online&&((Tiles.Computer)t).energy.fed()){controller=true;power+=5;}}
        reserved=DashboardStats.reservations(data.reservingJobs());
        for(Menus.Target t:servers){Tiles.Rack r=t.rack;int b=t.bay;UUID id=ClusterScheduler.serverId(t.item);int[] p=Hardware.spec(t.item);boolean on=(r.poweredMask&(1<<b))!=0;if(on)powered++;String pool=data.poolName(id);pools.put(pool,PoolResources.id(c.owner,pool));boolean available=on&&Hardware.state(t.item)==ServerSpec.READY&&controller&&r.switchOnline&&r.portOf(b)>=0&&wan>0&&ServerSpec.nic(p)>0;int port=available?r.patchLimit(b):0;
            DashboardStats.Totals s=DashboardStats.inventory(PoolResources.catalog(id,p),reserved,available,port);inventory.put(id,s);long held=s.used[DashboardStats.NET];if((r.enabledMask&(1<<b))!=0&&Hardware.state(t.item)==ServerSpec.READY){heldNet+=held;heldByRack.merge(r,held,Long::sum);}traffic+=r.netUsed[b];}
        for(TaskData.Job j:jobs){if(!current(j))continue;if(j.enabled){active++;if(j.served&&j.full)actualNet+=(j.usedNet+9L)/10;}else if(j.terms.hasKey("Start")||j.terms.getBoolean("Fault")||!j.bindings.isEmpty())paused++;long n=DashboardStats.hourly(j.type,j.cachedRate,j.enabled&&j.served&&j.full,ContractMarket.mineFactor,ContractMarket.serviceFactor);int coin=j.type<9?1:0;income[coin]=EventJournal.saturate(income[coin],n);}
    }
    static boolean current(TaskData.Job j){return !j.terms.hasKey("End")&&!j.terms.getBoolean("Finished");}
    DashboardStats.Totals raw(Menus.Target t){return inventory.get(ClusterScheduler.serverId(t.item));}
    DashboardStats.Totals aggregate(UUID pool,UUID server){DashboardStats.Totals sum=new DashboardStats.Totals();Map<Tiles.Rack,Long> candidates=new IdentityHashMap<>();for(Menus.Target t:servers){UUID id=ClusterScheduler.serverId(t.item);if(server!=null&&!server.equals(id)||pool!=null&&!pool.equals(data.poolId(c.owner,id)))continue;DashboardStats.Totals s=inventory.get(id);sum.add(s);candidates.merge(t.rack,s.free[DashboardStats.NET],Long::sum);}sum.free[DashboardStats.NET]=DashboardStats.networkFree(controller?wan:0,heldNet,candidates,caps,heldByRack);return sum;}
    static void write(NBTTagCompound n,DashboardStats.Totals s){for(int i=0;i<DashboardStats.SIZE;i++){n.setLong("T"+i,s.total[i]);n.setLong("U"+i,s.used[i]);n.setLong("F"+i,s.free[i]);n.setLong("D"+i,s.unavailable(i));n.setLong("H"+i,s.offlineHeld[i]);}}
    void statistics(NBTTagCompound out,UUID pool){write(out,aggregate(pool,null));out.setInteger("Racks",racks);out.setInteger("Servers",servers.size());out.setInteger("Powered",powered);out.setInteger("PoolCount",pools.size());out.setInteger("Tasks",active+paused);out.setInteger("Active",active);out.setInteger("Paused",paused);out.setLong("PowerDraw",power);out.setLong("WAN",wan);out.setLong("Traffic",actualNet);out.setLong("NetworkReserved",heldNet);out.setLong("NetworkPlan",traffic);out.setLong("LAN",lan);}
    void finances(NBTTagCompound out){long[] balance=data.wallet(ClusterScheduler.key(c));for(int i=0;i<2;i++){out.setLong("Balance"+i,balance[i]);out.setLong("Hourly"+i,income[i]);}}
    Map<UUID,String> serverLabels(){Map<UUID,String> m=new HashMap<>();for(Menus.Target t:servers)m.put(ClusterScheduler.serverId(t.item),t.item.getDisplayName());return m;}
    Map<UUID,String> jobLabels(){Map<UUID,String> m=new HashMap<>();for(TaskData.Job j:jobs)if(current(j))m.put(j.terms.getUniqueId("Key"),"Task #"+j.id);return m;}
    NBTTagCompound event(EventJournal.Entry e,Map<UUID,String> ss,Map<UUID,String> js){return EventSystem.row(e,ss,js);}
    void recent(NBTTagCompound out){List<EventJournal.Entry> events=data.recentEvents(c,data.journal.limit());int errors=0,critical=0;NBTTagList recent=new NBTTagList();Map<UUID,String> ss=serverLabels(),js=jobLabels();for(EventJournal.Entry e:events){if(e.level==EventJournal.Level.ERROR)errors++;if(e.level==EventJournal.Level.CRITICAL)critical++;if(recent.tagCount()<5)recent.appendTag(event(e,ss,js));}out.setInteger("Errors",errors);out.setInteger("Critical",critical);out.setTag("Recent",recent);}
    List<NBTTagCompound> financialRows(){List<NBTTagCompound> out=new ArrayList<>();Map<UUID,String> ss=serverLabels(),js=jobLabels();Map<UUID,TaskData.Job> known=new HashMap<>();for(TaskData.Job j:jobs)known.put(j.terms.getUniqueId("Key"),j);for(EventJournal.Entry e:data.financialEvents(c)){NBTTagCompound row=event(e,ss,js);TaskData.Job j=known.get(e.source);if(j!=null&&e.target==EventJournal.Target.TASK){row.setString("Label","Task #"+j.id);row.setInteger("JobType",j.type);if(j.terms.hasKey("Company"))row.setString("Company",j.terms.getString("Company"));}out.add(row);}return out;}
}
