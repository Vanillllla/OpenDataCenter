package odc;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=ODC.ID, name="OpenDataCenter", version="0.2.0-alpha", acceptedMinecraftVersions="[1.12.2]", dependencies="required-after:forge@[14.23.5.2859,)")
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class ODC {
    public static final String ID="odc";
    @Mod.Instance(ID) public static ODC instance;
    static int rewardDivisor=20;
    static int batteryOutput=50000;
    static int stationBandwidth=128, switchBandwidth=64;
    static int bankRewardMilli=1, aiRewardMilli=15;
    static boolean requireEnergy=true;
    static Market.Entry[] market;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e) {
        Configuration c=new Configuration(e.getSuggestedConfigurationFile());
        c.load();
        requireEnergy=c.getBoolean("requireEnergy","energy",true,"Require electricity for computers, racks and network devices.");
        batteryOutput=c.getInt("creativeBatteryOutput","energy",50000,1,1000000,"Maximum FE per tick from each creative battery.");
        rewardDivisor=c.getInt("rewardDivisor","mining",20,1,1000000,"Divisor for hash credit; integer milli-NTC accounting.");
        stationBandwidth=c.getInt("stationMbps","network",128,1,1000000,"Global connection Mbps per powered network station.");
        switchBandwidth=c.getInt("switchMbps","network",64,1,1000000,"Shared throughput Mbps per powered switch.");
        bankRewardMilli=c.getInt("bankRewardMilliPerCU","tasks",1,0,1000000,"Milli-NTC per CPU CU in a full one-second bank batch.");
        aiRewardMilli=c.getInt("aiRewardMilliPerGB","tasks",15,0,1000000,"Milli-NTC per eligible GB VRAM in a full one-second AI batch.");
        c.save();
        market=Market.load(e.getModConfigurationDirectory());
        GameRegistry.registerTileEntity(Tiles.Rack.class,new ResourceLocation(ID,"rack"));
        GameRegistry.registerTileEntity(Tiles.Assembler.class,new ResourceLocation(ID,"assembler"));
        GameRegistry.registerTileEntity(Tiles.Computer.class,new ResourceLocation(ID,"computer"));
        GameRegistry.registerTileEntity(Tiles.Battery.class,new ResourceLocation(ID,"battery"));
        GameRegistry.registerTileEntity(Tiles.Switch.class,new ResourceLocation(ID,"network_switch"));
        GameRegistry.registerTileEntity(Tiles.Station.class,new ResourceLocation(ID,"network_station"));
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new Menus.Handler());
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){e.getRegistry().registerAll(Hardware.BLOCKS);}
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){
        for(Block b:Hardware.BLOCKS){ItemBlock ib=new ItemBlock(b);ib.setRegistryName(b.getRegistryName());e.getRegistry().register(ib);}
        e.getRegistry().registerAll(Hardware.ITEMS);
    }
}
