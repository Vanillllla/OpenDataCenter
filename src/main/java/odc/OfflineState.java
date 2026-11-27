package odc;
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
    static final String KEY="odc_virtual_domains_v043";
    static final class Snapshot {UUID anchor,owner;Set<BlockPos> positions=new HashSet<>();List<TaskEngine.Node> nodes=new ArrayList<>();int[] caps,budget;int wan;}
    final Map<UUID,Snapshot> snapshots=new HashMap<>();final Map<UUID,Integer> faults=new HashMap<>();
    public OfflineState(){super(KEY);}public OfflineState(String n){super(n);}
    static OfflineState get(World w){net.minecraft.world.storage.MapStorage s=w.getPerWorldStorage();OfflineState d=(OfflineState)s.getOrLoadData(OfflineState.class,KEY);if(d==null){d=new OfflineState();s.setData(KEY,d);}return d;}
    static boolean loaded(World w,Snapshot s){for(BlockPos p:s.positions)if(w.isBlockLoaded(p))return true;return false;}
    static boolean allLoaded(World w,Snapshot s){for(BlockPos p:s.positions)if(!w.isBlockLoaded(p))return false;return true;}
    static void invalidateAt(World w,BlockPos p){if(w==null||w.isRemote)return;OfflineState o=get(w);boolean dirty=o.snapshots.values().removeIf(s->{if(s.positions.contains(p))return true;for(net.minecraft.util.EnumFacing f:net.minecraft.util.EnumFacing.VALUES)if(s.positions.contains(p.offset(f)))return true;return false;});if(dirty)o.markDirty();}
    @SubscribeEvent public static void placed(BlockEvent.PlaceEvent e){Network.invalidate(e.getWorld());invalidateAt(e.getWorld(),e.getPos());}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){Network.invalidate(e.getWorld());invalidateAt(e.getWorld(),e.getPos());}
    void capture(World w,UUID anchor,UUID owner,Set<BlockPos> positions,List<Tiles.Owned> devices,List<Tiles.Rack> racks,List<TaskEngine.Node> nodes,List<ClusterScheduler.Ref> refs,Set<Integer> excluded,int[] caps,int wan){if(anchor==null)return;
        Snapshot previous=snapshots.get(anchor);if(previous!=null&&!allLoaded(w,previous))return; // never overwrite a known larger domain with a fragment
        Snapshot s=new Snapshot();s.anchor=anchor;s.owner=owner;s.positions.addAll(positions);s.caps=caps.clone();s.budget=new int[racks.size()];s.wan=wan;
        Set<TileEntity> own=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());own.addAll(devices);Set<TileEntity> powered=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());
        if(ODC.requireEnergy){for(TileEntity tile:new ArrayList<>(w.loadedTileEntityList))if(tile instanceof Tiles.Battery&&Network.live(w,tile)){
            Set<BlockPos> trace=new HashSet<>();List<Network.Endpoint> ends=Network.power(w,tile.getPos(),trace);boolean touches=false,foreign=false;long demand=0;
            for(Network.Endpoint end:ends)if(end.energy instanceof Tiles.Buffer){TileEntity t=((Tiles.Buffer)end.energy).tile;if(own.contains(t))touches=true;else foreign=true;if(t instanceof Tiles.Rack)demand+=((Tiles.Rack)t).lastPower;else if(t instanceof Tiles.Powered)demand+=((Tiles.Powered)t).idlePower();}else foreign=true;
            if(!touches)continue;if(foreign||demand>ODC.batteryOutput){snapshots.remove(anchor);markDirty();return;}s.positions.addAll(trace);for(Network.Endpoint e:ends)if(e.energy instanceof Tiles.Buffer)powered.add(((Tiles.Buffer)e.energy).tile);}
            for(Tiles.Owned t:devices)if(t instanceof Tiles.Powered&&((Tiles.Powered)t).online&&!powered.contains(t)){snapshots.remove(anchor);markDirty();return;}}
        for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){ClusterScheduler.Ref f=refs.get(i);if(ODC.requireEnergy&&!powered.contains(f.rack))continue;s.nodes.add(nodes.get(i));s.budget[nodes.get(i).rack]+=f.rack.powerUsed[f.bay];}
        if(!ODC.requireEnergy)Arrays.fill(s.budget,Integer.MAX_VALUE);
        if(s.nodes.isEmpty()||wan<=0){snapshots.remove(anchor);markDirty();return;}snapshots.put(anchor,s);markDirty();
    }
    void tick(World w,TaskData d){Set<UUID> seen=new HashSet<>();for(Snapshot s:new ArrayList<>(snapshots.values())){if(loaded(w,s)||!seen.add(s.anchor))continue;List<TaskData.Job> jobs=new ArrayList<>();for(TaskData.Job j:d.jobs)if(s.anchor.equals(j.anchor)&&s.owner.equals(j.owner))jobs.add(j);if(jobs.isEmpty())continue;
        Set<Integer> excluded=new HashSet<>();for(int i=0;i<s.nodes.size();i++)if(faults.containsKey(s.nodes.get(i).id))excluded.add(i);TaskEngine.Plan p;while(true){p=TaskEngine.plan(s.nodes,new ArrayList<TaskEngine.Task>(jobs),s.caps,s.wan,d.groups,excluded);boolean retry=false;for(int i:p.overload)if(excluded.add(i)){faults.put(s.nodes.get(i).id,11);retry=true;}if(retry)continue;int[] remaining=s.budget.clone();for(int i=0;i<s.nodes.size();i++)if(!excluded.contains(i)){int r=s.nodes.get(i).rack;if(p.power[i]>remaining[r]){excluded.add(i);faults.put(s.nodes.get(i).id,6);retry=true;}else remaining[r]-=p.power[i];}if(!retry)break;}
        for(TaskData.Job j:jobs){TaskEngine.Result r=p.tasks.get(j.id);if(r==null)continue;j.work=r.work;j.usedCPU=r.cpu;j.usedGPU=r.gpus;j.usedRAM=r.ram;j.usedDisk=r.disk;j.usedNet=r.net;j.full=r.full;d.reward(j,r.work);}markDirty();}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){NBTTagList ss=new NBTTagList();for(Snapshot s:snapshots.values()){NBTTagCompound a=new NBTTagCompound();a.setUniqueId("Anchor",s.anchor);a.setUniqueId("Owner",s.owner);a.setIntArray("Caps",s.caps);a.setIntArray("Budget",s.budget);a.setInteger("WAN",s.wan);NBTTagList pp=new NBTTagList();for(BlockPos p:s.positions){NBTTagCompound q=new NBTTagCompound();q.setLong("P",p.toLong());pp.appendTag(q);}a.setTag("Positions",pp);NBTTagList nn=new NBTTagList();for(TaskEngine.Node node:s.nodes){NBTTagCompound q=new NBTTagCompound();q.setUniqueId("Id",node.id);q.setIntArray("Spec",new int[]{node.rack,node.cpu,node.cores,node.ram,node.disk,node.net,node.psu,node.idle,node.cpuPeak});q.setIntArray("GPU",node.gpu);nn.appendTag(q);}a.setTag("Nodes",nn);ss.appendTag(a);}n.setTag("Snapshots",ss);NBTTagList fs=new NBTTagList();for(Map.Entry<UUID,Integer> f:faults.entrySet()){NBTTagCompound a=new NBTTagCompound();a.setUniqueId("Id",f.getKey());a.setInteger("Reason",f.getValue());fs.appendTag(a);}n.setTag("Faults",fs);return n;}
    @Override public void readFromNBT(NBTTagCompound n){snapshots.clear();NBTTagList ss=n.getTagList("Snapshots",10);for(int i=0;i<ss.tagCount();i++){NBTTagCompound a=ss.getCompoundTagAt(i);if(!a.hasUniqueId("Anchor")||!a.hasUniqueId("Owner"))continue;Snapshot s=new Snapshot();s.anchor=a.getUniqueId("Anchor");s.owner=a.getUniqueId("Owner");s.caps=a.getIntArray("Caps");s.budget=a.getIntArray("Budget");s.wan=a.getInteger("WAN");if(s.caps.length!=s.budget.length)continue;NBTTagList pp=a.getTagList("Positions",10);for(int j=0;j<pp.tagCount();j++)s.positions.add(BlockPos.fromLong(pp.getCompoundTagAt(j).getLong("P")));NBTTagList nn=a.getTagList("Nodes",10);for(int j=0;j<nn.tagCount();j++){NBTTagCompound q=nn.getCompoundTagAt(j);int[] v=q.getIntArray("Spec"),gpu=q.getIntArray("GPU");if(v.length==9&&gpu.length==4&&q.hasUniqueId("Id")&&v[0]>=0&&v[0]<s.caps.length)s.nodes.add(new TaskEngine.Node(q.getUniqueId("Id"),v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],gpu));}if(!s.positions.isEmpty()&&!s.nodes.isEmpty())snapshots.put(s.anchor,s);}faults.clear();NBTTagList fs=n.getTagList("Faults",10);for(int i=0;i<fs.tagCount();i++){NBTTagCompound a=fs.getCompoundTagAt(i);if(a.hasUniqueId("Id"))faults.put(a.getUniqueId("Id"),a.getInteger("Reason"));}}
}
