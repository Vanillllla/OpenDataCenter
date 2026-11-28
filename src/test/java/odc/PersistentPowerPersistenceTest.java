package odc;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
public final class PersistentPowerPersistenceTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        OfflineState original=new OfflineState();
        OfflineState.Snapshot s=new OfflineState.Snapshot();s.anchor=UUID.randomUUID();s.owner=UUID.randomUUID();
        BlockPos rack=new BlockPos(33,64,0),source=new BlockPos(0,64,0),input=source.west();
        s.positions.add(rack);s.positions.add(source);s.consumers.add(rack);
        s.caps=new int[]{1000};s.budget=new int[]{200};s.gridByRack=new int[]{0};
        OfflineState.Group g=new OfflineState.Group();g.cells.addAll(s.positions);g.fixed=30;g.controller=true;
        OfflineState.SourceSpec spec=new OfflineState.SourceSpec();spec.pos=source;spec.external=true;spec.watts=5000;spec.inputs.add(input);g.sources.add(spec);s.groups.add(g);
        UUID server=UUID.randomUUID();s.nodes.add(new TaskEngine.Node(server,0,1000,ServerSpecTest.minimal(),PoolResources.id(s.owner,PoolResources.DEFAULT),ChassisSpec.BASIC_1));
        original.snapshots.put(s.anchor,s);original.faults.put(server,ComputeEngine.NO_POWER);
        NBTTagCompound n=original.writeToNBT(new NBTTagCompound());
        OfflineState restored=new OfflineState();restored.readFromNBT(n);
        OfflineState.Snapshot r=restored.snapshots.get(s.anchor);
        check(r!=null&&r.consumers.equals(s.consumers),"consumer footprint");
        check(r.nodes.get(0).chassis==ChassisSpec.BASIC_1,"stable chassis model restored");
        check(r.groups.get(0).fixed==30&&r.groups.get(0).controller,"fixed power and controller");
        check(r.groups.get(0).sources.get(0).external&&r.groups.get(0).sources.get(0).inputs.contains(input),"external source and input");
        check(restored.faults.get(server)==ComputeEngine.NO_POWER,"saved fault");
        n.setInteger("PowerSchema",4);restored.readFromNBT(n);
        check(restored.snapshots.isEmpty()&&restored.faults.containsKey(server),"old snapshot rejected without clearing faults");
        System.out.println("PersistentPowerPersistence: passed");
    }
}
