package odc;

import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import static odc.EventJournal.*;

/** Exclusive thermal state for a certified OfflineState footprint.
 * Pending records survive invalidation of the compute snapshot. */
final class PersistentThermal {
    static final class Server {
        UUID id,owner; BlockPos pos; int rack,node=-1,idleWatts,fault;
        int[] parts; ChassisSpec chassis; long broken;
        ThermalEngine.State state;boolean powered;
        Server(UUID id,UUID owner,BlockPos pos,int rack,ChassisSpec chassis,int[] parts){
            this(id,owner,pos,rack,chassis,parts,new ThermalEngine.State(ServerSpec.thermalProfile(chassis,parts),ODC.ambientTemperature));
        }
        Server(UUID id,UUID owner,BlockPos pos,int rack,ChassisSpec chassis,int[] parts,ThermalEngine.State state){
            this.id=id;this.owner=owner;this.pos=pos;this.rack=rack;this.chassis=chassis;this.parts=parts;this.state=state;
        }
        NBTTagCompound write(){
            NBTTagCompound n=new NBTTagCompound();n.setUniqueId("Id",id);n.setUniqueId("Owner",owner);n.setLong("Pos",pos.toLong());
            n.setInteger("Rack",rack);n.setInteger("Node",node);n.setInteger("Idle",idleWatts);n.setInteger("Fault",fault);n.setLong("Broken",broken);
            n.setString("Chassis",chassis.id);n.setIntArray("Parts",parts);n.setIntArray("Temps",state.snapshot());return n;
        }
        static Server read(NBTTagCompound n){
            if(!n.hasUniqueId("Id")||!n.hasUniqueId("Owner"))return null;
            ChassisSpec c=ChassisSpec.byId(n.getString("Chassis"));int[] parts=n.getIntArray("Parts"),temps=n.getIntArray("Temps");
            if(c==null||parts.length!=ServerSpec.COUNT||temps.length!=ServerSpec.COUNT+2)return null;
            for(int p:parts)if(p<0||p>=Part.models.size())return null;
            Server s=new Server(n.getUniqueId("Id"),n.getUniqueId("Owner"),BlockPos.fromLong(n.getLong("Pos")),n.getInteger("Rack"),c,parts);
            s.node=n.getInteger("Node");s.idleWatts=Math.max(0,n.getInteger("Idle"));s.fault=n.getInteger("Fault");
            if(s.fault!=0&&s.fault!=ComputeEngine.OVERHEAT&&s.fault!=ComputeEngine.HARDWARE_FAILURE)return null;
            for(int i=0;i<parts.length;i++)if(Part.at(parts[i]).electronic()&&(n.getLong("Broken")&(1L<<i))!=0)s.broken|=1L<<i;
            s.state.restore(temps,ODC.ambientTemperature);return s;
        }
    }
    static final class Cooler {
        final BlockPos pos;final int tier,grid;final boolean reserved;
        Cooler(BlockPos p,int t,int g,boolean reserved){pos=p;tier=t;grid=g;this.reserved=reserved;}
    }
    static final class Contour {
        final Set<BlockPos> cells;final int[] racks;final List<Cooler> coolers=new ArrayList<>();final LiquidCore.Loop loop;LiquidTemperatures.Sample saved;
        Contour(Set<BlockPos> c,int[] r,int endpoints,double temp){cells=c;racks=r;loop=new LiquidCore.Loop(endpoints,temp);}
    }
    final List<Server> servers=new ArrayList<>();
    final List<Contour> contours=new ArrayList<>();
    private LiquidCore.Loop[] byRack;
    long lastTick=Long.MIN_VALUE;

