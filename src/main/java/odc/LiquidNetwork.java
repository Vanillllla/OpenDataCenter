package odc;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Independent physical liquid graph. A rack's rear contacts are aliases
 * of one collector, not additional consumers. No LAN or power graph edges. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class LiquidNetwork {
    private LiquidNetwork(){}
    static final class Contour {
        final Set<BlockPos> cells;
        final List<Tiles.Rack> racks=new ArrayList<>();
        final List<Tiles.Chiller> chillers=new ArrayList<>();
        LiquidCore.Loop loop;
        Contour(Set<BlockPos> cells){this.cells=cells;}
    }
    private static final class Topology {
        final Map<BlockPos,Contour> byPos=new HashMap<>();
        final List<Contour> contours=new ArrayList<>();
        boolean dirty;
    }
    private static final Map<World,Topology> caches=new IdentityHashMap<>();
    static void invalidate(World w){
        if(w!=null&&!w.isRemote){Topology old=caches.get(w);if(old!=null)old.dirty=true;}
    }
    @SubscribeEvent public static void load(ChunkEvent.Load e){invalidate(e.getWorld());}
    @SubscribeEvent public static void unload(ChunkEvent.Unload e){invalidate(e.getWorld());}
    @SubscribeEvent public static void unloadWorld(WorldEvent.Unload e){caches.remove(e.getWorld());}
    private static boolean loaded(IBlockAccess w,BlockPos p){return !(w instanceof World)||((World)w).isBlockLoaded(p);}
    private static Tiles.Rack rack(IBlockAccess w,BlockPos p){
        if(!loaded(w,p))return null;
        TileEntity t=w.getTileEntity(p);if(!(t instanceof Tiles.Rack)||t.isInvalid())return null;
        Tiles.Rack r=(Tiles.Rack)t;
        if(r.role==-1){
            if(!loaded(w,p.down()))return null;
            TileEntity base=w.getTileEntity(p.down());
            if(!(base instanceof Tiles.Rack)||base.isInvalid()||((Tiles.Rack)base).role!=1)return null;
            r=(Tiles.Rack)base;
        }
        return r;
    }
    static boolean pipeContact(IBlockAccess w,BlockPos p,EnumFacing towardPipe){
        if(!loaded(w,p))return false;
        IBlockState state=w.getBlockState(p);Block b=state.getBlock();
        if(b==Hardware.LIQUID_PIPE)return true;
        if(b instanceof Blocks.Chiller)return true;
        if(b instanceof Blocks.Rack){
            Tiles.Rack r=rack(w,p);
            return r!=null&&r.manifold&&state.getValue(Blocks.FACING).getOpposite()==towardPipe;
        }
        return false;
    }
    private static boolean pipe(World w,BlockPos p){return w.isBlockLoaded(p)&&w.getBlockState(p).getBlock()==Hardware.LIQUID_PIPE;}
    private static boolean node(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return false;
        Block b=w.getBlockState(p).getBlock();
        if(b==Hardware.LIQUID_PIPE)return true;
        if(b instanceof Blocks.Chiller){TileEntity t=w.getTileEntity(p);return t instanceof Tiles.Chiller&&Network.live(w,t);}
        if(b instanceof Blocks.Rack){Tiles.Rack r=rack(w,p);return r!=null&&r.manifold&&Network.live(w,r);}
        return false;
    }
    private static Iterable<BlockPos> neighbors(World w,BlockPos p){
        List<BlockPos> out=new ArrayList<>(7);
        boolean isPipe=pipe(w,p);
        for(EnumFacing side:EnumFacing.VALUES){
            BlockPos q=p.offset(side);
            if(!w.isBlockLoaded(q))continue;
            // Only a pipe carries between distinct devices. Both halves of
            // a tall rack share one internal collector, independently of U.
            if(isPipe){if(pipeContact(w,q,side.getOpposite()))out.add(q);}
            else if(pipe(w,q)&&pipeContact(w,p,side))out.add(q);
        }
        Tiles.Rack r=rack(w,p);
        if(r!=null&&r.manifold&&r.role==1){
            BlockPos other=p.equals(r.getPos())?p.up():r.getPos();
            if(rack(w,other)==r)out.add(other);
        }
        return out;
    }
    private static Topology topology(World w){
        Topology old=caches.get(w);if(old!=null&&!old.dirty)return old;
        Topology result=new Topology();
        Map<Set<BlockPos>,Contour> previous=new HashMap<>();
        if(old!=null)for(Contour c:old.contours)previous.put(c.cells,c);
        List<TileEntity> loaded=new ArrayList<>(w.loadedTileEntityList);loaded.sort(Network.ORDER);
        for(TileEntity tile:loaded){
            if(!(tile instanceof Tiles.Rack||tile instanceof Tiles.Chiller)||!Network.live(w,tile))continue;
            if(tile instanceof Tiles.Rack&&(((Tiles.Rack)tile).role==-1||!((Tiles.Rack)tile).manifold))continue;
            BlockPos p=tile.getPos();
            if(result.byPos.containsKey(p)||!node(w,p))continue;
            Set<BlockPos> cells=TopologySearch.component(p,q->neighbors(w,q),q->node(w,q),Network.LIMIT);
            // Fail closed for oversized topology rather than awarding a
            // partial contour's capacity more than once.
            if(cells==null){result=new Topology();break;}
            Contour c=new Contour(cells);
            for(BlockPos q:cells){
                TileEntity t=w.getTileEntity(q);
                if(t instanceof Tiles.Rack&&((Tiles.Rack)t).role!=-1)c.racks.add((Tiles.Rack)t);
                else if(t instanceof Tiles.Chiller)c.chillers.add((Tiles.Chiller)t);
            }
            c.racks.sort(Network.ORDER);c.chillers.sort(Network.ORDER);
            Contour prior=previous.get(cells);
            c.loop=prior!=null&&prior.racks.equals(c.racks)&&prior.chillers.equals(c.chillers)?prior.loop:new LiquidCore.Loop(c.racks.size()+c.chillers.size(),ODC.ambientTemperature);
            result.contours.add(c);for(BlockPos q:cells)result.byPos.put(q,c);
        }
        caches.put(w,result);return result;
    }
    static Contour contour(World w,BlockPos p){return w==null||w.isRemote?null:topology(w).byPos.get(p);}
    static void step(World w,List<Tiles.Rack> racks,long tick){
        Topology t=topology(w);
        for(Contour c:t.contours){
            double capacity=0;
            if(!c.racks.isEmpty())for(Tiles.Chiller chiller:c.chillers)capacity+=chiller.available(tick);
            c.loop.begin(capacity);
        }
        for(Tiles.Rack rack:racks){
            Contour c=t.byPos.get(rack.getPos());ThermalRuntime.prepare(rack,tick,c==null?null:c.loop);
        }
        for(Contour c:t.contours)c.loop.allocate();
        for(Tiles.Rack rack:racks){
            Contour c=t.byPos.get(rack.getPos());ThermalRuntime.finish(rack,c==null?null:c.loop);
        }
        for(Contour c:t.contours)c.loop.finish(ODC.ambientTemperature);
    }
    /** Install with collector in hand on rear, remove with empty hand + sneak.
     * Owner/reach are enforced by the vanilla block interaction. */
    static boolean interact(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumFacing side){
        ItemStack hand=player.getHeldItem(EnumHand.MAIN_HAND);
        boolean install=hand.getItem()==Hardware.MANIFOLD,remove=hand.isEmpty()&&player.isSneaking();
        if(side!=s.getValue(Blocks.FACING).getOpposite()||!install&&!remove)return false;
        if(w.isRemote)return true;
        Tiles.Rack r=rack(w,p);if(r==null||!r.allows(player)||player.isSpectator())return true;
        if(install&&!r.manifold){
            r.setManifold(true);if(!player.capabilities.isCreativeMode)hand.shrink(1);player.inventory.markDirty();
        }else if(remove&&r.manifold){
            r.setManifold(false);ItemStack item=new ItemStack(Hardware.MANIFOLD);
            if(!player.capabilities.isCreativeMode&&!player.inventory.addItemStackToInventory(item)&&!item.isEmpty())player.dropItem(item,false);
            player.inventory.markDirty();
        }
        return true;
    }
}
