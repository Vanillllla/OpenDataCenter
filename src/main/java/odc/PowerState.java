package odc;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/** Dimension-local saved electrical latches and last verified footprints.
 * Never force-load chunks. An incomplete previously known grid cannot be reset. */
public final class PowerState extends WorldSavedData {
    private static final String KEY="odc_power_protection";
    final GridProtection protection=new GridProtection();
    final List<Set<BlockPos>> known=new ArrayList<>();
    public PowerState(){super(KEY);}
    public PowerState(String name){super(name);}
    static PowerState get(World w){
        net.minecraft.world.storage.MapStorage storage=w.getPerWorldStorage();
        PowerState state=(PowerState)storage.getOrLoadData(PowerState.class,KEY);
        if(state==null){state=new PowerState();storage.setData(KEY,state);}return state;
    }
    static Set<Long> keys(Collection<BlockPos> cells){Set<Long> out=new HashSet<>();for(BlockPos p:cells)out.add(p.toLong());return out;}
    int state(Collection<BlockPos> cells){return protection.state(keys(cells));}
    void set(Collection<BlockPos> cells,int value){if(protection.set(keys(cells),value))markDirty();}
    Set<BlockPos> footprint(World w,Set<BlockPos> current){
        Set<BlockPos> out=new HashSet<>(current);
        boolean repeat;do{repeat=false;for(Set<BlockPos> previous:known){
            if(Collections.disjoint(out,previous))continue;
            boolean partial=false;for(BlockPos p:previous)if(!w.isBlockLoaded(p)){partial=true;break;}
            if(partial)repeat|=out.addAll(previous);
        }}while(repeat);return out;
    }
    void remember(Set<BlockPos> current){
        for(Set<BlockPos> old:known)if(old.equals(current))return;
        known.removeIf(old->!Collections.disjoint(old,current));
        known.add(new HashSet<>(current));markDirty();
    }
    /** Physical edits invalidate old footprints; cell latches of remaining blocks survive. */
    static void changed(World w,BlockPos p,boolean replaced){
        if(w==null||w.isRemote)return;PowerState s=get(w);
        boolean dirty=s.known.removeIf(k->k.contains(p));
        if(replaced)dirty|=s.protection.forget(p.toLong());
        if(dirty)s.markDirty();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){
        NBTTagList marks=new NBTTagList();for(Map.Entry<Long,Integer> e:protection.snapshot().entrySet()){
            NBTTagCompound x=new NBTTagCompound();x.setLong("Pos",e.getKey());x.setInteger("State",e.getValue());marks.appendTag(x);
        }n.setTag("Latches",marks);
        NBTTagList grids=new NBTTagList();for(Set<BlockPos> cells:known){
            NBTTagCompound x=new NBTTagCompound();NBTTagList pp=new NBTTagList();
            for(BlockPos p:cells){NBTTagCompound q=new NBTTagCompound();q.setLong("Pos",p.toLong());pp.appendTag(q);}x.setTag("Cells",pp);grids.appendTag(x);
        }n.setTag("Grids",grids);return n;
    }
    @Override public void readFromNBT(NBTTagCompound n){
        Map<Long,Integer> saved=new HashMap<>();NBTTagList marks=n.getTagList("Latches",10);
        for(int i=0;i<marks.tagCount();i++){NBTTagCompound x=marks.getCompoundTagAt(i);saved.put(x.getLong("Pos"),x.getInteger("State"));}protection.restore(saved);
        known.clear();NBTTagList grids=n.getTagList("Grids",10);
        for(int i=0;i<grids.tagCount();i++){NBTTagList pp=grids.getCompoundTagAt(i).getTagList("Cells",10);Set<BlockPos> cells=new HashSet<>();
            for(int j=0;j<pp.tagCount();j++)cells.add(BlockPos.fromLong(pp.getCompoundTagAt(j).getLong("Pos")));
            if(!cells.isEmpty())known.add(cells);
        }
    }
}
