package odc;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.*;
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
    static final CreativeTabs TAB=new CreativeTabs("odc"){@Override public ItemStack createIcon(){return new ItemStack(CHASSIS4);}};
    static final Block RACK=new Blocks.Rack("rack",false),RACK_GLASS=new Blocks.Rack("rack_glass",true),WORKBENCH=new Blocks.Machine("workbench",1),COMPUTER=new Blocks.Terminal(),BATTERY=new Blocks.Machine("creative_battery",3),STATION=new Blocks.Machine("radio_station",5),SATELLITE=new Blocks.Machine("satellite_station",6),DATA_CABLE=new Blocks.Cable("data_cable",false,false),POWER_CABLE=new Blocks.Cable("power_cable",true,false),RELAY=new Blocks.Cable("power_relay",true,true),FLOOR=new Blocks.Decor("raised_floor"),PANEL=new Blocks.Decor("wall_panel");
    static final Block[] BLOCKS={RACK,RACK_GLASS,WORKBENCH,COMPUTER,BATTERY,STATION,SATELLITE,DATA_CABLE,POWER_CABLE,RELAY,FLOOR,PANEL};
    static final Item CPU1=item("cpu_basic","4c / 4t | 2.4 GHz | 40 CU/s"),CPU2=item("cpu_pro","8c / 16t | 3.2 GHz | 160 CU/s"),GPU1=item("gpu_basic","40 H/s | 4 GB VRAM | 25 FE/t"),GPU2=item("gpu_pro","160 H/s | 16 GB VRAM | 85 FE/t"),CHASSIS1=new Chassis("chassis_1u",1),CHASSIS2=new Chassis("chassis_2u",2),CHASSIS4=new Chassis("chassis_4u",4),CHIP=item("precision_chip",""),BOARD1=item("board_basic","2 RAM / 2 SATA / 0 NVMe / 1 PCIe"),BOARD2=item("board_pro","4 RAM / 4 SATA / 1 NVMe / 2 PCIe"),BOARD3=item("board_advanced","8 RAM / 8 SATA / 2 NVMe / 4 PCIe / 1000 Mb/s LAN"),RAM1=item("ram_basic","4 GB | 1600 MHz"),RAM2=item("ram_pro","16 GB | 3200 MHz"),RAM3=item("ram_advanced","32 GB | 3600 MHz"),HDD1=item("hdd_basic","512 GB | SATA | 100 MB/s"),HDD2=item("hdd_pro","2048 GB | SATA | 180 MB/s"),SSD1=item("ssd_basic","256 GB | SATA | 450 MB/s"),SSD2=item("ssd_pro","1024 GB | SATA | 550 MB/s"),NVME1=item("nvme_basic","512 GB | NVMe | 3000 MB/s"),NVME2=item("nvme_pro","2048 GB | NVMe | 7000 MB/s"),PSU1=item("psu_basic","250 FE/t"),PSU2=item("psu_pro","500 FE/t"),PSU3=item("psu_advanced","1000 FE/t"),NIC1=item("nic_basic","100 Mb/s"),NIC2=item("nic_pro","1000 Mb/s"),SWITCH1=item("switch_basic","1U | 4 ports | 100 Mb/s per port | 200 Mb/s | 8 FE/t"),SWITCH2=item("switch_pro","1U | 8 ports | 1000 Mb/s per port | 2000 Mb/s | 20 FE/t"),PATCH=item("ethernet_patch","1 internal connection");
    static final Item BOARD4=item("board_dual","2 CPU / 8 RAM / 8 SATA / 2 NVMe / 4 PCIe / 1000 Mb/s LAN"),CPU3=item("cpu_advanced","16c / 32t | 3.6 GHz | 480 CU/s"),RAM4=item("ram_elite","64 GB | 4800 MHz"),GPU3=item("gpu_advanced","480 H/s | 24 GB VRAM | 180 FE/t"),GPU4=item("gpu_elite","1280 H/s | 48 GB VRAM | 350 FE/t"),HDD3=item("hdd_advanced","4096 GB | SATA | 210 MB/s"),HDD4=item("hdd_elite","8192 GB | SATA | 250 MB/s"),SSD3=item("ssd_advanced","2048 GB | SATA | 560 MB/s"),NVME3=item("nvme_advanced","4096 GB | M.2 NVMe | 9000 MB/s"),PCIESSD=item("nvme_pcie","4096 GB | PCIe SSD | 10000 MB/s");
    static final Item[] ITEMS={CPU1,CPU2,GPU1,GPU2,CHASSIS1,CHASSIS2,CHASSIS4,CHIP,BOARD1,BOARD2,BOARD3,RAM1,RAM2,RAM3,HDD1,HDD2,SSD1,SSD2,NVME1,NVME2,PSU1,PSU2,PSU3,NIC1,NIC2,SWITCH1,SWITCH2,PATCH,BOARD4,CPU3,RAM4,GPU3,GPU4,HDD3,HDD4,SSD3,NVME3,PCIESSD};
    private static Item item(String id,String tip){return new Component(id,tip);}
    static int size(ItemStack s){return s.getItem() instanceof Chassis?((Chassis)s.getItem()).units:0;}
    static int units(ItemStack s){return s.isEmpty()?0:size(s);}
    static int switchTier(ItemStack s){return s.isEmpty()?0:s.getItem()==SWITCH1?1:s.getItem()==SWITCH2?2:0;}
    static int rackUnits(ItemStack s){return switchTier(s)>0?1:units(s);}
    static int ports(int tier){return tier==1?4:tier==2?8:0;}
    static int switchMbps(int tier){return tier==1?200:tier==2?2000:0;}
    static int portMbps(int tier){return tier==1?100:tier==2?1000:0;}
    static int switchPower(int tier){return tier==1?8:tier==2?20:0;}
    static int tier(ItemStack s,Item... options){if(s.isEmpty())return 0;if(s.getCount()!=1)return -1;for(int i=0;i<options.length;i++)if(s.getItem()==options[i])return i+1;return -1;}
    static int partTier(int i,ItemStack s){if(i==ServerSpec.BOARD)return tier(s,BOARD1,BOARD2,BOARD3,BOARD4);if(i==ServerSpec.CPU||i==ServerSpec.CPU2)return tier(s,CPU1,CPU2,CPU3);if(i<ServerSpec.SATA)return tier(s,RAM1,RAM2,RAM3,RAM4);if(i<ServerSpec.NVME)return tier(s,HDD1,HDD2,SSD1,SSD2,HDD3,HDD4,SSD3);if(i<ServerSpec.PCIE)return tier(s,NVME1,NVME2,NVME3);if(i<ServerSpec.PSU)return tier(s,GPU1,GPU2,GPU3,GPU4,NIC1,NIC2,PCIESSD);return tier(s,PSU1,PSU2,PSU3);}
    static boolean accepts(int i,ItemStack s){if(s.isEmpty())return false;ItemStack one=s.copy();one.setCount(1);return partTier(i,one)>0;}
    static NonNullList<ItemStack> emptyParts(){return NonNullList.withSize(ServerSpec.COUNT,ItemStack.EMPTY);}
    static NonNullList<ItemStack> parts(ItemStack s){NonNullList<ItemStack> p=emptyParts();if(units(s)>0&&s.hasTagCompound())ItemStackHelper.loadAllItems(s.getTagCompound().getCompoundTag("Hardware"),p);return p;}
    static int[] tiers(List<ItemStack> p){int[] out=new int[ServerSpec.COUNT];for(int i=0;i<out.length;i++)out[i]=partTier(i,p.get(i));return out;}
    static int[] spec(ItemStack s){return tiers(parts(s));}
    static void writeParts(ItemStack s,NonNullList<ItemStack> p){if(units(s)==0)return;NBTTagCompound n=s.hasTagCompound()?s.getTagCompound():new NBTTagCompound();NBTTagCompound h=new NBTTagCompound();ItemStackHelper.saveAllItems(h,p);n.setTag("Hardware",h);s.setTagCompound(n);}
    static void ensureId(ItemStack s){if(units(s)==0)return;NBTTagCompound n=s.hasTagCompound()?s.getTagCompound():new NBTTagCompound();if(!n.hasUniqueId("ServerId")){n.setUniqueId("ServerId",java.util.UUID.randomUUID());s.setTagCompound(n);}}
    static int state(ItemStack s){return ServerSpec.state(units(s),spec(s));}
    static int gpus(ItemStack s){return ServerSpec.gpuTotal(spec(s));}
    static int vram(ItemStack s){return ServerSpec.vram(spec(s));}
    static int nic(ItemStack s){return ServerSpec.nic(spec(s));}
    static int hash(ItemStack s){int[] p=spec(s);int n=ServerSpec.cpuCU(p)/5;for(int t:ServerSpec.gpuSlots(p))n+=ServerSpec.gpuHash(t);return n;}
    static int idle(ItemStack s){return state(s)==ServerSpec.READY?8:0;}
    static int power(ItemStack s){int[] p=spec(s);if(state(s)!=ServerSpec.READY)return 0;int n=8+ServerSpec.cpuWatts(p);for(int t:ServerSpec.gpuSlots(p))n+=ServerSpec.gpuPeak(t);return n;}
    private static class Component extends Item {
        final String tip;
        Component(String id,String tip){this.tip=tip;setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setCreativeTab(TAB);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){if(!tip.isEmpty())tips.add(tip);}
    }
    private static final class Chassis extends Component {
        final int units;
        Chassis(String id,int u){super(id,u+"U");units=u;setMaxStackSize(1);addPropertyOverride(new ResourceLocation(ODC.ID,"ready"),(stack,world,entity)->state(stack)==ServerSpec.READY?1:0);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){super.addInformation(s,w,tips,flag);int[] p=spec(s);tips.add(net.minecraft.client.resources.I18n.format("gui.odc.boot."+state(s)));tips.add("Board T"+p[0]+" | CPU T"+p[1]+(p[ServerSpec.CPU2]>0?" + T"+p[ServerSpec.CPU2]:"")+" | "+gpus(s)+" GPU");tips.add(ServerSpec.ramGB(p)+" GB RAM | "+ServerSpec.diskGB(p)+" GB disk | "+vram(s)+" GB VRAM");tips.add(ServerSpec.psu(p)+" FE/t PSU | "+ServerSpec.nic(p)+" Mb/s NIC");for(ItemStack part:parts(s))if(!part.isEmpty())tips.add("  "+part.getDisplayName());}
    }
}
