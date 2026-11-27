package odc;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.util.NonNullList;
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
        CHASSIS1=item("chassis_1u"), CHASSIS2=item("chassis_2u"), CHASSIS4=item("chassis_4u"), CHIP=item("precision_chip"), BOARD1=item("board_basic"), BOARD2=item("board_pro"),
        RAM1=item("ram_basic"), RAM2=item("ram_pro"), DISK1=item("hdd_basic"), DISK2=item("ssd_pro"), SERVER=new ServerItem();
    static final Item[] ITEMS={CPU1,CPU2,GPU1,GPU2,CHASSIS1,CHASSIS2,CHASSIS4,CHIP,BOARD1,BOARD2,RAM1,RAM2,DISK1,DISK2,SERVER};
    private static Item item(String id){return new ComponentItem(id).setRegistryName(ODC.ID,id).setTranslationKey(ODC.ID+"."+id).setCreativeTab(CreativeTabs.REDSTONE);}
    static int size(ItemStack s){if(s.isEmpty())return 0;Item i=s.getItem();return i==CHASSIS1?1:i==CHASSIS2?2:i==CHASSIS4?4:0;}
    static int cpu(ItemStack s){return s.isEmpty()?0:s.getItem()==CPU1?1:s.getItem()==CPU2?2:0;}
    static int gpu(ItemStack s){return s.isEmpty()?0:s.getItem()==GPU1?1:s.getItem()==GPU2?2:0;}
    static int tier(ItemStack s,Item a,Item b){if(s.isEmpty())return 0;if(s.getCount()!=1)return -1;return s.getItem()==a?1:s.getItem()==b?2:-1;}
    static int partTier(int slot,ItemStack s){
        if(slot==ServerSpec.BOARD)return tier(s,BOARD1,BOARD2);
        if(slot==ServerSpec.CPU)return tier(s,CPU1,CPU2);
        if(slot>=ServerSpec.RAM&&slot<ServerSpec.DISK)return tier(s,RAM1,RAM2);
        if(slot>=ServerSpec.DISK&&slot<ServerSpec.GPU)return tier(s,DISK1,DISK2);
        if(slot>=ServerSpec.GPU&&slot<ServerSpec.COUNT)return tier(s,GPU1,GPU2);
        return -1;
    }
    static boolean accepts(int slot,ItemStack s){if(s.isEmpty())return false;ItemStack one=s.copy();one.setCount(1);return partTier(slot,one)>0;}
    static NonNullList<ItemStack> emptyParts(){return NonNullList.withSize(ServerSpec.COUNT,ItemStack.EMPTY);}
    static NonNullList<ItemStack> parts(ItemStack server){
        NonNullList<ItemStack> p=emptyParts();if(!validServer(server))return p;
        NBTTagCompound n=server.getTagCompound();
        if(n.hasKey("Hardware",10)){ItemStackHelper.loadAllItems(n.getCompoundTag("Hardware"),p);return p;}
        // 0.1/0.2: retain paid CPU and GPUs, never invent mandatory components.
        int c=n.getInteger("CPU"),a=n.getInteger("GPU1"),b=n.getInteger("GPU2");
        if(c==1||c==2)p.set(ServerSpec.CPU,new ItemStack(c==1?CPU1:CPU2));
        int at=ServerSpec.GPU;for(int i=0;i<Math.min(4,Math.max(0,a))&&at<ServerSpec.COUNT;i++)p.set(at++,new ItemStack(GPU1));
        for(int i=0;i<Math.min(4,Math.max(0,b))&&at<ServerSpec.COUNT;i++)p.set(at++,new ItemStack(GPU2));
        return p;
    }
    static int[] tiers(List<ItemStack> parts){int[] p=new int[ServerSpec.COUNT];for(int i=0;i<p.length;i++)p[i]=partTier(i,parts.get(i));return p;}
    static ItemStack build(int u,NonNullList<ItemStack> parts){ItemStack s=new ItemStack(SERVER);NBTTagCompound n=new NBTTagCompound();n.setInteger("Units",u);s.setTagCompound(n);writeParts(s,parts);return s;}
    static void writeParts(ItemStack s,NonNullList<ItemStack> parts){NBTTagCompound n=s.getTagCompound();NBTTagCompound h=new NBTTagCompound();ItemStackHelper.saveAllItems(h,parts);n.setTag("Hardware",h);n.removeTag("CPU");n.removeTag("GPU1");n.removeTag("GPU2");}
    static int field(ItemStack s,String key){return s.hasTagCompound()?s.getTagCompound().getInteger(key):0;}
    /** Physical server validity, not readiness. Empty chassis still occupies rack units. */
    static boolean validServer(ItemStack s){return !s.isEmpty()&&s.getItem()==SERVER&&s.getCount()==1&&ServerSpec.chassis(field(s,"Units"));}
    static int units(ItemStack s){return validServer(s)?field(s,"Units"):0;}
    static int state(ItemStack s){return ServerSpec.state(units(s),tiers(parts(s)));}
    static int gpus(ItemStack s){int[] p=tiers(parts(s));return ServerSpec.gpuCount(p,1)+ServerSpec.gpuCount(p,2);}
    static int vram(ItemStack s){int[] p=tiers(parts(s));return 4*ServerSpec.gpuCount(p,1)+16*ServerSpec.gpuCount(p,2);}
    static ComputeEngine.Profile profile(ItemStack s,int job){int[] p=tiers(parts(s));return ServerSpec.state(units(s),p)==ServerSpec.READY?ComputeEngine.profile(job,p[ServerSpec.CPU],ServerSpec.gpuCount(p,1),ServerSpec.gpuCount(p,2),ODC.rewardDivisor,ODC.bankRewardMilli,ODC.aiRewardMilli):ComputeEngine.profile(0,0,0,0,1);}
    static int hash(ItemStack s){int[] p=tiers(parts(s));return 40*ServerSpec.gpuCount(p,1)+160*ServerSpec.gpuCount(p,2);}
    static int power(ItemStack s){int[] p=tiers(parts(s));return ServerSpec.state(units(s),p)!=ServerSpec.READY?0:8+(p[1]==1?8:20)+25*ServerSpec.gpuCount(p,1)+85*ServerSpec.gpuCount(p,2);}
    private static final class ComponentItem extends Item {
        final String id;
        ComponentItem(String id){this.id=id;}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){
            if(id.equals("board_basic"))tips.add("2 RAM / 1 drive / 1 GPU (max)");
            if(id.equals("board_pro"))tips.add("4 RAM / 2 drives / 4 GPU (max)");
            if(id.equals("ram_basic"))tips.add("4 GB | 1600 MHz");
            if(id.equals("ram_pro"))tips.add("16 GB | 3200 MHz");
            if(id.equals("hdd_basic"))tips.add("128 GB HDD");
            if(id.equals("ssd_pro"))tips.add("512 GB SSD");
            if(id.equals("cpu_basic"))tips.add("40 CPU CU/s | 8 FE/t");
            if(id.equals("cpu_pro"))tips.add("160 CPU CU/s | 20 FE/t");
            if(id.equals("gpu_basic"))tips.add("40 H/s | 4 GB VRAM | 25 FE/t");
            if(id.equals("gpu_pro")){tips.add("160 H/s | 16 GB VRAM | 85 FE/t");tips.add("AI compatible (>=8 GB per GPU)");}
        }
    }
    private static final class ServerItem extends Item {
        ServerItem(){setRegistryName(ODC.ID,"server");setTranslationKey(ODC.ID+".server");setMaxStackSize(1);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){
            if(!validServer(s)){tips.add("Assemble at the Server Assembly Station");return;}
            int[] p=tiers(parts(s));tips.add(units(s)+"U | CPU T"+p[1]+" | "+gpus(s)+" GPU");
            tips.add(ServerSpec.ramGB(p)+" GB RAM | "+ServerSpec.diskGB(p)+" GB disk");tips.add(hash(s)+" H/s | "+vram(s)+" GB VRAM");
            tips.add(net.minecraft.client.resources.I18n.format("gui.odc.boot."+state(s)));
        }
    }
}
