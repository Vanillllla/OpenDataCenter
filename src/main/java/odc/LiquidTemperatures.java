package odc;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/** Bounded, exact-footprint thermal checkpoint. Handles are cached in the
 * live/virtual contour: no hashing or traversing pipe cells each second. */
public final class LiquidTemperatures extends WorldSavedData {
    static final String KEY="odc_liquid_temperatures_v1";
    static final int MAX_LOOPS=1024,MAX_CELLS=131072;
    private final LinkedHashMap<Set<BlockPos>,Sample> samples=new LinkedHashMap<>(16,.75f,true);
    private int cells;
    public LiquidTemperatures(){super(KEY);}public LiquidTemperatures(String key){super(key);}
    static LiquidTemperatures get(World w){
        net.minecraft.world.storage.MapStorage storage=w.getPerWorldStorage();
        LiquidTemperatures data=(LiquidTemperatures)storage.getOrLoadData(LiquidTemperatures.class,KEY);
        if(data==null){data=new LiquidTemperatures();storage.setData(KEY,data);}return data;
    }
    final class Sample {
        double temp;long tick=Long.MIN_VALUE;
        Sample(double value){temp=ThermalEngine.safe(value,ODC.ambientTemperature);}
        void record(double value,long now){if(tick==now)return;temp=ThermalEngine.safe(value,ODC.ambientTemperature);tick=now;if(now%400==0)markDirty();}
    }
    Sample sample(Set<BlockPos> footprint,double ambient){
        Sample s=samples.get(footprint);if(s!=null)return s;
        s=new Sample(ambient);samples.put(new HashSet<>(footprint),s);cells+=footprint.size();trim();markDirty();return s;
    }
    private void trim(){
        Iterator<Map.Entry<Set<BlockPos>,Sample>> it=samples.entrySet().iterator();
        while((samples.size()>MAX_LOOPS||cells>MAX_CELLS)&&it.hasNext()){cells-=it.next().getKey().size();it.remove();}
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){
        NBTTagList list=new NBTTagList();for(Map.Entry<Set<BlockPos>,Sample> e:samples.entrySet()){
            NBTTagCompound x=new NBTTagCompound();x.setTag("Cells",OfflineState.writePositions(e.getKey()));x.setDouble("Temp",e.getValue().temp);x.setLong("Tick",e.getValue().tick);list.appendTag(x);
        }n.setTag("Loops",list);return n;
    }
    @Override public void readFromNBT(NBTTagCompound n){
        samples.clear();cells=0;NBTTagList list=n.getTagList("Loops",10);
        for(int i=0;i<list.tagCount();i++){NBTTagCompound x=list.getCompoundTagAt(i);Set<BlockPos> footprint=OfflineState.readPositions(x.getTagList("Cells",10));
            if(footprint.isEmpty()||footprint.size()>Network.LIMIT||samples.containsKey(footprint))continue;
            Sample s=new Sample(x.getDouble("Temp"));s.tick=x.getLong("Tick");samples.put(footprint,s);cells+=footprint.size();trim();
        }
    }
}
