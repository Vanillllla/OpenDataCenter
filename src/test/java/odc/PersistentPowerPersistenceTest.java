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
        PersistentThermal.Server thermal=new PersistentThermal.Server(server,s.owner,rack,0,ChassisSpec.BASIC_1,ServerSpecTest.minimal());thermal.node=0;
        thermal.state.internal=65;thermal.state.air=40;thermal.fault=ComputeEngine.HARDWARE_FAILURE;thermal.broken=1L<<ServerSpec.CPU;
        s.thermal.servers.add(thermal);PersistentThermal.Contour loop=new PersistentThermal.Contour(Collections.singleton(rack),new int[]{0},1,41);s.thermal.contours.add(loop);
        original.pendingThermal.put(server,thermal);
        original.snapshots.put(s.anchor,s);original.faults.put(server,ComputeEngine.NO_POWER);
        NBTTagCompound n=original.writeToNBT(new NBTTagCompound());
        OfflineState restored=new OfflineState();restored.readFromNBT(n);
        OfflineState.Snapshot r=restored.snapshots.get(s.anchor);
        check(r!=null&&r.consumers.equals(s.consumers),"consumer footprint");
        check(r.nodes.get(0).chassis==ChassisSpec.BASIC_1,"stable chassis model restored");
        check(r.groups.get(0).fixed==30&&r.groups.get(0).controller,"fixed power and controller");
        check(r.groups.get(0).sources.get(0).external&&r.groups.get(0).sources.get(0).inputs.contains(input),"external source and input");
        check(restored.faults.get(server)==ComputeEngine.NO_POWER,"saved fault");
        check(r.thermal.servers.size()==1&&r.thermal.servers.get(0).state.internal==65&&r.thermal.servers.get(0).state.air==40,"thermal state round trip");
        check(r.thermal.contours.size()==1&&r.thermal.contours.get(0).loop.temp==41,"contour temperature round trip");
        PersistentThermal.Server pending=restored.pendingThermal.get(server);
        check(pending!=null&&pending.broken==(1L<<ServerSpec.CPU)&&pending.fault==ComputeEngine.HARDWARE_FAILURE&&Arrays.equals(pending.parts,thermal.parts),"pending original model and failure round trip");
        LiquidTemperatures temperatures=new LiquidTemperatures();temperatures.sample(Collections.singleton(rack),25).record(44.5,400);
        LiquidTemperatures recovered=new LiquidTemperatures();recovered.readFromNBT(temperatures.writeToNBT(new NBTTagCompound()));
        check(recovered.sample(Collections.singleton(rack),25).temp==44.5,"live loop world save round trip");
        n.setInteger("PowerSchema",5);restored.readFromNBT(n);
        check(restored.snapshots.isEmpty()&&restored.faults.containsKey(server)&&restored.pendingThermal.containsKey(server),"old snapshot rejected without clearing faults or pending damage");
        System.out.println("PersistentPowerPersistence: passed");
    }
}