    // Only whole contours whose rack endpoints belong to the captured LAN can
    // be certified for virtual service. A shared contour stays live-only.
    static List<LiquidNetwork.Contour> exclusive(World w,List<Tiles.Rack> racks){
        Set<Tiles.Rack> own=Collections.newSetFromMap(new IdentityHashMap<Tiles.Rack,Boolean>());own.addAll(racks);
        Set<LiquidNetwork.Contour> seen=Collections.newSetFromMap(new IdentityHashMap<LiquidNetwork.Contour,Boolean>());
        List<LiquidNetwork.Contour> out=new ArrayList<>();
        for(Tiles.Rack rack:racks){LiquidNetwork.Contour c=LiquidNetwork.contour(w,rack.getPos());
            if(c!=null&&seen.add(c)&&own.containsAll(c.racks))out.add(c);}
        return out;
    }
    void capture(OfflineState.Snapshot s,OfflineState.Snapshot previous,List<Tiles.Rack> racks,List<ClusterScheduler.Ref> refs,List<TaskEngine.Node> nodes,Set<Integer> excluded,List<PowerGrid.Domain> grids,List<LiquidNetwork.Contour> certified){
        Map<UUID,Server> prior=new HashMap<>();if(previous!=null)for(Server record:previous.thermal.servers)prior.put(record.id,record);
        Map<UUID,Integer> indices=new HashMap<>();for(int i=0;i<s.nodes.size();i++)indices.put(s.nodes.get(i).id,i);
        Map<Tiles.Rack,Integer> rackIndex=new IdentityHashMap<>();
        for(int ri=0;ri<racks.size();ri++){
            Tiles.Rack rack=racks.get(ri);rackIndex.put(rack,ri);
            for(int b=0;b<rack.capacity();b++)if(rack.isServer(b)){
                ItemStack item=rack.slots.get(b);ThermalRuntime.Entry e=ThermalRuntime.entry(rack,b);
                int[] parts=e.parts;boolean valid=true;for(int p:parts)if(p<0){valid=false;break;}if(!valid)continue;
                UUID id=ClusterScheduler.serverId(item);Server record=prior.get(id);
                if(record==null||record.state!=e.state||!record.pos.equals(rack.getPos())||record.rack!=ri)record=new Server(id,s.owner,rack.getPos(),ri,e.chassis,parts,e.state);
                record.broken=e.broken;record.fault=HardwareDamage.fault(item);record.idleWatts=0;
                record.node=indices.getOrDefault(record.id,-1);
                if(record.node<0&&record.fault==0&&record.broken==0&&(rack.poweredMask&(1<<b))!=0){
                    record.idleWatts=Math.max(0,rack.powerUsed[b]);
                    int g=s.gridByRack[ri];if(g>=0){
                        OfflineState.Group group=s.groups.get(g);
                        // Idle servers were previously part of fixed demand.
                        // Reserve them separately so a thermal trip removes their load.
                        long subtract=Math.min(group.fixed,record.idleWatts);
                        group.fixed-=subtract;
                        long cat=Math.min(group.fixedCategories[PowerStats.SERVER],subtract);
                        group.fixedCategories[PowerStats.SERVER]-=cat;
                        long rest=subtract-cat;
                        for(int k=0;k<PowerStats.SIZE&&rest>0;k++){long n=Math.min(group.fixedCategories[k],rest);group.fixedCategories[k]-=n;rest-=n;}
                    }
                }
                servers.add(record);
            }
        }
        for(LiquidNetwork.Contour c:certified){
            int[] rr=new int[c.racks.size()];for(int i=0;i<rr.length;i++)rr[i]=rackIndex.get(c.racks.get(i));
            Contour loop=new Contour(c.cells,rr,c.racks.size()+c.chillers.size(),c.loop.temp);
            loop.saved=c.saved;
            for(Tiles.Chiller cooler:c.chillers){int g=grids.indexOf(cooler.energy.domain());loop.coolers.add(new Cooler(cooler.getPos(),cooler.tier(),g,cooler.online));}
            contours.add(loop);
        }
        index(s.caps.length);
    }
    private void index(int count){
        byRack=new LiquidCore.Loop[count];for(Contour c:contours)for(int r:c.racks)byRack[r]=c.loop;
    }
    void addIdleDemand(OfflineState.Snapshot s,OfflineState offline,long[] demand){
        for(Server server:servers)if(server.node<0&&server.idleWatts>0&&server.fault==0&&server.broken==0&&!offline.faults.containsKey(server.id)){
            int g=s.gridByRack[server.rack];if(g>=0)demand[g]=PersistentPower.demand(demand[g],server.idleWatts);
        }
    }
    static boolean funded(PowerGrid.Domain[] domains,int g){return g>=0&&g<domains.length&&domains[g]!=null&&domains[g].funded();}
    private static long allocate(long[] prefix,PowerGrid.Domain[] domains,int g,int watts){
        if(!funded(domains,g)||watts<=0)return 0;
        int factor=domains[g].budget.factor;long old=prefix[g];prefix[g]=PowerEngine.sum(old,watts);
        return PowerEngine.scale(prefix[g],factor)-PowerEngine.scale(old,factor);
    }
    void step(World w,TaskData data,OfflineState offline,OfflineState.Snapshot s,TaskEngine.Plan plan,Set<Integer> excluded,PowerGrid.Domain[] domains,boolean passive,boolean onlyUnloaded){
        long tick=w.getTotalWorldTime();if(lastTick==tick)return;lastTick=tick;
        boolean bypass=!ODC.requireEnergy;
        long[] prefix=new long[domains.length];for(int g=0;g<prefix.length;g++)prefix[g]=s.groups.get(g).fixed;
        // Remove captured chiller nominal watts from fixed prefix, then add
        // them in deterministic order. Total stays exactly the reserved demand.
        for(Contour c:contours)for(Cooler ch:c.coolers)if(ch.reserved&&ch.grid>=0&&ch.grid<prefix.length)prefix[ch.grid]=Math.max(0,prefix[ch.grid]-LiquidCore.electric(ch.tier));
        for(Contour c:contours){
            double cap=0;
            if(!passive)for(Cooler ch:c.coolers)if(ch.reserved){
                long ac=bypass?LiquidCore.electric(ch.tier):allocate(prefix,domains,ch.grid,LiquidCore.electric(ch.tier));
                cap+=LiquidCore.available(ch.tier,ac,bypass||funded(domains,ch.grid));
            }
            c.loop.begin(cap);
        }
        // Each virtual node is processed once, using the same plan DC array.
        // Unallocated idle servers have cached DC computed only on demand.
        for(Server server:servers){
            if(onlyUnloaded&&w.isBlockLoaded(server.pos)){syncLoaded(w,server);continue;}
            int g=s.gridByRack[server.rack],nominal=0;int[] dc=null;
            if(!passive&&server.fault==0&&server.broken==0&&!offline.faults.containsKey(server.id)){
                if(server.node>=0&&plan!=null&&!excluded.contains(server.node)){nominal=plan.power[server.node];dc=plan.dcWatts[server.node];}
                else if(server.node<0&&server.idleWatts>0){nominal=server.idleWatts;dc=idle(server);}
            }
            long ac=nominal<=0?0:bypass?nominal:allocate(prefix,domains,g,nominal);
            server.powered=!passive&&nominal>0&&(bypass||funded(domains,g));
            server.state.prepare(dc,nominal,ac,ODC.ambientTemperature);
            LiquidCore.Loop loop=byRack[server.rack];if(loop!=null&&loop.capacity>0)loop.request(server.state.liquidDemand(loop.temp));
        }
        for(Contour c:contours)c.loop.allocate();
        for(Server server:servers){
            if(onlyUnloaded&&w.isBlockLoaded(server.pos))continue;
            server.state.finish(ODC.ambientTemperature,byRack[server.rack]);
            ThermalProtection.Accident accident=ThermalProtection.evaluate(server.state,server.powered,server.fault!=0,server.broken,ODC.thermalFailurePercent,w.rand::nextInt);
            if(accident!=null){
                server.broken|=accident.damage;server.fault=accident.reason();offline.faults.put(server.id,server.fault);
                if(server.node>=0)excluded.add(server.node);
                data.serverStopped(server.id,false);
                ThermalProtection.Limit hit=accident.limit;
                data.record(s.owner,s.anchor,server.id,Level.CRITICAL,Category.COOLING,Target.SERVER,"thermal_shutdown",String.format(Locale.ROOT,"%s (slot %d): %.1f / %d C; damaged: %d (virtual)",server.state.profile.part(hit.slot).name,hit.slot,hit.actual,hit.critical,Long.bitCount(accident.damage)),-1,0);
                for(int slot=0;slot<ServerSpec.COUNT;slot++)if((accident.damage&(1L<<slot))!=0){
                    Part part=server.state.profile.part(slot);
                    data.record(s.owner,s.anchor,server.id,Level.ERROR,Category.HARDWARE,Target.SERVER,"thermal_damage",String.format(Locale.ROOT,"%s (slot %d) broken; %.1f / %d C (virtual)",part.name,slot,ThermalProtection.temperature(server.state,slot),part.criticalC),-1,0);
                }
                offline.markDirty();
            }
            offline.pendingThermal.put(server.id,server);
        }
        Set<LiquidCore.Loop> liveLoops=Collections.newSetFromMap(new IdentityHashMap<LiquidCore.Loop,Boolean>());
        if(onlyUnloaded&&!(offline.active(data,s)&&OfflineState.mode(w,s)==PersistentPower.Mode.PARTIAL))for(Server server:servers)if(w.isBlockLoaded(server.pos)&&byRack[server.rack]!=null)liveLoops.add(byRack[server.rack]);
        for(Contour c:contours){
            // A loaded, unpaused rack owns the live loop; do not overwrite
            // its checkpoint from a passive virtual fragment.
            if(liveLoops.contains(c.loop))continue;
            c.loop.finish(ODC.ambientTemperature);
            if(c.saved==null)c.saved=LiquidTemperatures.get(w).sample(c.cells,ODC.ambientTemperature);
            c.saved.record(c.loop.temp,tick);
        }
        if(tick%400==0)offline.markDirty();
    }
    private static void syncLoaded(World w,Server record){
        TileEntity tile=w.getTileEntity(record.pos);if(!(tile instanceof Tiles.Rack))return;Tiles.Rack rack=(Tiles.Rack)tile;
        for(int b=0;b<rack.capacity();b++)if(rack.isServer(b)){
            ThermalRuntime.Entry e=rack.thermal[b];if(e!=null&&record.id.equals(ClusterScheduler.serverId(rack.slots.get(b)))&&e.chassis==record.chassis&&Arrays.equals(e.parts,record.parts)){record.state=e.state;record.broken=e.broken;record.fault=HardwareDamage.fault(e.item);return;}
        }
    }
    private final Map<UUID,int[]> idleCache=new HashMap<>();
    private int[] idle(Server s){
        int[] dc=idleCache.get(s.id);if(dc==null){dc=new int[ServerSpec.COUNT];new TaskEngine.Node(s.id,s.rack,0,s.parts,null,s.chassis).watts(new BitSet(),0,dc);idleCache.put(s.id,dc);}return dc;
    }
    /** Apply pending metadata before planning or extracting an item.
     * Validate physical location, owner, UUID and model per slot. */
    static void reconcile(OfflineState offline,Tiles.Rack rack){
        if(rack.role==-1||offline.pendingThermal.isEmpty())return;
        for(int b=0;b<rack.capacity();b++)if(rack.isServer(b)){
            ItemStack item=rack.slots.get(b);UUID id=ClusterScheduler.serverId(item);
            Server s=offline.pendingThermal.get(id);if(s==null||!s.pos.equals(rack.getPos())||!Objects.equals(s.owner,rack.owner))continue;
            NonNullList<ItemStack> parts=Hardware.parts(item);int[] actual=Hardware.tiers(parts);
            if(Hardware.chassis(item)!=s.chassis||!Arrays.equals(actual,s.parts)){
                // Never apply damage to a different physical configuration.
                // Keep pending record, block start and require manual recovery.
                rack.enabledMask&=~(1<<b);rack.fault[b]=rack.status[b]=ComputeEngine.HARDWARE_FAILURE;HardwareDamage.latch(item,ComputeEngine.HARDWARE_FAILURE);
                rack.markDirty();continue;
            }
            boolean changed=false;
            for(int i=0;i<parts.size();i++)if((s.broken&(1L<<i))!=0)changed|=HardwareDamage.mark(parts.get(i));
            if(changed)Hardware.writeParts(item,parts);
            if(s.fault!=0){HardwareDamage.latch(item,s.fault);rack.enabledMask&=~(1<<b);rack.fault[b]=rack.status[b]=s.fault;offline.faults.remove(id);}
            HardwareDamage.checkpoint(item,s.state);rack.thermal[b]=null;
            offline.pendingThermal.remove(id);offline.markDirty();rack.changed();
        }
    }
    NBTTagCompound write(){
        NBTTagCompound n=new NBTTagCompound();NBTTagList ss=new NBTTagList(),cc=new NBTTagList();
        for(Server s:servers)ss.appendTag(s.write());
        for(Contour c:contours){
            NBTTagCompound x=new NBTTagCompound();x.setIntArray("Racks",c.racks);x.setDouble("Temp",c.loop.temp);x.setTag("Cells",OfflineState.writePositions(c.cells));
            NBTTagList cl=new NBTTagList();for(Cooler ch:c.coolers){NBTTagCompound a=new NBTTagCompound();a.setLong("Pos",ch.pos.toLong());a.setInteger("Tier",ch.tier);a.setInteger("Grid",ch.grid);a.setBoolean("Reserved",ch.reserved);cl.appendTag(a);}x.setTag("Coolers",cl);cc.appendTag(x);
        }
        n.setTag("Servers",ss);n.setTag("Contours",cc);return n;
    }
    static PersistentThermal read(NBTTagCompound n,OfflineState.Snapshot s){
        PersistentThermal result=new PersistentThermal();Set<UUID> ids=new HashSet<>();Set<Integer> nodes=new HashSet<>(),racks=new HashSet<>();Set<BlockPos> claimed=new HashSet<>();
        NBTTagList ss=n.getTagList("Servers",10);
        for(int i=0;i<ss.tagCount();i++){
            Server a=Server.read(ss.getCompoundTagAt(i));
            if(a==null||!ids.add(a.id)||!s.owner.equals(a.owner)||!s.consumers.contains(a.pos)||a.rack<0||a.rack>=s.caps.length||a.node< -1||a.node>=s.nodes.size())return null;
            if(a.node>=0){TaskEngine.Node node=s.nodes.get(a.node);if(!nodes.add(a.node)||!node.id.equals(a.id)||node.rack!=a.rack||node.chassis!=a.chassis||!Arrays.equals(node.parts,a.parts))return null;}
            result.servers.add(a);
        }
        if(nodes.size()!=s.nodes.size())return null;
        NBTTagList cc=n.getTagList("Contours",10);
        for(int i=0;i<cc.tagCount();i++){
            NBTTagCompound x=cc.getCompoundTagAt(i);int[] rr=x.getIntArray("Racks");Set<BlockPos> cells=OfflineState.readPositions(x.getTagList("Cells",10));
            if(cells.isEmpty()||!s.positions.containsAll(cells)||!Collections.disjoint(cells,claimed))return null;claimed.addAll(cells);
            for(int ri:rr)if(ri<0||ri>=s.caps.length||!racks.add(ri))return null;
            NBTTagList cl=x.getTagList("Coolers",10);Contour c=new Contour(cells,rr,rr.length+cl.tagCount(),x.getDouble("Temp"));Set<BlockPos> unique=new HashSet<>();
            for(int k=0;k<cl.tagCount();k++){NBTTagCompound a=cl.getCompoundTagAt(k);BlockPos p=BlockPos.fromLong(a.getLong("Pos"));int t=a.getInteger("Tier"),g=a.getInteger("Grid");
                if(!unique.add(p)||!cells.contains(p)||!s.consumers.contains(p)||t<1||t>3||g< -1||g>=s.groups.size()||g>=0&&!s.groups.get(g).cells.contains(p))return null;
                c.coolers.add(new Cooler(p,t,g,a.getBoolean("Reserved")));
            }
            result.contours.add(c);
        }
        result.index(s.caps.length);return result;
    }
}
