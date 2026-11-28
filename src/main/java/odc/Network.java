package odc;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Legacy loaded-block traffic topology (until Stage 4/7). PhysicalNetwork is the new independent discovery index. Internal switches limit their own rack only; neighboring racks share the outer data fabric. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ODC.ID)
public final class Network {
    static final Map<World,Cache> caches=new HashMap<>();
    static final class Cache {final Map<BlockPos,Domain> data=new HashMap<>();PowerGrid.Topology power;}
    static Cache cache(World w){return caches.computeIfAbsent(w,k->new Cache());}
    static void invalidate(World w){if(w!=null&&!w.isRemote)caches.remove(w);}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void load(net.minecraftforge.event.world.ChunkEvent.Load e){invalidate(e.getWorld());}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.ChunkEvent.Unload e){invalidate(e.getWorld());}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void worldUnload(net.minecraftforge.event.world.WorldEvent.Unload e){caches.remove(e.getWorld());}
    static final int LIMIT=16384;
    static final Comparator<TileEntity> ORDER=Comparator.comparingLong(t->t.getPos().toLong());
    static final class Domain {
        final List<Tiles.Owned> devices=new ArrayList<>();
        final Set<BlockPos> positions=new HashSet<>();
    }
    static boolean isDataDevice(TileEntity t){return t instanceof Tiles.Rack||t instanceof Tiles.Computer||t instanceof Tiles.Station||t instanceof Tiles.NetworkTerminal;}
    static boolean isLanDevice(TileEntity t){return isDataDevice(t)&&!(t instanceof Tiles.Station);}
    static boolean isPowerDevice(TileEntity t){return isDataDevice(t);}
    static boolean live(World w,TileEntity t){return t!=null&&!t.isInvalid()&&w.isBlockLoaded(t.getPos())&&w.getTileEntity(t.getPos())==t;}
    static TileEntity deviceAt(World w,BlockPos p){return w.getTileEntity(Blocks.Terminal.base(w,p));}
    static boolean dataNode(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return false;
        Block b=w.getBlockState(p).getBlock();
        if(b==Hardware.DATA_CABLE)return true;
        if(b!=Hardware.RACK&&b!=Hardware.RACK_GLASS&&b!=Hardware.COMPUTER&&!Hardware.networkTerminal(b))return false;
        TileEntity t=deviceAt(w,p);return isLanDevice(t);
    }
    /** Null means the safety limit was exceeded. Never split a partially scanned domain and award duplicate capacity. */
    static Domain scan(World w,BlockPos origin){Cache c=cache(w);if(c.data.containsKey(origin))return c.data.get(origin);Domain d=scanFresh(w,origin);if(d==null)c.data.put(origin,null);else for(BlockPos p:d.positions)c.data.put(p,d);return d;}
    private static Domain scanFresh(World w,BlockPos origin){
        Domain out=new Domain();Set<BlockPos> component=TopologySearch.component(origin,p->{List<BlockPos> adjacent=new ArrayList<>(6);for(EnumFacing f:EnumFacing.VALUES)adjacent.add(p.offset(f));return adjacent;},p->dataNode(w,p),LIMIT);
        if(component==null)return null;
        out.positions.addAll(component);
        for(BlockPos p:component){TileEntity t=w.getTileEntity(p);if(isLanDevice(t)&&live(w,t)&&(!(t instanceof Tiles.Rack)||((Tiles.Rack)t).role!=-1))out.devices.add((Tiles.Owned)t);}
        out.devices.sort(ORDER);return out;
    }
    static List<Domain> domains(World w,List<TileEntity> loaded){
        List<Domain> out=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();
        for(TileEntity t:loaded){if(!isLanDevice(t)||!live(w,t)||seen.contains(t.getPos()))continue;
            Domain d=scan(w,t.getPos());if(d==null)continue;seen.addAll(d.positions);out.add(d);
        }
        return out;
    }
    static List<Tiles.Computer> controllers(World w,Tiles.Rack r){
        List<Tiles.Computer> out=new ArrayList<>();Domain d=scan(w,r.getPos());if(d==null||r.owner==null)return out;
        for(Tiles.Owned t:d.devices)if(t instanceof Tiles.Computer&&r.owner.equals(t.owner))out.add((Tiles.Computer)t);
        return out;
    }
    static void revokeDisconnected(World w){
        if(!ODC.requireEnergy||w.isRemote)return;
        for(TileEntity t:new ArrayList<>(w.loadedTileEntityList))if(isPowerDevice(t)&&live(w,t)){
            if(t instanceof Tiles.Rack){
                Tiles.Rack r=(Tiles.Rack)t;
                if(r.role!=-1&&!r.energy.fed()&&r.enabledMask!=0)r.energy.tripGrid();
            }else if(t instanceof Tiles.Powered){
                Tiles.Powered p=(Tiles.Powered)t;if(!p.energy.fed())p.setPowered(false);
            }
        }
    }
}
