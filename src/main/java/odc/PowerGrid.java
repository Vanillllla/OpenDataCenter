package odc;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Physical electrical domains, independent of LAN, ownership, and WAN.
 * All consumers share one budget per loaded component and scheduler cycle. */
final class PowerGrid {
    /** Rated capacity is informational; output is the available funded capacity.
     * External source settlement must be integrated before returning nonzero output. */
    interface Source {
        long outputWatts();
        default long ratedWatts(){return outputWatts();}
    }
    static final class Domain {
        final Set<BlockPos> positions;
        final List<TileEntity> consumers=new ArrayList<>();
        final List<Source> sources=new ArrayList<>();
        PowerEngine.Budget budget;
        Domain(Set<BlockPos> p){positions=p;}
        long ratedWatts(){
            long total=0;
            for(Source source:sources)total=PowerEngine.sum(total,Math.max(0,source.ratedWatts()));
            return total;
        }
        void reset(){
            long capacity=0;
            for(Source source:sources)capacity=PowerEngine.sum(capacity,Math.max(0,source.outputWatts()));
            budget=new PowerEngine.Budget(capacity,PowerEngine.TICKS_PER_SECOND);
        }
    }
    static final class Topology {
        final Map<BlockPos,Domain> byPos=new HashMap<>();
        final Set<BlockPos> failed=new HashSet<>();
        final List<Domain> domains=new ArrayList<>();
    }
    static final class Port {
        final TileEntity tile;
        Port(TileEntity t){tile=t;}
        Domain domain(){World w=tile.getWorld();return w==null||w.isRemote?null:PowerGrid.domain(w,tile.getPos());}
        PowerEngine.Budget budget(){Domain d=domain();return d==null?null:d.budget;}
        boolean fed(){PowerEngine.Budget b=budget();return !ODC.requireEnergy||b!=null&&b.capacityWatts>0;}
        boolean useWatts(int watts){PowerEngine.Budget b=budget();return watts>=0&&(!ODC.requireEnergy||b!=null&&b.use(watts));}
        int availableWatts(){PowerEngine.Budget b=budget();return !ODC.requireEnergy?Integer.MAX_VALUE:b==null?0:(int)Math.min(Integer.MAX_VALUE,b.capacityWatts);}
    }
    private PowerGrid(){}
    static void begin(World w){
        Topology t=topology(w);for(Domain d:t.domains)d.reset();
    }
    static Domain domain(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return null;
        return topology(w).byPos.get(canonical(w,p));
    }
    static Topology topology(World w){
        Network.Cache c=Network.cache(w);
        if(c.power==null)c.power=build(w);
        return c.power;
    }
    private static boolean wire(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return false;
        Block b=w.getBlockState(p).getBlock();
        return b==Hardware.POWER_CABLE||b==Hardware.RELAY&&!w.isBlockPowered(p);
    }
    private static BlockPos canonical(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return p;
        BlockPos q=Blocks.Terminal.base(w,p);
        if(!w.isBlockLoaded(q))return p;
        TileEntity t=w.getTileEntity(q);
        if(t instanceof Tiles.Rack){Tiles.Rack r=((Tiles.Rack)t).master();if(r!=null&&Network.live(w,r))return r.getPos();}
        return q;
    }
    private static TileEntity endpoint(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return null;
        TileEntity t=w.getTileEntity(p);
        if(!Network.live(w,t))return null;
        if(t instanceof Tiles.Rack&&((Tiles.Rack)t).role==-1)return null;
        return t instanceof Source||Network.isPowerDevice(t)?t:null;
    }
    private static boolean allowed(World w,BlockPos p){return wire(w,p)||endpoint(w,p)!=null;}
    /** Consumers may share a bus through their own input contacts, but adjacent consumers
     * alone do not form a power cable. Upper rack/terminal cells are aliases, not loads. */
    private static Iterable<BlockPos> neighbors(World w,BlockPos p){
        List<BlockPos> result=new ArrayList<>();
        TileEntity self=endpoint(w,p);
        boolean conductive=wire(w,p)||self instanceof Source;
        List<BlockPos> contacts=new ArrayList<>();contacts.add(p);
        if(self instanceof Tiles.Rack&&((Tiles.Rack)self).role==1||self instanceof Tiles.Computer){
            BlockPos upper=p.up();if(w.isBlockLoaded(upper)&&canonical(w,upper).equals(p))contacts.add(upper);
        }
        for(BlockPos contact:contacts)for(EnumFacing side:EnumFacing.VALUES){
            BlockPos raw=contact.offset(side);
            if(!w.isBlockLoaded(raw))continue;
            BlockPos q=canonical(w,raw);
            if(q.equals(p))continue;
            TileEntity other=endpoint(w,q);
            if((conductive||wire(w,q)||other instanceof Source)&&allowed(w,q))result.add(q);
        }
        return result;
    }
    private static Topology build(World w){
        Topology t=new Topology();
        List<TileEntity> loaded=new ArrayList<>(w.loadedTileEntityList);loaded.sort(Network.ORDER);
        for(TileEntity tile:loaded){
            BlockPos p=tile.getPos();
            if(endpoint(w,p)!=tile||t.byPos.containsKey(p)||t.failed.contains(p))continue;
            Set<BlockPos> cells=TopologySearch.component(p,q->neighbors(w,q),q->allowed(w,q),Network.LIMIT);
            if(cells==null){t.failed.add(p);continue;} // fail closed; never count a partial domain
            Domain d=new Domain(new HashSet<>(cells));
            for(BlockPos q:cells){
                TileEntity device=endpoint(w,q);
                if(device instanceof Source)d.sources.add((Source)device);
                else if(device!=null){
                    d.consumers.add(device);
                    if(device instanceof Tiles.Computer||device instanceof Tiles.Rack&&((Tiles.Rack)device).role==1){
                        BlockPos upper=q.up();if(w.isBlockLoaded(upper)&&canonical(w,upper).equals(q))d.positions.add(upper);
                    }
                }
            }
            d.consumers.sort(Network.ORDER);d.reset();t.domains.add(d);
            for(BlockPos q:d.positions)t.byPos.put(q,d);
        }
        return t;
    }
}
