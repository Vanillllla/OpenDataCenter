package odc;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

/** A broken part remains the same registry item and model, with its own NBT.
 * No replacement item is spawned, no physical slot or binding index changes. */
final class HardwareDamage {
    static final String BROKEN="ODCBroken",FAULT="ThermalFault",TEMPS="ThermalTemperatures";
    private HardwareDamage(){}
    static boolean broken(ItemStack part){
        Part model=Hardware.MODELS.get(part.getItem());
        return !part.isEmpty()&&model!=null&&model.electronic()&&part.hasTagCompound()&&part.getTagCompound().getBoolean(BROKEN);
    }
    static boolean mark(ItemStack part){
        Part model=Hardware.MODELS.get(part.getItem());
        if(part.isEmpty()||model==null||!model.electronic()||broken(part))return false;
        NBTTagCompound tag=part.hasTagCompound()?part.getTagCompound():new NBTTagCompound();
        tag.setBoolean(BROKEN,true);part.setTagCompound(tag);return true;
    }
    static long mask(NonNullList<ItemStack> parts){
        long bits=0;for(int i=0;i<parts.size();i++)if(broken(parts.get(i)))bits|=1L<<i;return bits;
    }
    static long mask(ItemStack chassis){return mask(Hardware.parts(chassis));}
    static int fault(ItemStack item){
        if(!item.hasTagCompound())return 0;
        int f=item.getTagCompound().getInteger(FAULT);
        return f==ComputeEngine.OVERHEAT||f==ComputeEngine.HARDWARE_FAILURE?f:0;
    }
    static void latch(ItemStack item,int reason){
        NBTTagCompound n=item.hasTagCompound()?item.getTagCompound():new NBTTagCompound();
        if(reason==0)n.removeTag(FAULT);else n.setInteger(FAULT,reason);
        item.setTagCompound(n);
    }
    static void checkpoint(ItemStack item,ThermalEngine.State state){
        NBTTagCompound n=item.hasTagCompound()?item.getTagCompound():new NBTTagCompound();
        n.setIntArray(TEMPS,state.snapshot());item.setTagCompound(n);
    }
    static void restore(ItemStack item,ThermalEngine.State state){
        if(item.hasTagCompound())state.restore(item.getTagCompound().getIntArray(TEMPS),ODC.ambientTemperature);
    }
}
