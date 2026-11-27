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
        if(b==Hardware.NETWORK_TERMINAL)return PhysicalTopology.Kind.TERMINAL;
        if(b==Hardware.STATION||b==Hardware.SATELLITE)return PhysicalTopology.Kind.ANTENNA;
        return null;
    }
    private static PhysicalTopology.Device device(World w,BlockPos p,UUID id,PhysicalTopology.Type type,String name,String model,int capacity,int used,boolean powered,UUID parent,int slot,int port){return new PhysicalTopology.Device(id,type,name,model,pos(p),w.provider.getDimension(),capacity,used,powered,parent,slot,port);}
    private void refresh(World w,BlockPos p){
        if(!w.isBlockLoaded(p)){verified.remove(pos(p));return;} // never erase an unloaded block
        PhysicalTopology.Pos key=pos(p);PhysicalTopology.Kind k=kind(w.getBlockState(p).getBlock());
        if(k==null){verified.remove(key);if(topology.put(key,null))markDirty();return;}
        List<PhysicalTopology.Device> devices=new ArrayList<>();TileEntity tile=w.getTileEntity(p);
        if(k==PhysicalTopology.Kind.RACK){
            if(!(tile instanceof Tiles.Rack)||tile.isInvalid()){verified.remove(key);changed(w,p);return;}
            Tiles.Rack r=(Tiles.Rack)tile;
            if(r.role!=-1){int bay=r.switchBay(),tier=r.switchTier();if(bay>=0&&tier>0){
                ItemStack sw=r.slots.get(bay);boolean had=sw.hasTagCompound()&&sw.getTagCompound().hasUniqueId("SwitchId");UUID id=Hardware.switchId(sw);if(!had)r.markDirty();
                int used=0;for(int port=0;port<Hardware.ports(tier);port++){int b=r.ports[port]-1;if(r.isServer(b))used+=Math.max(0,r.netUsed[b]);}
                devices.add(device(w,p,id,PhysicalTopology.Type.SWITCH,sw.getDisplayName(),sw.getItem().getRegistryName().toString(),Hardware.switchMbps(tier),used,r.switchOnline,null,bay,-1));
                for(int port=0;port<Hardware.ports(tier);port++){int b=r.ports[port]-1;if(!r.isServer(b)||Hardware.nic(r.slots.get(b))<=0)continue;ItemStack s=r.slots.get(b);boolean identified=s.hasTagCompound()&&s.getTagCompound().hasUniqueId("ServerId");UUID server=ClusterScheduler.serverId(s);if(!identified)r.markDirty();
                    devices.add(device(w,p,server,PhysicalTopology.Type.SERVER,s.getDisplayName(),s.getItem().getRegistryName().toString(),Hardware.nic(s),r.netUsed[b],(r.poweredMask&(1<<b))!=0&&r.switchOnline,id,b,port));
                }
            }}
        }else if(k==PhysicalTopology.Kind.ANTENNA){
            if(!(tile instanceof Tiles.Station)||tile.isInvalid()){verified.remove(key);changed(w,p);return;}
            Tiles.Station a=(Tiles.Station)tile;boolean sat=a.satellite();devices.add(device(w,p,a.networkId,sat?PhysicalTopology.Type.SATELLITE:PhysicalTopology.Type.RADIO,sat?"Satellite":"Radio antenna",w.getBlockState(p).getBlock().getRegistryName().toString(),a.bandwidth(),-1,a.online,null,-1,-1));
        }else if(k==PhysicalTopology.Kind.TERMINAL){
            if(!(tile instanceof Tiles.NetworkTerminal)||tile.isInvalid()){verified.remove(key);changed(w,p);return;}
            Tiles.NetworkTerminal t=(Tiles.NetworkTerminal)tile;devices.add(device(w,p,t.networkId,PhysicalTopology.Type.TERMINAL,"Network terminal", "T1 (foundation)",t.bandwidth(),-1,t.online,null,-1,-1));
        }
        // DataOS upper blocks and rack upper halves remain physical LAN cells, not duplicate devices.
        if(topology.put(key,new PhysicalTopology.Cell(k,devices)))markDirty();verified.add(key);
    }
    private void flush(World w){Set<BlockPos> work=pending.remove(w);if(work!=null)for(BlockPos p:work)refresh(w,p);}
    public static PhysicalTopology.View view(World w,BlockPos origin){PhysicalNetwork d=get(w);d.flush(w);return d.topology.view(pos(origin),p->d.verified.contains(p)&&w.isBlockLoaded(block(p)));}
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
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){NBTTagList list=new NBTTagList();for(Map.Entry<PhysicalTopology.Pos,PhysicalTopology.Cell> entry:topology.cells().entrySet()){NBTTagCompound x=new NBTTagCompound();x.setLong("Pos",block(entry.getKey()).toLong());x.setString("Kind",entry.getValue().kind.name());NBTTagList devices=new NBTTagList();for(PhysicalTopology.Device d:entry.getValue().devices){NBTTagCompound t=new NBTTagCompound();t.setUniqueId("Id",d.id);if(d.parent!=null)t.setUniqueId("Parent",d.parent);t.setString("Type",d.type.name());t.setString("Name",d.name);t.setString("Model",d.model);t.setInteger("Dimension",d.dimension);t.setInteger("Capacity",d.capacityMbps);t.setInteger("Used",d.usedMbps);t.setBoolean("Powered",d.powered);t.setInteger("Slot",d.slot);t.setInteger("Port",d.port);devices.appendTag(t);}x.setTag("Devices",devices);list.appendTag(x);}n.setTag("Cells",list);return n;}
    @Override public void readFromNBT(NBTTagCompound n){topology.clear();verified.clear();NBTTagList list=n.getTagList("Cells",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound x=list.getCompoundTagAt(i);PhysicalTopology.Pos p=pos(BlockPos.fromLong(x.getLong("Pos")));try{PhysicalTopology.Kind k=PhysicalTopology.Kind.valueOf(x.getString("Kind"));List<PhysicalTopology.Device> devices=new ArrayList<>();NBTTagList dl=x.getTagList("Devices",10);for(int j=0;j<dl.tagCount();j++){NBTTagCompound t=dl.getCompoundTagAt(j);if(!t.hasUniqueId("Id"))continue;try{devices.add(new PhysicalTopology.Device(t.getUniqueId("Id"),PhysicalTopology.Type.valueOf(t.getString("Type")),t.getString("Name"),t.getString("Model"),p,t.getInteger("Dimension"),t.getInteger("Capacity"),t.getInteger("Used"),t.getBoolean("Powered"),t.hasUniqueId("Parent")?t.getUniqueId("Parent"):null,t.getInteger("Slot"),t.getInteger("Port")));}catch(IllegalArgumentException ignored){}}
        topology.put(p,new PhysicalTopology.Cell(k,devices));}catch(IllegalArgumentException ignored){}}
    }
}
