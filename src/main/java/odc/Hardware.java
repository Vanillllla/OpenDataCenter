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
    static final Block RACK=new Blocks.Rack("rack",false),RACK_GLASS=new Blocks.Rack("rack_glass",true),WORKBENCH=new Blocks.Workbench(),COMPUTER=new Blocks.Terminal(),BATTERY=new Blocks.Machine("creative_battery",3),STATION=new Blocks.Machine("radio_station",5),SATELLITE=new Blocks.Machine("satellite_station",6),DATA_CABLE=new Blocks.Cable("data_cable",false,false),POWER_CABLE=new Blocks.Cable("power_cable",true,false),RELAY=new Blocks.Cable("power_relay",true,true),FLOOR=new Blocks.Decor("raised_floor"),PANEL=new Blocks.Decor("wall_panel");
    static final Block[] BLOCKS={RACK,RACK_GLASS,WORKBENCH,COMPUTER,BATTERY,STATION,SATELLITE,DATA_CABLE,POWER_CABLE,RELAY,FLOOR,PANEL};
    static final Item CHASSIS1=new Chassis("chassis_1u",1), CHASSIS2=new Chassis("chassis_2u",2), CHASSIS4=new Chassis("chassis_4u",4);
    static final Item CHIP=item("precision_chip",""), SWITCH1=item("switch_basic","4 × 100 Mb/s"), SWITCH2=item("switch_pro","8 × 1000 Mb/s"), PATCH=item("ethernet_patch","1 internal connection");
    static final java.util.Map<Item,Part> MODELS=new java.util.IdentityHashMap<>();
    static final java.util.List<Item> ITEMS=new java.util.ArrayList<>(java.util.Arrays.asList(CHASSIS1,CHASSIS2,CHASSIS4,CHIP,SWITCH1,SWITCH2,PATCH));
    static {
        for(Part model:Part.models) if(model.index>0) {
            Item component=item(model.id,String.join("\n",model.details()));
            MODELS.put(component,model);
            ITEMS.add(component);
        }
    }
    private static Item item(String id,String tip){return new Component(id,tip);}
    static int size(ItemStack s){return s.getItem() instanceof Chassis?((Chassis)s.getItem()).units:0;}
    static int units(ItemStack s){return s.isEmpty()?0:size(s);}
    static int switchTier(ItemStack s){return s.isEmpty()?0:s.getItem()==SWITCH1?1:s.getItem()==SWITCH2?2:0;}
    static int rackUnits(ItemStack s){return switchTier(s)>0?1:units(s);}
    static int ports(int tier){return tier==1?4:tier==2?8:0;}
    static int switchMbps(int tier){return tier==1?200:tier==2?2000:0;}
    static int portMbps(int tier){return tier==1?100:tier==2?1000:0;}
    static int switchPower(int tier){return tier==1?8:tier==2?20:0;}
    static int model(ItemStack s) {
        if(s.isEmpty()) return 0;
        Part part=MODELS.get(s.getItem());
        return part==null || s.getCount()!=1?-1:part.index;
    }
    static NonNullList<ItemStack> emptyParts(){return NonNullList.withSize(ServerSpec.COUNT,ItemStack.EMPTY);}
    static NonNullList<ItemStack> parts(ItemStack s){NonNullList<ItemStack> p=emptyParts();if(units(s)>0&&s.hasTagCompound())ItemStackHelper.loadAllItems(s.getTagCompound().getCompoundTag("Hardware"),p);return p;}
    static int[] tiers(List<ItemStack> p){int[] out=new int[ServerSpec.COUNT];for(int i=0;i<out.length;i++)out[i]=model(p.get(i));return out;}
    static int[] spec(ItemStack s){return tiers(parts(s));}
    static void writeParts(ItemStack s,NonNullList<ItemStack> p){if(units(s)==0)return;NBTTagCompound n=s.hasTagCompound()?s.getTagCompound():new NBTTagCompound();NBTTagCompound h=new NBTTagCompound();ItemStackHelper.saveAllItems(h,p);n.setTag("Hardware",h);s.setTagCompound(n);}
    static void ensureId(ItemStack s){if(units(s)==0)return;NBTTagCompound n=s.hasTagCompound()?s.getTagCompound():new NBTTagCompound();if(!n.hasUniqueId("ServerId")){n.setUniqueId("ServerId",java.util.UUID.randomUUID());s.setTagCompound(n);}}
    static int state(ItemStack s){return ServerSpec.state(units(s),spec(s));}
    static int gpus(ItemStack s){return ServerSpec.gpuTotal(spec(s));}
    static int vram(ItemStack s){return ServerSpec.vram(spec(s));}
    static int nic(ItemStack s){return ServerSpec.nic(spec(s));}
    static int idle(ItemStack s){int[] p=spec(s);TaskEngine.Node node=new TaskEngine.Node(null,0,0,p);return node.fe(node.watts(0,0));}
    private static class Component extends Item {
        final String tip;
        Component(String id,String tip){this.tip=tip;setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setCreativeTab(TAB);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){if(!tip.isEmpty())java.util.Collections.addAll(tips,tip.split("\n"));}
    }
    private static final class Chassis extends Component {
        final int units;
        Chassis(String id,int u){super(id,u+"U | "+u+" PSU | "+ServerSpec.chassisPcie(u)+" PCIe");units=u;setMaxStackSize(1);addPropertyOverride(new ResourceLocation(ODC.ID,"ready"),(stack,world,entity)->state(stack)==ServerSpec.READY?1:0);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,@Nullable World w,List<String> tips,ITooltipFlag flag){super.addInformation(s,w,tips,flag);int[] p=spec(s);tips.add(net.minecraft.client.resources.I18n.format("gui.odc.boot."+state(s)));tips.add(ServerSpec.cpuCores(p)+" cores | "+gpus(s)+" GPU");tips.add(ServerSpec.ramGB(p)+" GB RAM | "+ServerSpec.diskGB(p)+" GB disk | "+vram(s)+" GB VRAM");tips.add(ServerSpec.psu(p)+" W PSU | "+ServerSpec.nic(p)+" Mb/s NIC");Part board=Part.at(p[ServerSpec.BOARD]);
            for(ItemStack part:parts(s)) if(!part.isEmpty()) {
                Part device=MODELS.get(part.getItem());
                String link=device!=null && (device.kind==Part.Kind.GPU || device.kind==Part.Kind.NIC) && board.pci>0
                    ?" [PCIe "+Math.min(device.pci,board.pci)+".0]":"";
                tips.add("  "+part.getDisplayName()+link);
            }}
    }
}
