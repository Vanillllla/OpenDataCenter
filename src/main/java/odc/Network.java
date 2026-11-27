package odc;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import java.util.*;

/** Loaded-block topology. Internal switches limit their own rack only; neighboring racks share the outer data fabric. */
final class Network {
    static final int LIMIT=16384;
    static final Comparator<TileEntity> ORDER=Comparator.comparingLong(t->t.getPos().toLong());
    static final class Endpoint {final IEnergyStorage energy;Endpoint(IEnergyStorage e){energy=e;}}
    static final class Domain {
        final List<Tiles.Owned> devices=new ArrayList<>();
        final Set<BlockPos> positions=new HashSet<>();
    }
    static boolean isDataDevice(TileEntity t){return t instanceof Tiles.Rack||t instanceof Tiles.Computer||t instanceof Tiles.Station;}
    static boolean isPowerDevice(TileEntity t){return isDataDevice(t);}
    static boolean live(World w,TileEntity t){return t!=null&&!t.isInvalid()&&w.isBlockLoaded(t.getPos())&&w.getTileEntity(t.getPos())==t;}
    static boolean dataNode(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return false;
        Block b=w.getBlockState(p).getBlock();
        if(b==Hardware.DATA_CABLE)return true;
        if(b!=Hardware.RACK&&b!=Hardware.RACK_GLASS&&b!=Hardware.COMPUTER&&b!=Hardware.STATION&&b!=Hardware.SATELLITE)return false;
        TileEntity t=w.getTileEntity(p);return isDataDevice(t);
    }
    /** Null means the safety limit was exceeded. Never split a partially scanned domain and award duplicate capacity. */
    static Domain scan(World w,BlockPos origin){
        Domain out=new Domain();Set<BlockPos> component=TopologySearch.component(origin,p->{List<BlockPos> adjacent=new ArrayList<>(6);for(EnumFacing f:EnumFacing.VALUES)adjacent.add(p.offset(f));return adjacent;},p->dataNode(w,p),LIMIT);
        if(component==null)return null;
        out.positions.addAll(component);
        for(BlockPos p:component){TileEntity t=w.getTileEntity(p);if(isDataDevice(t)&&live(w,t)&&(!(t instanceof Tiles.Rack)||((Tiles.Rack)t).role!=-1))out.devices.add((Tiles.Owned)t);}
        out.devices.sort(ORDER);return out;
    }
    static List<Domain> domains(World w,List<TileEntity> loaded){
        List<Domain> out=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();
        for(TileEntity t:loaded){if(!isDataDevice(t)||!live(w,t)||seen.contains(t.getPos()))continue;
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
        for(TileEntity t:loaded)if(isPowerDevice(t)&&live(w,t)){IEnergyStorage storage=t.getCapability(CapabilityEnergy.ENERGY,null);if(storage instanceof Tiles.Buffer&&!reachable.contains(storage))((Tiles.Buffer)storage).lastFeed=Long.MIN_VALUE;}
    }
    /** Internal creative power network. Discover each receiving device only once per battery. */
    static List<Endpoint> power(World w,BlockPos origin){
        List<Endpoint> out=new ArrayList<>();Set<BlockPos> seen=new HashSet<>(),ends=new HashSet<>();Set<IEnergyStorage> unique=Collections.newSetFromMap(new IdentityHashMap<IEnergyStorage,Boolean>());ArrayDeque<BlockPos> q=new ArrayDeque<>();seen.add(origin);q.add(origin);
        while(!q.isEmpty()){
            BlockPos p=q.removeFirst();for(EnumFacing f:EnumFacing.VALUES){BlockPos n=p.offset(f);if(!w.isBlockLoaded(n)||n.equals(origin))continue;
                Block b=w.getBlockState(n).getBlock();
                if(b==Hardware.POWER_CABLE||b==Hardware.RELAY&&!w.isBlockPowered(n)){if(seen.add(n)){if(seen.size()>LIMIT)return Collections.emptyList();q.addLast(n);}}
                else if(ends.add(n)){TileEntity t=w.getTileEntity(n);if(isPowerDevice(t)){IEnergyStorage e=t.getCapability(CapabilityEnergy.ENERGY,f.getOpposite());if(e!=null&&e.canReceive()&&unique.add(e))out.add(new Endpoint(e));}}
            }
        }
        return out;
    }
}
