package odc;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.world.World;
import net.minecraft.util.ResourceLocation;
import javax.annotation.Nullable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.List;

final class Hardware {
    static final Block RACK=new Blocks.Rack("rack",false), RACK_GLASS=new Blocks.Rack("rack_glass",true),
        ASSEMBLER=new Blocks.Machine("assembler",1), COMPUTER=new Blocks.Machine("computer",2),
        BATTERY=new Blocks.Machine("creative_battery",3), SWITCH=new Blocks.Machine("network_switch",4), STATION=new Blocks.Machine("network_station",5), DATA_CABLE=new Blocks.Cable("data_cable",false), POWER_CABLE=new Blocks.Cable("power_cable",true), FLOOR=new Blocks.Decor("raised_floor"), PANEL=new Blocks.Decor("wall_panel");
    static final Block[] BLOCKS={RACK,RACK_GLASS,ASSEMBLER,COMPUTER,BATTERY,SWITCH,STATION,DATA_CABLE,POWER_CABLE,FLOOR,PANEL};
    static final Item CPU1=item("cpu_basic"), CPU2=item("cpu_pro"), GPU1=item("gpu_basic"), GPU2=item("gpu_pro"),
        CHASSIS1=item("chassis_1u"), CHASSIS2=item("chassis_2u"), CHASSIS4=item("chassis_4u"), CHIP=item("precision_chip"), SERVER=new ServerItem();
    static final Item[] ITEMS={CPU1,CPU2,GPU1,GPU2,CHASSIS1,CHASSIS2,CHASSIS4,CHIP,SERVER};
    private static Item item(String id){return new ComponentItem(id).setRegistryName(ODC.ID,id).setUnlocalizedName(ODC.ID+"."+id).setCreativeTab(CreativeTabs.REDSTONE);}
    static int size(ItemStack s){if(s.isEmpty())return 0;Item i=s.getItem();return i==CHASSIS1?1:i==CHASSIS2?2:i==CHASSIS4?4:0;}
    static int cpu(ItemStack s){return s.isEmpty()?0:s.getItem()==CPU1?1:s.getItem()==CPU2?2:0;}
    static int gpu(ItemStack s){return s.isEmpty()?0:s.getItem()==GPU1?1:s.getItem()==GPU2?2:0;}
    static ItemStack build(int u,int cpu,int basic,int pro){
        ItemStack s=new ItemStack(SERVER);NBTTagCompound n=new NBTTagCompound();n.setInteger("Units",u);n.setInteger("CPU",cpu);n.setInteger("GPU1",basic);n.setInteger("GPU2",pro);s.setTagCompound(n);return s;
    }
    static int field(ItemStack s,String key){return s.hasTagCompound()?s.getTagCompound().getInteger(key):0;}
    static boolean validServer(ItemStack s){if(s.isEmpty()||s.getItem()!=SERVER||s.getCount()!=1)return false;int u=field(s,"Units"),c=field(s,"CPU"),a=field(s,"GPU1"),b=field(s,"GPU2");return (u==1||u==2||u==4)&&(c==1||c==2)&&a>=0&&a<=4&&b>=0&&b<=4&&a+b<=u;}
    static int units(ItemStack s){return validServer(s)?field(s,"Units"):0;}
    static int gpus(ItemStack s){return validServer(s)?field(s,"GPU1")+field(s,"GPU2"):0;}
    static int vram(ItemStack s){return validServer(s)?4*field(s,"GPU1")+16*field(s,"GPU2"):0;}
    static ComputeEngine.Profile profile(ItemStack s,int job){return validServer(s)?ComputeEngine.profile(job,field(s,"CPU"),field(s,"GPU1"),field(s,"GPU2"),ODC.rewardDivisor,ODC.bankRewardMilli,ODC.aiRewardMilli):ComputeEngine.profile(0,0,0,0,1);}
    static int hash(ItemStack s){return !validServer(s)?0:40*field(s,"GPU1")+160*field(s,"GPU2");}
    static int power(ItemStack s){return !validServer(s)?0:8+(field(s,"CPU")==1?8:20)+25*field(s,"GPU1")+85*field(s,"GPU2");}
    private static final class ComponentItem extends Item {
        final String id;
        ComponentItem(String id){this.id=id;}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){
            if(id.equals("cpu_basic"))tips.add("40 CPU CU/s | 8 FE/t");
            if(id.equals("cpu_pro"))tips.add("160 CPU CU/s | 20 FE/t");
            if(id.equals("gpu_basic"))tips.add("40 H/s | 4 GB VRAM | 25 FE/t");
            if(id.equals("gpu_pro")){tips.add("160 H/s | 16 GB VRAM | 85 FE/t");tips.add("AI compatible (>=8 GB per GPU)");}
        }
    }
    private static final class ServerItem extends Item {
        ServerItem(){setRegistryName(ODC.ID,"server");setUnlocalizedName(ODC.ID+".server");setMaxStackSize(1);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){
            if(!validServer(s)){tips.add("Assemble at the Server Assembly Station");return;}
            tips.add(units(s)+"U | CPU T"+field(s,"CPU")+" | "+gpus(s)+" GPU");tips.add(hash(s)+" H/s | "+vram(s)+" GB VRAM");tips.add(profile(s,ComputeEngine.BANK).work+" CPU CU/s | max "+power(s)+" FE/t");
        }
    }
}
