package odc;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import java.util.*;

/** Legacy loaded-block traffic topology (until Stage 4/7). PhysicalNetwork is the new independent discovery index. Internal switches limit their own rack only; neighboring racks share the outer data fabric. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ODC.ID)
public final class Network {
    static final Map<World,Cache> caches=new HashMap<>();
    static final class Cache {final Map<BlockPos,Domain> data=new HashMap<>();final Map<BlockPos,PowerPath> power=new HashMap<>();}
    static final class PowerPath {final Set<BlockPos> trace=new HashSet<>();List<Endpoint> endpoints;}
    static Cache cache(World w){return caches.computeIfAbsent(w,k->new Cache());}
    static void invalidate(World w){if(w!=null&&!w.isRemote)caches.remove(w);}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void load(net.minecraftforge.event.world.ChunkEvent.Load e){invalidate(e.getWorld());}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.ChunkEvent.Unload e){invalidate(e.getWorld());}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void worldUnload(net.minecraftforge.event.world.WorldEvent.Unload e){caches.remove(e.getWorld());}
    static final int LIMIT=16384;
    static final Comparator<TileEntity> ORDER=Comparator.comparingLong(t->t.getPos().toLong());
    static final class Endpoint {final IEnergyStorage energy;Endpoint(IEnergyStorage e){energy=e;}}
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
        // Transitional old WAN: a directly attached old station can feed one loaded LAN only.
        // It is a leaf, never a bridge between LANs. Fiber WAN is not credited until Stage 4/7.
        Set<BlockPos> stationEnds=new HashSet<>();for(BlockPos p:component)for(EnumFacing f:EnumFacing.VALUES){BlockPos n=p.offset(f);if(!w.isBlockLoaded(n)||!stationEnds.add(n))continue;
            TileEntity t=w.getTileEntity(n);if(!(t instanceof Tiles.Station)||!live(w,t))continue;
            boolean shared=false;for(EnumFacing g:EnumFacing.VALUES){BlockPos q=n.offset(g);if(dataNode(w,q)&&!component.contains(q)){shared=true;break;}}
            if(!shared){out.devices.add((Tiles.Station)t);out.positions.add(n);}
        }
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
        List<TileEntity> loaded=new ArrayList<>(w.loadedTileEntityList);Set<IEnergyStorage> reachable=Collections.newSetFromMap(new IdentityHashMap<IEnergyStorage,Boolean>());
        for(TileEntity t:loaded)if(t instanceof Tiles.Battery&&live(w,t))for(Endpoint e:power(w,t.getPos()))reachable.add(e.energy);
        for(TileEntity t:loaded)if(isPowerDevice(t)&&live(w,t)){IEnergyStorage storage=t.getCapability(CapabilityEnergy.ENERGY,null);if(storage instanceof Tiles.Buffer&&!reachable.contains(storage)){Tiles.Buffer b=(Tiles.Buffer)storage;boolean was=b.lastFeed!=Long.MIN_VALUE;b.lastFeed=Long.MIN_VALUE;if(was&&b.tile instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)b.tile;for(int bay=0;bay<r.capacity();bay++)if(r.isServer(bay)&&(r.enabledMask&(1<<bay))!=0)ClusterScheduler.trip(r,bay,6,TaskData.get(w));}}}
    }
    /** Internal creative power network. Discover each receiving device only once per battery. */
    static List<Endpoint> power(World w,BlockPos origin){return power(w,origin,null);}
    static List<Endpoint> power(World w,BlockPos origin,Set<BlockPos> trace){Cache c=cache(w);PowerPath path=c.power.get(origin);if(path==null){path=new PowerPath();path.endpoints=powerFresh(w,origin,path.trace);c.power.put(origin,path);}if(trace!=null)trace.addAll(path.trace);return path.endpoints;}
    private static List<Endpoint> powerFresh(World w,BlockPos origin,Set<BlockPos> trace){
        List<Endpoint> out=new ArrayList<>();Set<BlockPos> seen=new HashSet<>(),ends=new HashSet<>();Set<IEnergyStorage> unique=Collections.newSetFromMap(new IdentityHashMap<IEnergyStorage,Boolean>());ArrayDeque<BlockPos> q=new ArrayDeque<>();seen.add(origin);q.add(origin);if(trace!=null)trace.add(origin);
        while(!q.isEmpty()){
            BlockPos p=q.removeFirst();for(EnumFacing f:EnumFacing.VALUES){BlockPos n=p.offset(f);if(!w.isBlockLoaded(n)||n.equals(origin))continue;
                Block b=w.getBlockState(n).getBlock();
                if(b==Hardware.POWER_CABLE||b==Hardware.RELAY&&!w.isBlockPowered(n)){if(seen.add(n)){if(seen.size()>LIMIT)return Collections.emptyList();q.addLast(n);if(trace!=null)trace.add(n);}}
                else if(ends.add(n)){TileEntity t=deviceAt(w,n);if(isPowerDevice(t)){IEnergyStorage e=t.getCapability(CapabilityEnergy.ENERGY,f.getOpposite());if(e!=null&&e.canReceive()&&unique.add(e)){out.add(new Endpoint(e));if(trace!=null){trace.add(n);if(e instanceof Tiles.Buffer)trace.add(((Tiles.Buffer)e).tile.getPos());}}}}
            }
        }
        return out;
    }
}
