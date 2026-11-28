package odc;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.BitSet;

/** Loaded server adapter. Profile and idle DC are built once per unchanged
 * chassis/Hardware tag. Only the existing scheduler calls step. */
final class ThermalRuntime {
    private ThermalRuntime(){}
    static final class Entry {
        final ItemStack item;
        final NBTTagCompound hardware;
        final ThermalEngine.State state;
        final int[] idle=new int[ServerSpec.COUNT];
        final int idleAC;
        int[] dc;
        int nominalAC;
        long actualAC,tick=Long.MIN_VALUE;
        PowerGrid.Domain domain;
        Entry(ItemStack item,NBTTagCompound hardware){
            this.item=item;this.hardware=hardware;
            int[] parts=Hardware.spec(item);ChassisSpec chassis=Hardware.chassis(item);
            state=new ThermalEngine.State(ServerSpec.thermalProfile(chassis,parts),ODC.ambientTemperature);
            TaskEngine.Node node=new TaskEngine.Node(null,0,0,parts,null,chassis);
            idleAC=node.inputWatts(node.watts(new BitSet(),0,idle));
        }
    }
    static Entry entry(Tiles.Rack rack,int bay){
        ItemStack item=rack.slots.get(bay);
        if(!rack.isServer(bay)){rack.thermal[bay]=null;return null;}
        NBTTagCompound hardware=item.hasTagCompound()?item.getTagCompound().getCompoundTag("Hardware"):null;
        // getCompoundTag returns a fresh empty tag when absent; use null in that case.
        if(item.hasTagCompound()&&!item.getTagCompound().hasKey("Hardware",10))hardware=null;
        Entry e=rack.thermal[bay];
        if(e==null||e.item!=item||e.hardware!=hardware)rack.thermal[bay]=e=new Entry(item,hardware);
        return e;
    }
    static int idleWatts(Tiles.Rack rack,int bay){return entry(rack,bay).idleAC;}
    static boolean reserve(Tiles.Rack rack,int bay,int nominal,int[] dc){
        Entry e=entry(rack,bay);
        PowerGrid.Domain domain=rack.energy.domain();
        if(domain==null||e==null||nominal<0)return false;
        long before=domain.budget.usedUnits();
        if(!domain.use(nominal))return false;
        e.actualAC=ODC.requireEnergy?(domain.budget.usedUnits()-before)/PowerEngine.TICKS_PER_SECOND:PowerEngine.scale(nominal,domain.budget.factor);
        e.nominalAC=nominal;e.dc=dc==null?e.idle:dc;e.tick=rack.getWorld().getTotalWorldTime();e.domain=domain;
        return true;
    }
    static void prepare(Tiles.Rack rack,long tick,LiquidCore.Loop loop){
        for(int bay=0;bay<rack.thermal.length;bay++){
            Entry e=entry(rack,bay);if(e==null)continue;
            boolean powered=(rack.poweredMask&(1<<bay))!=0&&e.tick==tick&&e.domain!=null&&e.domain.funded();
            e.state.prepare(powered?e.dc:null,powered?e.nominalAC:0,powered?e.actualAC:0,ODC.ambientTemperature);
            if(loop!=null&&loop.capacity>0)loop.request(e.state.liquidDemand(loop.temperature()));
            e.dc=null;e.domain=null;
        }
    }
    static void finish(Tiles.Rack rack,LiquidCore.Loop loop){
        for(Entry e:rack.thermal)if(e!=null)e.state.finish(ODC.ambientTemperature,loop);
    }
    static ThermalEngine.State state(Tiles.Rack rack,int bay){Entry e=rack.thermal[bay];return e==null?null:e.state;}
}
