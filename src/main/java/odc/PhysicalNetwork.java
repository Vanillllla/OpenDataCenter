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

/** Persistent discovery index. Old Network/ClusterScheduler traffic is deliberately separate until Stage 4/7. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class PhysicalNetwork extends WorldSavedData {
    private static final String KEY="odc_physical_network_v1";
    private static final Map<World,Set<BlockPos>> pending=new IdentityHashMap<>();
    final PhysicalTopology topology=new PhysicalTopology();
    final LogicalLinks uplinks=new LogicalLinks();
    private final Set<PhysicalTopology.Pos> verified=new HashSet<>(); // runtime only, never serialized
    public PhysicalNetwork(){super(KEY);}
    public PhysicalNetwork(String name){super(name);}
    static PhysicalNetwork get(World w){if(w.isRemote)throw new IllegalStateException("Server topology only");String key=KEY+"_"+w.provider.getDimension();net.minecraft.world.storage.MapStorage storage=w.getPerWorldStorage();PhysicalNetwork data=(PhysicalNetwork)storage.getOrLoadData(PhysicalNetwork.class,key);if(data==null){data=new PhysicalNetwork(key);storage.setData(key,data);}return data;}
    static PhysicalTopology.Pos pos(BlockPos p){return new PhysicalTopology.Pos(p.getX(),p.getY(),p.getZ());}
    static BlockPos block(PhysicalTopology.Pos p){return new BlockPos(p.x,p.y,p.z);}
    static void changed(World w,BlockPos p){if(w!=null&&!w.isRemote)pending.computeIfAbsent(w,k->new HashSet<>()).add(p.toImmutable());}
    static void blockAdded(World w,BlockPos p,Block b){if(w==null||w.isRemote)return;PhysicalTopology.Kind k=kind(b);if(k==null)return;PhysicalNetwork d=get(w);if(k==PhysicalTopology.Kind.DATA||k==PhysicalTopology.Kind.FIBER){if(d.topology.put(pos(p),new PhysicalTopology.Cell(k,Collections.emptyList())))d.markDirty();d.verified.add(pos(p));}changed(w,p);}
    static void blockRemoved(World w,BlockPos p,Block b){if(w==null||w.isRemote||kind(b)==null)return;PhysicalNetwork d=get(w);if(d.topology.put(pos(p),null))d.markDirty();d.verified.remove(pos(p));changed(w,p);}
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
        if(!w.isBlockLoaded(p)){verified.remove(pos(p));return;} // never erase an unloaded block
        PhysicalTopology.Pos key=pos(p);PhysicalTopology.Kind k=kind(w.getBlockState(p).getBlock());
        if(k==null||w.getBlockState(p).getBlock() instanceof Blocks.Tower&&w.getBlockState(p).getValue(Blocks.PART)!=0){verified.remove(key);if(topology.put(key,null))markDirty();return;}
        List<PhysicalTopology.Device> devices=new ArrayList<>();TileEntity tile=w.getTileEntity(p);
        if(k==PhysicalTopology.Kind.RACK){
            if(!(tile instanceof Tiles.Rack)||tile.isInvalid()){verified.remove(key);changed(w,p);return;}
            Tiles.Rack r=(Tiles.Rack)tile;
            if(r.role!=-1){int bay=r.switchBay(),tier=r.switchTier();if(bay>=0&&tier>0){
                ItemStack sw=r.slots.get(bay);boolean had=sw.hasTagCompound()&&sw.getTagCompound().hasUniqueId("SwitchId");UUID id=Hardware.switchId(sw);if(!had)r.markDirty();
                int used=0;for(int port=0;port<Hardware.ports(tier);port++){int b=r.ports[port]-1;if(r.isServer(b))used+=Math.max(0,r.netUsed[b]);}
                devices.add(device(w,p,id,PhysicalTopology.Type.SWITCH,sw.getDisplayName(),sw.getItem().getRegistryName().toString(),Hardware.switchMbps(tier),used,r.switchOnline,null,bay,-1));
                for(int port=0;port<Hardware.ports(tier);port++){int b=r.ports[port]-1;if(!r.isServer(b)||Hardware.nic(r.slots.get(b))<=0)continue;ItemStack s=r.slots.get(b);boolean identified=s.hasTagCompound()&&s.getTagCompound().hasUniqueId("ServerId");UUID server=ClusterScheduler.serverId(s);if(!identified)r.markDirty();
                    if(r.cableIds[port]==null){r.cableIds[port]=UUID.randomUUID();r.markDirty();}
                    devices.add(new PhysicalTopology.Device(server,PhysicalTopology.Type.SERVER,s.getDisplayName(),s.getItem().getRegistryName().toString(),pos(p),w.provider.getDimension(),Hardware.nic(s),r.netUsed[b],(r.poweredMask&(1<<b))!=0&&r.switchOnline,id,b,port,r.cableIds[port],r.cableTier(port)));
                }
            }}
        }else if(k==PhysicalTopology.Kind.ANTENNA){
            if(!(tile instanceof Tiles.Station)||tile.isInvalid()){verified.remove(key);changed(w,p);return;}
            Tiles.Station a=(Tiles.Station)tile;NetworkEquipment e=a.equipment();devices.add(device(w,p,a.networkId,e.type,e.id,"odc:"+e.id,a.bandwidth(),-1,a.online,null,-1,-1));
        }else if(k==PhysicalTopology.Kind.TERMINAL){
            if(!(tile instanceof Tiles.NetworkTerminal)||tile.isInvalid()){verified.remove(key);changed(w,p);return;}
            Tiles.NetworkTerminal t=(Tiles.NetworkTerminal)tile;devices.add(device(w,p,t.networkId,PhysicalTopology.Type.TERMINAL,t.equipment().id,"odc:"+t.equipment().id,t.bandwidth(),-1,t.online,null,-1,-1));
        }
        // DataOS upper blocks and rack upper halves remain physical LAN cells, not duplicate devices.
        if(topology.put(key,new PhysicalTopology.Cell(k,devices)))markDirty();verified.add(key);
    }
    private void flush(World w){Set<BlockPos> work=pending.remove(w);if(work!=null)for(BlockPos p:work)refresh(w,p);}
    public static PhysicalTopology.View view(World w,BlockPos origin){PhysicalNetwork d=get(w);d.flush(w);return d.topology.view(pos(origin),p->d.verified.contains(p)&&w.isBlockLoaded(block(p)));}
    /** Authoritative link snapshot. Stage 4 will populate actual flow, currently unknown (-1). */
    public static List<LogicalLinks.Link> logicalView(World w,BlockPos origin){PhysicalNetwork d=get(w);PhysicalTopology.View v=view(w,origin);List<LogicalLinks.Link> out=new ArrayList<>();
        for(PhysicalTopology.Link l:v.links){PhysicalTopology.State a=v.devices.get(l.from),b=v.devices.get(l.to);boolean patch=l.type==PhysicalTopology.LinkType.PATCH;LogicalLinks.Kind k=patch?LogicalLinks.Kind.PATCH:LogicalLinks.Kind.FIBER;int tier=patch?Math.max(1,b.device.patchTier):0;int cap=patch?LogicalLinks.speed(tier):Math.min(a.device.capacityMbps,b.device.capacityMbps);UUID id=patch&&b.device.patchId!=null?b.device.patchId:LogicalLinks.automaticId(k,l.from,l.to);LogicalLinks.State state=!l.verified?LogicalLinks.State.UNVERIFIED:a.available&&b.available?LogicalLinks.State.ACTIVE:LogicalLinks.State.OFFLINE;out.add(new LogicalLinks.Link(id,l.from,l.to,k,tier,cap,state,state==LogicalLinks.State.OFFLINE?0:-1));}
        for(LogicalLinks.Link l:d.uplinks.all())if(v.devices.containsKey(l.from)||v.devices.containsKey(l.to)){LogicalLinks.State state=LogicalLinks.state(v,l.from,l.to);out.add(new LogicalLinks.Link(l.id,l.from,l.to,l.kind,l.tier,l.capacityMbps,state,state==LogicalLinks.State.OFFLINE||state==LogicalLinks.State.SEPARATED?0:-1));}return out;}
    static void removeUplinks(World w,BlockPos p,UUID device){PhysicalNetwork d=get(w);for(LogicalLinks.Link l:d.uplinks.removeDevice(device)){LanCables.refund(w,p,null,l.tier);d.markDirty();}}
    static boolean editUplink(net.minecraft.entity.player.EntityPlayer player,Tiles.NetworkTerminal terminal,UUID sw,int op,int tier){World w=player.world;if(w.isRemote||!terminal.allows(player)||!w.isBlockLoaded(terminal.getPos())||w.getTileEntity(terminal.getPos())!=terminal)return false;PhysicalNetwork d=get(w);PhysicalTopology.View v=view(w,terminal.getPos());LogicalLinks.Link old=d.uplinks.find(terminal.networkId,sw);
        if(op==1){if(old==null)return false;d.uplinks.remove(terminal.networkId,sw);LanCables.refund(w,terminal.getPos(),player,old.tier);d.markDirty();return true;}
        PhysicalTopology.State target=v.devices.get(sw),self=v.devices.get(terminal.networkId);if(target==null||self==null||target.device.type!=PhysicalTopology.Type.SWITCH||!target.loaded||!v.verifiedLanPositions.contains(target.device.pos))return false;TileEntity t=w.getTileEntity(block(target.device.pos));if(!(t instanceof Tiles.Rack)||!((Tiles.Rack)t).allows(player))return false;Tiles.Rack r=(Tiles.Rack)t;int b=r.switchBay();if(b<0||!Hardware.switchId(r.slots.get(b)).equals(sw))return false;
        if(op!=0&&op!=2||op==0&&old!=null||op==2&&(old==null||LogicalLinks.speed(tier)<=old.capacityMbps)||!LanCables.take(player,tier))return false;
        boolean ok=op==0?d.uplinks.add(terminal.networkId,sw,tier)!=null:d.uplinks.replace(terminal.networkId,sw,tier);if(!ok){LanCables.refund(w,terminal.getPos(),player,tier);return false;}if(old!=null)LanCables.refund(w,terminal.getPos(),player,old.tier);d.markDirty();return true;}
    static List<PhysicalTopology.Peer> opticalPeers(World w,BlockPos origin){PhysicalNetwork d=get(w);d.flush(w);return d.topology.opticalPeers(pos(origin),p->d.verified.contains(p)&&w.isBlockLoaded(block(p)));}
    /** Called only on block/TE changes and chunk lifecycle, not a full graph scan each tick. */
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent e){World w=e.world;if(w.isRemote||e.phase!=TickEvent.Phase.END)return;PhysicalNetwork d=get(w);
        if(w.getTotalWorldTime()%20==0)for(TileEntity tile:new ArrayList<>(w.loadedTileEntityList))if(tile instanceof Tiles.Rack||tile instanceof Tiles.Station||tile instanceof Tiles.NetworkTerminal)if(Network.live(w,tile))changed(w,tile.getPos());
        d.flush(w);
    }
    private static boolean inChunk(PhysicalTopology.Pos p,Chunk c){return (p.x>>4)==c.x&&(p.z>>4)==c.z;}
    @SubscribeEvent public static void load(ChunkEvent.Load e){World w=e.getWorld();if(w.isRemote)return;PhysicalNetwork d=get(w);Chunk c=e.getChunk();
        // Revalidate known cells, including blocks removed while the chunk was unavailable.
        for(PhysicalTopology.Pos p:d.topology.cells().keySet())if(inChunk(p,c))changed(w,block(p));
        // Discover existing relevant blocks (including cable-only chunks) without loading neighbors.
        ExtendedBlockStorage[] sections=c.getBlockStorageArray();for(ExtendedBlockStorage section:sections){if(section==null||section.isEmpty())continue;int y0=section.getYLocation();for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(relevant(section.get(x,y,z).getBlock()))changed(w,new BlockPos((c.x<<4)+x,y0+y,(c.z<<4)+z));}
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload e){if(e.getWorld().isRemote)return;PhysicalNetwork d=get(e.getWorld());Chunk c=e.getChunk();Set<BlockPos> queued=pending.get(e.getWorld());if(queued!=null){List<BlockPos> local=new ArrayList<>();for(BlockPos x:queued)if((x.getX()>>4)==c.x&&(x.getZ()>>4)==c.z)local.add(x);queued.removeAll(local);for(BlockPos x:local)if(e.getWorld().isBlockLoaded(x))d.refresh(e.getWorld(),x);queued.removeAll(local);}d.verified.removeIf(p->inChunk(p,c));}
    @SubscribeEvent public static void worldUnload(WorldEvent.Unload e){pending.remove(e.getWorld());}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){NBTTagList list=new NBTTagList();for(Map.Entry<PhysicalTopology.Pos,PhysicalTopology.Cell> entry:topology.cells().entrySet()){NBTTagCompound x=new NBTTagCompound();x.setLong("Pos",block(entry.getKey()).toLong());x.setString("Kind",entry.getValue().kind.name());NBTTagList devices=new NBTTagList();for(PhysicalTopology.Device d:entry.getValue().devices){NBTTagCompound t=new NBTTagCompound();t.setUniqueId("Id",d.id);if(d.parent!=null)t.setUniqueId("Parent",d.parent);t.setString("Type",d.type.name());t.setString("Name",d.name);t.setString("Model",d.model);t.setInteger("Dimension",d.dimension);t.setInteger("Capacity",d.capacityMbps);t.setInteger("Used",d.usedMbps);t.setBoolean("Powered",d.powered);t.setInteger("Slot",d.slot);t.setInteger("Port",d.port);if(d.patchId!=null)t.setUniqueId("PatchId",d.patchId);t.setInteger("PatchTier",d.patchTier);devices.appendTag(t);}x.setTag("Devices",devices);list.appendTag(x);}n.setTag("Cells",list);NBTTagList ll=new NBTTagList();for(LogicalLinks.Link l:uplinks.all()){NBTTagCompound z=new NBTTagCompound();z.setUniqueId("Id",l.id);z.setUniqueId("From",l.from);z.setUniqueId("To",l.to);z.setInteger("Tier",l.tier);ll.appendTag(z);}n.setTag("Uplinks",ll);return n;}
    @Override public void readFromNBT(NBTTagCompound n){topology.clear();verified.clear();uplinks.clear();NBTTagList ll=n.getTagList("Uplinks",10);for(int k=0;k<ll.tagCount();k++){NBTTagCompound z=ll.getCompoundTagAt(k);if(z.hasUniqueId("Id")&&z.hasUniqueId("From")&&z.hasUniqueId("To")){int tier=z.getInteger("Tier");uplinks.restore(new LogicalLinks.Link(z.getUniqueId("Id"),z.getUniqueId("From"),z.getUniqueId("To"),LogicalLinks.Kind.UPLINK,tier,LogicalLinks.speed(tier),LogicalLinks.State.UNVERIFIED,-1));}}NBTTagList list=n.getTagList("Cells",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound x=list.getCompoundTagAt(i);PhysicalTopology.Pos p=pos(BlockPos.fromLong(x.getLong("Pos")));try{PhysicalTopology.Kind k=PhysicalTopology.Kind.valueOf(x.getString("Kind"));List<PhysicalTopology.Device> devices=new ArrayList<>();NBTTagList dl=x.getTagList("Devices",10);for(int j=0;j<dl.tagCount();j++){NBTTagCompound t=dl.getCompoundTagAt(j);if(!t.hasUniqueId("Id"))continue;try{devices.add(new PhysicalTopology.Device(t.getUniqueId("Id"),PhysicalTopology.Type.valueOf(t.getString("Type")),t.getString("Name"),t.getString("Model"),p,t.getInteger("Dimension"),t.getInteger("Capacity"),t.getInteger("Used"),t.getBoolean("Powered"),t.hasUniqueId("Parent")?t.getUniqueId("Parent"):null,t.getInteger("Slot"),t.getInteger("Port"),t.hasUniqueId("PatchId")?t.getUniqueId("PatchId"):null,t.getInteger("PatchTier")));}catch(IllegalArgumentException ignored){}}
        topology.put(p,new PhysicalTopology.Cell(k,devices));}catch(IllegalArgumentException ignored){}}
    }
}
