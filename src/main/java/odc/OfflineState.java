package odc;
import static odc.EventJournal.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Conservative virtual execution: never simulates a snapshot if any of its topology is loaded.
 * A snapshot contains the actual last verified, exclusively supplied creative-energy domain.
 * Partial loading pauses the virtual copy; live devices remain authoritative. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class OfflineState extends WorldSavedData {
    static final String KEY="odc_virtual_domains_v053";
    static final class Snapshot {UUID anchor,owner;Set<BlockPos> positions=new HashSet<>();List<TaskEngine.Node> nodes=new ArrayList<>();int[] caps,budget,gridByRack,gridBudget;List<Set<BlockPos>> grids=new ArrayList<>();Set<Integer> controllerGrids=new HashSet<>();int wan;Set<PhysicalTopology.Pos> routing=new HashSet<>();}
    final Map<UUID,Snapshot> snapshots=new HashMap<>();final Map<UUID,Integer> faults=new HashMap<>();
    public OfflineState(){super(KEY);}public OfflineState(String n){super(n);}
    static OfflineState get(World w){net.minecraft.world.storage.MapStorage s=w.getPerWorldStorage();OfflineState d=(OfflineState)s.getOrLoadData(OfflineState.class,KEY);if(d==null){d=new OfflineState();s.setData(KEY,d);}return d;}
    static boolean loaded(World w,Snapshot s){for(BlockPos p:s.positions)if(w.isBlockLoaded(p))return true;return false;}
    static boolean allLoaded(World w,Snapshot s){for(BlockPos p:s.positions)if(!w.isBlockLoaded(p))return false;return true;}
    static void invalidateAt(World w,BlockPos p){if(w==null||w.isRemote)return;OfflineState o=get(w);boolean dirty=o.snapshots.values().removeIf(s->{if(s.positions.contains(p))return true;for(net.minecraft.util.EnumFacing f:net.minecraft.util.EnumFacing.VALUES)if(s.positions.contains(p.offset(f)))return true;return false;});if(dirty)o.markDirty();}
    @SubscribeEvent public static void placed(BlockEvent.PlaceEvent e){Network.invalidate(e.getWorld());invalidateAt(e.getWorld(),e.getPos());}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){Network.invalidate(e.getWorld());invalidateAt(e.getWorld(),e.getPos());}
    Set<PhysicalTopology.Pos> virtualCells(World w){Set<PhysicalTopology.Pos> out=new HashSet<>();for(Snapshot s:snapshots.values())if(!s.routing.isEmpty()&&!loaded(w,s))out.addAll(s.routing);return out;}
    void capture(World w,UUID anchor,UUID owner,Set<BlockPos> positions,List<Tiles.Owned> devices,List<Tiles.Rack> racks,List<TaskEngine.Node> nodes,List<ClusterScheduler.Ref> refs,Set<Integer> excluded,int[] caps,int wan){if(anchor==null)return;
        Snapshot previous=snapshots.get(anchor);if(previous!=null&&!allLoaded(w,previous))return; // never overwrite a known larger domain with a fragment
        Snapshot s=new Snapshot();s.anchor=anchor;s.owner=owner;s.positions.addAll(positions);s.caps=caps.clone();s.budget=new int[racks.size()];s.gridByRack=new int[racks.size()];Arrays.fill(s.gridByRack,-1);s.wan=0;
        Set<TileEntity> own=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());own.addAll(devices);
        Tiles.Computer root=null;for(Tiles.Owned t:devices)if(t instanceof Tiles.Computer){root=(Tiles.Computer)t;break;}
        if(root!=null){Set<PhysicalTopology.Pos> footprint=PhysicalNetwork.exclusiveFootprint(w,root.getPos());boolean certified=!footprint.isEmpty();for(PhysicalTopology.Pos p:footprint){BlockPos q=PhysicalNetwork.block(p);TileEntity tile=w.getTileEntity(q);if(tile instanceof Tiles.Station){if(!Objects.equals(((Tiles.Station)tile).owner,owner)){certified=false;break;}own.add(tile);}else if(tile instanceof Tiles.Owned&&Network.isLanDevice(tile)&&!own.contains(tile)){if(tile instanceof Tiles.Rack&&((Tiles.Rack)tile).role==-1)continue;certified=false;break;}}
            if(certified){s.routing.addAll(footprint);for(PhysicalTopology.Pos p:footprint)s.positions.add(PhysicalNetwork.block(p));}else{own.clear();own.addAll(devices);}}
        Set<TileEntity> powered=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());List<PowerGrid.Domain> verified=new ArrayList<>();
        if(ODC.requireEnergy){
            Set<PowerGrid.Domain> grids=Collections.newSetFromMap(new IdentityHashMap<PowerGrid.Domain,Boolean>());
            for(TileEntity tile:own){
                PowerGrid.Domain grid=PowerGrid.domain(w,tile.getPos());
                if(grid!=null&&grid.state==GridProtection.ON&&grid.budget.capacityWatts>0)grids.add(grid);
            }
            for(PowerGrid.Domain grid:grids){
                // Stage 1 virtual simulation only certifies exclusive, creative-powered grids.
                for(TileEntity consumer:grid.consumers)if(!own.contains(consumer)){snapshots.remove(anchor);markDirty();return;}
                for(PowerGrid.Source source:grid.sources)if(!(source instanceof Tiles.Battery)){snapshots.remove(anchor);markDirty();return;}
                if(!grid.complete){snapshots.remove(anchor);markDirty();return;}
                verified.add(grid);s.grids.add(new HashSet<>(grid.positions));s.positions.addAll(grid.positions);powered.addAll(grid.consumers);
            }
            for(TileEntity tile:own)if(tile instanceof Tiles.Powered&&((Tiles.Powered)tile).online&&!powered.contains(tile)){snapshots.remove(anchor);markDirty();return;}
        }
        for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){ClusterScheduler.Ref f=refs.get(i);if(ODC.requireEnergy&&!powered.contains(f.rack))continue;s.nodes.add(nodes.get(i));s.budget[nodes.get(i).rack]+=f.rack.powerUsed[f.bay];}
        s.gridBudget=new int[verified.size()];
        for(int g=0;g<verified.size();g++){
            PowerGrid.Domain grid=verified.get(g);long planned=0;
            for(int ri=0;ri<racks.size();ri++)if(racks.get(ri).energy.domain()==grid){s.gridByRack[ri]=g;planned+=s.budget[ri];}
            long fixed=Math.max(0,grid.budget.usedUnits()/PowerEngine.TICKS_PER_SECOND-planned);
            s.gridBudget[g]=(int)Math.min(Integer.MAX_VALUE,Math.max(0,grid.budget.capacityWatts-fixed));
            for(TileEntity consumer:grid.consumers)if(consumer instanceof Tiles.Computer&&((Tiles.Computer)consumer).operational())s.controllerGrids.add(g);
        }
        if(!ODC.requireEnergy)Arrays.fill(s.budget,Integer.MAX_VALUE);
        if(s.nodes.isEmpty()){snapshots.remove(anchor);markDirty();return;}snapshots.put(anchor,s);markDirty();
    }
    void tick(World w,TaskData d){BandwidthEngine.Result flow=PhysicalNetwork.bandwidth(w);Set<UUID> seen=new HashSet<>();for(Snapshot s:new ArrayList<>(snapshots.values())){if(loaded(w,s)||!seen.add(s.anchor))continue;List<TaskData.Job> jobs=new ArrayList<>();for(TaskData.Job j:d.jobs)if(s.anchor.equals(j.anchor)&&s.owner.equals(j.owner))jobs.add(j);if(jobs.isEmpty())continue;
        Set<Integer> excluded=new HashSet<>();for(int i=0;i<s.nodes.size();i++)if(faults.containsKey(s.nodes.get(i).id))excluded.add(i);Set<Integer> blocked=new HashSet<>();for(TaskData.Job j:jobs)if(j.enabled){boolean ok=NetworkAdmission.amounts(j.bindings).isEmpty()||!s.routing.isEmpty()&&NetworkAdmission.served(flow,j.terms.getUniqueId("Key"),j.bindings);if(!ok)blocked.add(j.id);d.routeState(j,ok);}TaskEngine.Plan p;while(true){p=d.plan(s.anchor,s.nodes,jobs,s.caps,Integer.MAX_VALUE,excluded,blocked);boolean retry=false;for(int i:p.overload)if(excluded.add(i)){s.routing.clear();faults.put(s.nodes.get(i).id,11);d.record(s.owner,s.anchor,s.nodes.get(i).id,Level.CRITICAL,Category.POWER,Target.SERVER,"psu_overload","PSU overload (virtual simulation)",-1,0);retry=true;}if(retry)continue;if(ODC.requireEnergy){
            long[] demand=new long[s.grids.size()];for(int i=0;i<s.nodes.size();i++)if(!excluded.contains(i)){int g=s.gridByRack[s.nodes.get(i).rack];if(g>=0)demand[g]+=p.power[i];}
            for(int g=0;g<demand.length;g++)if(PowerState.get(w).state(s.grids.get(g))!=GridProtection.ON||!GridProtection.sufficient(s.gridBudget[g],demand[g])){
                int state=PowerState.get(w).state(s.grids.get(g));
                if(state==GridProtection.ON)PowerState.get(w).set(s.grids.get(g),GridProtection.TRIPPED);
                s.routing.clear();for(TaskData.Job j:jobs)if(!NetworkAdmission.amounts(j.bindings).isEmpty()&&blocked.add(j.id))retry=true;
                for(int i=0;i<s.nodes.size();i++)if(s.gridByRack[s.nodes.get(i).rack]==g&&excluded.add(i)){
                    UUID id=s.nodes.get(i).id;faults.put(id,6);d.serverStopped(id,false);
                    d.record(s.owner,s.anchor,id,Level.CRITICAL,Category.POWER,Target.SERVER,"grid_overload","Electrical grid tripped (virtual simulation)",-1,0);retry=true;
                }
            }
        }if(!retry)break;}
        if(ODC.requireEnergy&&!s.controllerGrids.stream().anyMatch(g->PowerState.get(w).state(s.grids.get(g))==GridProtection.ON)){s.routing.clear();markDirty();continue;}
        for(TaskData.Job j:jobs){TaskEngine.Result r=p.tasks.get(j.id);if(r==null)continue;if(j.enabled)d.resourceState(j,r.work<=0||!r.full);j.work=r.work;j.usedCPU=r.cpu;j.usedGPU=r.gpus;j.usedRAM=r.ram;j.usedDisk=r.disk;j.usedNet=r.net;j.full=r.full;d.account(j,r);}markDirty();}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){NBTTagList ss=new NBTTagList();for(Snapshot s:snapshots.values()){NBTTagCompound a=new NBTTagCompound();a.setUniqueId("Anchor",s.anchor);a.setUniqueId("Owner",s.owner);a.setIntArray("Caps",s.caps);a.setIntArray("Budget",s.budget);a.setIntArray("GridByRack",s.gridByRack);a.setIntArray("GridBudget",s.gridBudget);NBTTagList groups=new NBTTagList();for(int g=0;g<s.grids.size();g++){NBTTagCompound z=new NBTTagCompound();z.setBoolean("Controller",s.controllerGrids.contains(g));NBTTagList cells=new NBTTagList();for(BlockPos p:s.grids.get(g)){NBTTagCompound q=new NBTTagCompound();q.setLong("P",p.toLong());cells.appendTag(q);}z.setTag("Cells",cells);groups.appendTag(z);}a.setTag("PowerGroups",groups);a.setInteger("WAN",s.wan);NBTTagList pp=new NBTTagList();for(BlockPos p:s.positions){NBTTagCompound q=new NBTTagCompound();q.setLong("P",p.toLong());pp.appendTag(q);}a.setTag("Positions",pp);NBTTagList route=new NBTTagList();for(PhysicalTopology.Pos p:s.routing){NBTTagCompound q=new NBTTagCompound();q.setLong("P",PhysicalNetwork.block(p).toLong());route.appendTag(q);}a.setTag("Routing",route);NBTTagList nn=new NBTTagList();for(TaskEngine.Node node:s.nodes){NBTTagCompound q=new NBTTagCompound();q.setUniqueId("Id",node.id);if(node.pool!=null)q.setUniqueId("Pool",node.pool);q.setInteger("Rack",node.rack);q.setInteger("Net",node.net);q.setIntArray("Parts",node.parts);nn.appendTag(q);}a.setTag("Nodes",nn);ss.appendTag(a);}n.setInteger("PowerSchema",3);n.setTag("Snapshots",ss);NBTTagList fs=new NBTTagList();for(Map.Entry<UUID,Integer> f:faults.entrySet()){NBTTagCompound a=new NBTTagCompound();a.setUniqueId("Id",f.getKey());a.setInteger("Reason",f.getValue());fs.appendTag(a);}n.setTag("Faults",fs);return n;}
    @Override public void readFromNBT(NBTTagCompound n){snapshots.clear();NBTTagList ss=n.getInteger("PowerSchema")==3?n.getTagList("Snapshots",10):new NBTTagList();for(int i=0;i<ss.tagCount();i++){NBTTagCompound a=ss.getCompoundTagAt(i);if(!a.hasUniqueId("Anchor")||!a.hasUniqueId("Owner"))continue;Snapshot s=new Snapshot();s.anchor=a.getUniqueId("Anchor");s.owner=a.getUniqueId("Owner");s.caps=a.getIntArray("Caps");s.budget=a.getIntArray("Budget");s.gridByRack=a.getIntArray("GridByRack");s.gridBudget=a.getIntArray("GridBudget");NBTTagList groups=a.getTagList("PowerGroups",10);for(int g=0;g<groups.tagCount();g++){NBTTagCompound z=groups.getCompoundTagAt(g);Set<BlockPos> cells=new HashSet<>();NBTTagList cl=z.getTagList("Cells",10);for(int k=0;k<cl.tagCount();k++)cells.add(BlockPos.fromLong(cl.getCompoundTagAt(k).getLong("P")));s.grids.add(cells);if(z.getBoolean("Controller"))s.controllerGrids.add(g);}if(s.gridByRack.length!=s.caps.length||s.gridBudget.length!=s.grids.size())continue;s.wan=0;if(s.caps.length!=s.budget.length)continue;NBTTagList pp=a.getTagList("Positions",10);for(int j=0;j<pp.tagCount();j++)s.positions.add(BlockPos.fromLong(pp.getCompoundTagAt(j).getLong("P")));NBTTagList route=a.getTagList("Routing",10);for(int j=0;j<route.tagCount();j++){BlockPos p=BlockPos.fromLong(route.getCompoundTagAt(j).getLong("P"));if(s.positions.contains(p))s.routing.add(PhysicalNetwork.pos(p));}NBTTagList nn=a.getTagList("Nodes",10);for(int j=0;j<nn.tagCount();j++){NBTTagCompound q=nn.getCompoundTagAt(j);int rack=q.getInteger("Rack");int[] parts=q.getIntArray("Parts");if(parts.length==ServerSpec.COUNT && q.hasUniqueId("Id") && rack>=0 && rack<s.caps.length && (!ODC.requireEnergy||s.gridByRack[rack]>=0&&s.gridByRack[rack]<s.grids.size()))s.nodes.add(new TaskEngine.Node(q.getUniqueId("Id"),rack,q.getInteger("Net"),parts,q.hasUniqueId("Pool")?q.getUniqueId("Pool"):PoolResources.id(s.owner,PoolResources.DEFAULT)));}if(!s.positions.isEmpty()&&!s.nodes.isEmpty())snapshots.put(s.anchor,s);}faults.clear();NBTTagList fs=n.getTagList("Faults",10);for(int i=0;i<fs.tagCount();i++){NBTTagCompound a=fs.getCompoundTagAt(i);if(a.hasUniqueId("Id"))faults.put(a.getUniqueId("Id"),a.getInteger("Reason"));}}
}
