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
    static final Block RACK=new Blocks.Rack("rack",false),RACK_GLASS=new Blocks.Rack("rack_glass",true),WORKBENCH=new Blocks.Workbench(),COMPUTER=new Blocks.Terminal(),BATTERY=new Blocks.Machine("creative_battery",3),STATION=new Blocks.Machine("radio_station",5),SATELLITE=new Blocks.Machine("satellite_station",6),TOWER=new Blocks.Tower(),NETWORK_TERMINAL=new Blocks.NetworkTerminal(NetworkEquipment.TERMINAL_1),NETWORK_TERMINAL_T2=new Blocks.NetworkTerminal(NetworkEquipment.TERMINAL_2),NETWORK_TERMINAL_T3=new Blocks.NetworkTerminal(NetworkEquipment.TERMINAL_3),FIBER_CABLE=new Blocks.Cable("fiber_cable",false,false,true),DATA_CABLE=new Blocks.Cable("data_cable",false,false),POWER_CABLE=new Blocks.Cable("power_cable",true,false),RELAY=new Blocks.Cable("power_relay",true,true),FLOOR=new Blocks.Decor("raised_floor"),PANEL=new Blocks.Decor("wall_panel");
    static final Block[] BLOCKS={RACK,RACK_GLASS,WORKBENCH,COMPUTER,BATTERY,STATION,SATELLITE,TOWER,NETWORK_TERMINAL,NETWORK_TERMINAL_T2,NETWORK_TERMINAL_T3,FIBER_CABLE,DATA_CABLE,POWER_CABLE,RELAY,FLOOR,PANEL};
    static final class NetworkTerminalItem extends ItemBlock {
        private final NetworkEquipment equipment;
        NetworkTerminalItem(Blocks.NetworkTerminal block){super(block);equipment=block.equipment;}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack stack,@Nullable World world,List<String> tips,ITooltipFlag flag){super.addInformation(stack,world,tips,flag);tips.add(net.minecraft.client.resources.I18n.format("tooltip.odc.network_terminal.capacity",equipment.mbps));}
    }
    static boolean networkTerminal(Block b){return b instanceof Blocks.NetworkTerminal;}
    static boolean antenna(Block b){return b==STATION||b==SATELLITE||b==TOWER;}
    static final Item CHASSIS1=new Chassis("chassis_1u",1), CHASSIS2=new Chassis("chassis_2u",2), CHASSIS4=new Chassis("chassis_4u",4);
    static final Item CHIP=item("precision_chip",""), PATCH=item("ethernet_patch","100 Mb/s"), PATCH2=item("ethernet_patch_t2","1000 Mb/s"), PATCH3=item("ethernet_patch_t3","100000 Mb/s");
    static final Item[] SWITCHES=new Item[SwitchLimits.count()+1];
    static final java.util.Map<Item,Part> MODELS=new java.util.IdentityHashMap<>();
    static final java.util.List<Item> ITEMS=new java.util.ArrayList<>(java.util.Arrays.asList(CHASSIS1,CHASSIS2,CHASSIS4,CHIP,PATCH,PATCH2,PATCH3));
    static {
        for(int i=1;i<SWITCHES.length;i++){SwitchLimits.Model m=SwitchLimits.model(i);SWITCHES[i]=item(m.id,m.ports+" ports | "+m.total+" Mb/s total\n"+m.port+" Mb/s per port");ITEMS.add(SWITCHES[i]);}
        for(Part model:Part.models) if(model.index>0) {
            Item component=item(model.id,String.join("\n",model.details()));
            MODELS.put(component,model);
            ITEMS.add(component);
        }
    }
    private static Item item(String id,String tip){return new Component(id,tip);}
    static int size(ItemStack s){return s.getItem() instanceof Chassis?((Chassis)s.getItem()).units:0;}
    static int units(ItemStack s){return s.isEmpty()?0:size(s);}
    static int switchTier(ItemStack s){if(!s.isEmpty())for(int i=1;i<SWITCHES.length;i++)if(s.getItem()==SWITCHES[i])return i;return 0;}
    static int rackUnits(ItemStack s){return switchTier(s)>0?1:units(s);}
    static int ports(int tier){return SwitchLimits.ports(tier);}
    static int switchMbps(int tier){return SwitchLimits.totalMbps(tier);}
    static Item lan(int tier){return tier==2?PATCH2:tier==3?PATCH3:PATCH;}
    static int portMbps(int tier){return SwitchLimits.portMbps(tier);}
    static int switchPower(int tier){return SwitchLimits.power(tier);}
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
    static java.util.UUID switchId(ItemStack s){if(switchTier(s)==0)throw new IllegalArgumentException("Not a switch");NBTTagCompound n=s.hasTagCompound()?s.getTagCompound():new NBTTagCompound();if(!n.hasUniqueId("SwitchId")){n.setUniqueId("SwitchId",java.util.UUID.randomUUID());s.setTagCompound(n);}return n.getUniqueId("SwitchId");}
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
