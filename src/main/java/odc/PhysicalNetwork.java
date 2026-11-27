package odc;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Persistent discovery index and authoritative dimension-wide bandwidth calculation. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class PhysicalNetwork extends WorldSavedData {
    private static final String KEY="odc_physical_network_v1";
    private static final Map<World,Set<BlockPos>> pending=new IdentityHashMap<>();
    final PhysicalTopology topology=new PhysicalTopology();
    final LogicalLinks uplinks=new LogicalLinks();
    final MapEdits layout=new MapEdits();
    private final Set<PhysicalTopology.Pos> verified=new HashSet<>(); // runtime only, never serialized
    private long availabilityRevision;private Set<PhysicalTopology.Pos> virtual=Collections.emptySet();
    private final BandwidthCache bandwidthCache=new BandwidthCache();
    private BandwidthEngine.Result residualResult;private final Map<UUID,Integer> residualSingle=new HashMap<>();
    private void verify(PhysicalTopology.Pos p,boolean value){if(value?verified.add(p):verified.remove(p))availabilityRevision++;}
    public PhysicalNetwork(){super(KEY);}
    public PhysicalNetwork(String name){super(name);}
    static PhysicalNetwork get(World w){if(w.isRemote)throw new IllegalStateException("Server topology only");String key=KEY+"_"+w.provider.getDimension();net.minecraft.world.storage.MapStorage storage=w.getPerWorldStorage();PhysicalNetwork data=(PhysicalNetwork)storage.getOrLoadData(PhysicalNetwork.class,key);if(data==null){data=new PhysicalNetwork(key);storage.setData(key,data);}return data;}
    static PhysicalTopology.Pos pos(BlockPos p){return new PhysicalTopology.Pos(p.getX(),p.getY(),p.getZ());}
    static BlockPos block(PhysicalTopology.Pos p){return new BlockPos(p.x,p.y,p.z);}
    static void changed(World w,BlockPos p){if(w!=null&&!w.isRemote)pending.computeIfAbsent(w,k->new HashSet<>()).add(p.toImmutable());}
    static void blockAdded(World w,BlockPos p,Block b){if(w==null||w.isRemote)return;PhysicalTopology.Kind k=kind(b);if(k==null)return;PhysicalNetwork d=get(w);if(k==PhysicalTopology.Kind.DATA||k==PhysicalTopology.Kind.FIBER){if(d.topology.put(pos(p),new PhysicalTopology.Cell(k,Collections.emptyList())))d.markDirty();d.verify(pos(p),true);}changed(w,p);}
    static void blockRemoved(World w,BlockPos p,Block b){if(w==null||w.isRemote||kind(b)==null)return;PhysicalNetwork d=get(w);if(d.topology.put(pos(p),null))d.markDirty();d.verify(pos(p),false);changed(w,p);}
    static boolean relevant(Block b){return kind(b)!=null;}
    private static PhysicalTopology.Kind kind(Block b){
        if(b==Hardware.DATA_CABLE)return PhysicalTopology.Kind.DATA;
        if(b==Hardware.FIBER_CABLE)return PhysicalTopology.Kind.FIBER;
        if(b==Hardware.RACK||b==Hardware.RACK_GLASS)return PhysicalTopology.Kind.RACK;
        if(b==Hardware.COMPUTER)return PhysicalTopology.Kind.CONTROLLER;
        if(Hardware.networkTerminal(b))return PhysicalTopology.Kind.TERMINAL;
        if(Hardware.antenna(b))return PhysicalTopology.Kind.ANTENNA;
        return null;
    }
    private static PhysicalTopology.Device device(World w,BlockPos p,UUID id,PhysicalTopology.Type type,String name,String model,int capacity,int used,boolean powered,UUID parent,int slot,int port){return new PhysicalTopology.Device(id,type,name,model,pos(p),w.provider.getDimension(),capacity,used,powered,parent,slot,port);}
    private void refresh(World w,BlockPos p){
        if(!w.isBlockLoaded(p)){verify(pos(p),false);return;} // never erase an unloaded block
        PhysicalTopology.Pos key=pos(p);PhysicalTopology.Kind k=kind(w.getBlockState(p).getBlock());
        if(k==null||w.getBlockState(p).getBlock() instanceof Blocks.Tower&&w.getBlockState(p).getValue(Blocks.PART)!=0){verify(key,false);if(topology.put(key,null))markDirty();return;}
        List<PhysicalTopology.Device> devices=new ArrayList<>();TileEntity tile=w.getTileEntity(p);
        if(k==PhysicalTopology.Kind.RACK){
            if(!(tile instanceof Tiles.Rack)||tile.isInvalid()){verify(key,false);changed(w,p);return;}
            Tiles.Rack r=(Tiles.Rack)tile;
            if(r.role!=-1){int bay=r.switchBay(),tier=r.switchTier();if(bay>=0&&tier>0){
                ItemStack sw=r.slots.get(bay);boolean had=sw.hasTagCompound()&&sw.getTagCompound().hasUniqueId("SwitchId");UUID id=Hardware.switchId(sw);if(!had)r.markDirty();
                int used=0;for(int port=0;port<Hardware.ports(tier);port++){int b=r.ports[port]-1;if(r.isServer(b))used+=Math.max(0,r.netUsed[b]);}
                devices.add(device(w,p,id,PhysicalTopology.Type.SWITCH,sw.getDisplayName(),sw.getItem().getRegistryName().toString(),Hardware.switchMbps(tier),used,r.switchOnline,null,bay,-1));
                for(int port=0;port<Hardware.ports(tier);port++){int b=r.ports[port]-1;if(!r.isServer(b)||Hardware.nic(r.slots.get(b))<=0)continue;ItemStack s=r.slots.get(b);boolean identified=s.hasTagCompound()&&s.getTagCompound().hasUniqueId("ServerId");UUID server=ClusterScheduler.serverId(s);if(!identified)r.markDirty();
                    if(r.cableIds[port]==null){r.cableIds[port]=UUID.randomUUID();r.markDirty();}
                    devices.add(new PhysicalTopology.Device(server,PhysicalTopology.Type.SERVER,s.getDisplayName(),s.getItem().getRegistryName().toString(),pos(p),w.provider.getDimension(),Hardware.nic(s),r.netUsed[b],(r.enabledMask&(1<<b))!=0&&Hardware.state(s)==ServerSpec.READY&&r.energy.fed()&&r.switchOnline,id,b,port,r.cableIds[port],r.cableTier(port)));
                }
            }}
        }else if(k==PhysicalTopology.Kind.ANTENNA){
            if(!(tile instanceof Tiles.Station)||tile.isInvalid()){verify(key,false);changed(w,p);return;}
            Tiles.Station a=(Tiles.Station)tile;NetworkEquipment e=a.equipment();devices.add(device(w,p,a.networkId,e.type,e.id,"odc:"+e.id,a.bandwidth(),-1,a.online&&a.energy.fed(),null,-1,-1));
        }else if(k==PhysicalTopology.Kind.TERMINAL){
            if(!(tile instanceof Tiles.NetworkTerminal)||tile.isInvalid()){verify(key,false);changed(w,p);return;}
            Tiles.NetworkTerminal t=(Tiles.NetworkTerminal)tile;devices.add(device(w,p,t.networkId,PhysicalTopology.Type.TERMINAL,t.equipment().id,"odc:"+t.equipment().id,t.bandwidth(),-1,t.online&&t.energy.fed(),null,-1,-1));
        }
        // DataOS upper blocks and rack upper halves remain physical LAN cells, not duplicate devices.
        if(topology.put(key,new PhysicalTopology.Cell(k,devices)))markDirty();verify(key,true);
    }
    private void flush(World w){Set<BlockPos> work=pending.remove(w);if(work!=null)for(BlockPos p:work)refresh(w,p);}
    public static PhysicalTopology.View view(World w,BlockPos origin){PhysicalNetwork d=get(w);d.flush(w);return d.topology.view(pos(origin),p->d.verified.contains(p)&&w.isBlockLoaded(block(p)));}
    /** Global, dimension-local projection of active fixed reservations, not measured traffic.
     * Used for task admission, scheduling and monitoring. */
    private static List<BandwidthEngine.Request> requests(World w,TaskData.Job ignore){List<BandwidthEngine.Request> out=new ArrayList<>();for(TaskData.Job j:TaskData.get(w).reservingJobs())if(j.enabled&&j!=ignore)out.addAll(NetworkAdmission.requests(j.terms.getUniqueId("Key"),j.bindings,true));return out;}
    public static BandwidthEngine.Result bandwidth(World w){PhysicalNetwork d=get(w);d.flush(w);Set<PhysicalTopology.Pos> virtualNow=OfflineState.get(w).virtualCells(w);if(!virtualNow.equals(d.virtual)){d.virtual=virtualNow;d.availabilityRevision++;}return d.bandwidthCache.get(d.topology,d.uplinks,d.availabilityRevision,p->d.verified.contains(p)&&w.isBlockLoaded(block(p))||d.virtual.contains(p),requests(w,null));}
    static Set<PhysicalTopology.Pos> exclusiveFootprint(World w,BlockPos origin){PhysicalNetwork d=get(w);d.flush(w);return d.topology.exclusiveFootprint(pos(origin),p->d.verified.contains(p)&&w.isBlockLoaded(block(p)));}
    static void refreshRouting(World w){PhysicalNetwork d=get(w);for(TileEntity t:new ArrayList<>(w.loadedTileEntityList))if((t instanceof Tiles.Rack||t instanceof Tiles.NetworkDevice)&&Network.live(w,t))changed(w,t.getPos());d.flush(w);}
    static NetworkAdmission.Quote quote(World w,TaskData.Job ignore){PhysicalNetwork d=get(w);BandwidthEngine.Result base=bandwidth(w);List<BandwidthEngine.Request> requests=requests(w,ignore);BandwidthEngine engine=d.bandwidthCache.engine();if(ignore!=null&&ignore.enabled)base=engine.solve(requests);return new NetworkAdmission.Quote(engine,requests,base);}
    static int additional(World w,BandwidthEngine.Result result,Collection<UUID> ids){PhysicalNetwork d=get(w);if(d.residualResult!=result){d.residualResult=result;d.residualSingle.clear();}if(ids.size()==1){UUID id=ids.iterator().next();return d.residualSingle.computeIfAbsent(id,k->NetworkAdmission.floor(d.bandwidthCache.engine().additional(result,Collections.singleton(k))));}return NetworkAdmission.floor(d.bandwidthCache.engine().additional(result,ids));}
    static boolean hasWanRoute(World w,UUID id){return get(w).bandwidthCache.engine().hasWanRoute(id);}
    static NetworkMonitor monitor(World w,BlockPos pos){return new NetworkMonitor(view(w,pos),logicalView(w,pos),bandwidth(w));}
    /** Logical snapshot with calculated (not measured) traffic from the shared engine. */
    public static List<LogicalLinks.Link> logicalView(World w,BlockPos origin){PhysicalNetwork d=get(w);PhysicalTopology.View v=view(w,origin);BandwidthEngine.Result flow=bandwidth(w);List<LogicalLinks.Link> out=new ArrayList<>();
        for(PhysicalTopology.Link l:v.links){PhysicalTopology.State a=v.devices.get(l.from),b=v.devices.get(l.to);boolean patch=l.type==PhysicalTopology.LinkType.PATCH;LogicalLinks.Kind k=patch?LogicalLinks.Kind.PATCH:LogicalLinks.Kind.FIBER;int tier=patch?Math.max(1,b.device.patchTier):0;int cap=patch?LogicalLinks.speed(tier):Math.min(a.device.capacityMbps,b.device.capacityMbps);UUID id=patch&&b.device.patchId!=null?b.device.patchId:LogicalLinks.automaticId(k,l.from,l.to);LogicalLinks.State state=!l.verified?LogicalLinks.State.UNVERIFIED:a.available&&b.available?LogicalLinks.State.ACTIVE:LogicalLinks.State.OFFLINE;out.add(new LogicalLinks.Link(id,l.from,l.to,k,tier,cap,state,usage(flow.links.get(id),state)));}
        for(LogicalLinks.Link l:d.uplinks.all())if(v.devices.containsKey(l.from)||v.devices.containsKey(l.to)){LogicalLinks.State state=LogicalLinks.state(v,l.from,l.to);out.add(new LogicalLinks.Link(l.id,l.from,l.to,l.kind,l.tier,l.capacityMbps,state,usage(flow.links.get(l.id),state)));}return out;}
    private static int usage(BandwidthEngine.Usage u,LogicalLinks.State state){return state==LogicalLinks.State.UNVERIFIED?-1:state!=LogicalLinks.State.ACTIVE?0:u==null?-1:(int)Math.round(u.served);}
    static void removeUplinks(World w,BlockPos p,UUID device){PhysicalNetwork d=get(w);for(LogicalLinks.Link l:d.uplinks.removeDevice(device)){OfflineState.invalidateAt(w,p);LanCables.refund(w,p,null,l.tier);d.markDirty();}}
    static boolean editUplink(net.minecraft.entity.player.EntityPlayer player,Tiles.NetworkTerminal terminal,UUID sw,int op,int tier){World w=player.world;if(w.isRemote||!terminal.allows(player)||!w.isBlockLoaded(terminal.getPos())||w.getTileEntity(terminal.getPos())!=terminal)return false;PhysicalNetwork d=get(w);PhysicalTopology.View v=view(w,terminal.getPos());LogicalLinks.Link old=d.uplinks.find(terminal.networkId,sw);
        if(op==1){if(old==null)return false;d.uplinks.remove(terminal.networkId,sw);LanCables.refund(w,terminal.getPos(),player,old.tier);d.markDirty();OfflineState.invalidateAt(w,terminal.getPos());networkEvent(terminal,old.id,"uplink_removed","Uplink disconnected (LAN T"+old.tier+")");return true;}
        PhysicalTopology.State target=v.devices.get(sw),self=v.devices.get(terminal.networkId);if(target==null||self==null||target.device.type!=PhysicalTopology.Type.SWITCH||!target.loaded||!v.verifiedLanPositions.contains(target.device.pos))return false;TileEntity t=w.getTileEntity(block(target.device.pos));if(!(t instanceof Tiles.Rack)||!((Tiles.Rack)t).allows(player))return false;Tiles.Rack r=(Tiles.Rack)t;int b=r.switchBay();if(b<0||!Hardware.switchId(r.slots.get(b)).equals(sw))return false;
        if(op!=0&&op!=2||op==0&&old!=null||op==2&&(old==null||LogicalLinks.speed(tier)<=old.capacityMbps)||!LanCables.take(player,tier))return false;
        boolean ok=op==0?d.uplinks.add(terminal.networkId,sw,tier)!=null:d.uplinks.replace(terminal.networkId,sw,tier);if(!ok){LanCables.refund(w,terminal.getPos(),player,tier);return false;}if(old!=null)LanCables.refund(w,terminal.getPos(),player,old.tier);d.markDirty();OfflineState.invalidateAt(w,terminal.getPos());LogicalLinks.Link link=d.uplinks.find(terminal.networkId,sw);networkEvent(terminal,link.id,op==0?"uplink_added":"uplink_upgraded",op==0?"Uplink connected (LAN T"+tier+")":"Uplink cable replaced (LAN T"+tier+")");return true;}
    static void networkEvent(Tiles.NetworkTerminal terminal,UUID source,String code,String message){World w=terminal.getWorld();if(w==null||w.isRemote||terminal.owner==null)return;Network.Domain domain=Network.scan(w,terminal.getPos());if(domain==null)return;UUID scope=null;for(Tiles.Owned t:domain.devices)if(Objects.equals(t.owner,terminal.owner)){UUID id=EventSystem.scope(t);if(id!=null&&(scope==null||id.compareTo(scope)<0))scope=id;}if(scope!=null)TaskData.get(w).record(terminal.owner,scope,source,EventJournal.Level.INFO,EventJournal.Category.NETWORK,EventJournal.Target.NONE,code,message,-1,0);}
    static boolean editMap(net.minecraft.entity.player.EntityPlayer player,Tiles.NetworkTerminal root,int op,UUID a,UUID b,int x,int y){
        World w=player.world;if(w.isRemote||!root.operational()||!root.allows(player)||!Network.live(w,root))return false;PhysicalNetwork d=get(w);PhysicalTopology.View v=view(w,root.getPos());
        if(op==3){if(!v.devices.containsKey(a)||!d.layout.put(a,x,y))return false;d.markDirty();return true;}
        if(op==4){d.layout.reset(v.devices.keySet());d.markDirty();return true;}
        if(op<0||op>2)return false;PhysicalTopology.State t=v.devices.get(a),sw=v.devices.get(b);
        if(t==null||t.device.type!=PhysicalTopology.Type.TERMINAL||!t.loaded||!v.verifiedLanPositions.contains(t.device.pos))return false;
        TileEntity tile=w.getTileEntity(block(t.device.pos));if(!(tile instanceof Tiles.NetworkTerminal)||!((Tiles.NetworkTerminal)tile).networkId.equals(a)||!((Tiles.NetworkTerminal)tile).allows(player))return false;
        if(sw==null||sw.device.type!=PhysicalTopology.Type.SWITCH||!sw.loaded||!v.verifiedLanPositions.contains(sw.device.pos))return false;
        TileEntity rack=w.getTileEntity(block(sw.device.pos));if(!(rack instanceof Tiles.Rack)||!((Tiles.Rack)rack).allows(player))return false;
        return editUplink(player,(Tiles.NetworkTerminal)tile,b,op,x);
    }
    static List<PhysicalTopology.Peer> opticalPeers(World w,BlockPos origin){PhysicalNetwork d=get(w);d.flush(w);return d.topology.opticalPeers(pos(origin),p->d.verified.contains(p)&&w.isBlockLoaded(block(p)));}
    /** Called only on block/TE changes and chunk lifecycle, not a full graph scan each tick. */
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST) public static void tick(TickEvent.WorldTickEvent e){World w=e.world;if(w.isRemote||e.phase!=TickEvent.Phase.END)return;PhysicalNetwork d=get(w);
        if(w.getTotalWorldTime()%20==0)for(TileEntity tile:new ArrayList<>(w.loadedTileEntityList))if(tile instanceof Tiles.Rack||tile instanceof Tiles.Station||tile instanceof Tiles.NetworkTerminal)if(Network.live(w,tile))changed(w,tile.getPos());
        d.flush(w);
        if(w.getTotalWorldTime()%20==0)bandwidth(w);
    }
    private static boolean inChunk(PhysicalTopology.Pos p,Chunk c){return (p.x>>4)==c.x&&(p.z>>4)==c.z;}
    @SubscribeEvent public static void load(ChunkEvent.Load e){World w=e.getWorld();if(w.isRemote)return;PhysicalNetwork d=get(w);Chunk c=e.getChunk();
        // Revalidate known cells, including blocks removed while the chunk was unavailable.
        for(PhysicalTopology.Pos p:d.topology.cells().keySet())if(inChunk(p,c))changed(w,block(p));
        // Discover existing relevant blocks (including cable-only chunks) without loading neighbors.
        ExtendedBlockStorage[] sections=c.getBlockStorageArray();for(ExtendedBlockStorage section:sections){if(section==null||section.isEmpty())continue;int y0=section.getYLocation();for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(relevant(section.get(x,y,z).getBlock()))changed(w,new BlockPos((c.x<<4)+x,y0+y,(c.z<<4)+z));}
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload e){if(e.getWorld().isRemote)return;PhysicalNetwork d=get(e.getWorld());Chunk c=e.getChunk();Set<BlockPos> queued=pending.get(e.getWorld());if(queued!=null){List<BlockPos> local=new ArrayList<>();for(BlockPos x:queued)if((x.getX()>>4)==c.x&&(x.getZ()>>4)==c.z)local.add(x);queued.removeAll(local);for(BlockPos x:local)if(e.getWorld().isBlockLoaded(x))d.refresh(e.getWorld(),x);queued.removeAll(local);}if(d.verified.removeIf(p->inChunk(p,c)))d.availabilityRevision++;}
    @SubscribeEvent public static void worldUnload(WorldEvent.Unload e){pending.remove(e.getWorld());}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){NBTTagList list=new NBTTagList();for(Map.Entry<PhysicalTopology.Pos,PhysicalTopology.Cell> entry:topology.cells().entrySet()){NBTTagCompound x=new NBTTagCompound();x.setLong("Pos",block(entry.getKey()).toLong());x.setString("Kind",entry.getValue().kind.name());NBTTagList devices=new NBTTagList();for(PhysicalTopology.Device d:entry.getValue().devices){NBTTagCompound t=new NBTTagCompound();t.setUniqueId("Id",d.id);if(d.parent!=null)t.setUniqueId("Parent",d.parent);t.setString("Type",d.type.name());t.setString("Name",d.name);t.setString("Model",d.model);t.setInteger("Dimension",d.dimension);t.setInteger("Capacity",d.capacityMbps);t.setInteger("Used",d.usedMbps);t.setBoolean("Powered",d.powered);t.setInteger("Slot",d.slot);t.setInteger("Port",d.port);if(d.patchId!=null)t.setUniqueId("PatchId",d.patchId);t.setInteger("PatchTier",d.patchTier);devices.appendTag(t);}x.setTag("Devices",devices);list.appendTag(x);}n.setTag("Cells",list);NBTTagList ll=new NBTTagList();for(LogicalLinks.Link l:uplinks.all()){NBTTagCompound z=new NBTTagCompound();z.setUniqueId("Id",l.id);z.setUniqueId("From",l.from);z.setUniqueId("To",l.to);z.setInteger("Tier",l.tier);ll.appendTag(z);}n.setTag("Uplinks",ll);NBTTagList coords=new NBTTagList();for(Map.Entry<UUID,NetworkMapLayout.Point> e:layout.positions.entrySet()){NBTTagCompound z=new NBTTagCompound();z.setUniqueId("Id",e.getKey());z.setInteger("X",(int)e.getValue().x);z.setInteger("Y",(int)e.getValue().y);coords.appendTag(z);}n.setTag("MapLayout",coords);return n;}
    @Override public void readFromNBT(NBTTagCompound n){layout.positions.clear();NBTTagList coords=n.getTagList("MapLayout",10);for(int i=0;i<coords.tagCount();i++){NBTTagCompound z=coords.getCompoundTagAt(i);if(z.hasUniqueId("Id"))layout.put(z.getUniqueId("Id"),z.getInteger("X"),z.getInteger("Y"));}topology.clear();verified.clear();virtual=Collections.emptySet();availabilityRevision++;bandwidthCache.clear();uplinks.clear();NBTTagList ll=n.getTagList("Uplinks",10);for(int k=0;k<ll.tagCount();k++){NBTTagCompound z=ll.getCompoundTagAt(k);if(z.hasUniqueId("Id")&&z.hasUniqueId("From")&&z.hasUniqueId("To")){int tier=z.getInteger("Tier");uplinks.restore(new LogicalLinks.Link(z.getUniqueId("Id"),z.getUniqueId("From"),z.getUniqueId("To"),LogicalLinks.Kind.UPLINK,tier,LogicalLinks.speed(tier),LogicalLinks.State.UNVERIFIED,-1));}}NBTTagList list=n.getTagList("Cells",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound x=list.getCompoundTagAt(i);PhysicalTopology.Pos p=pos(BlockPos.fromLong(x.getLong("Pos")));try{PhysicalTopology.Kind k=PhysicalTopology.Kind.valueOf(x.getString("Kind"));List<PhysicalTopology.Device> devices=new ArrayList<>();NBTTagList dl=x.getTagList("Devices",10);for(int j=0;j<dl.tagCount();j++){NBTTagCompound t=dl.getCompoundTagAt(j);if(!t.hasUniqueId("Id"))continue;try{devices.add(new PhysicalTopology.Device(t.getUniqueId("Id"),PhysicalTopology.Type.valueOf(t.getString("Type")),t.getString("Name"),t.getString("Model"),p,t.getInteger("Dimension"),t.getInteger("Capacity"),t.getInteger("Used"),t.getBoolean("Powered"),t.hasUniqueId("Parent")?t.getUniqueId("Parent"):null,t.getInteger("Slot"),t.getInteger("Port"),t.hasUniqueId("PatchId")?t.getUniqueId("PatchId"):null,t.getInteger("PatchTier")));}catch(IllegalArgumentException ignored){}}
        topology.put(p,new PhysicalTopology.Cell(k,devices));}catch(IllegalArgumentException ignored){}}
    }
}
