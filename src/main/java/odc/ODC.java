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

@Mod(modid=ODC.ID, name="OpenDataCenter", version="0.3.1-alpha", acceptedMinecraftVersions="[1.12.2]", dependencies="required-after:forge@[14.23.5.2859,)")
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class ODC {
    public static final String ID="odc";
    @Mod.Instance(ID) public static ODC instance;
    static int rewardDivisor=20;
    static int batteryOutput=50000;
    static boolean requireEnergy=true;
    static Market.Entry[] market;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e) {
        Configuration c=new Configuration(e.getSuggestedConfigurationFile());
        c.load();
        requireEnergy=c.getBoolean("requireEnergy","energy",true,"Require electricity for computers, racks and network devices.");
        batteryOutput=c.getInt("creativeBatteryOutput","energy",50000,1,1000000,"Maximum FE per tick from each creative battery.");
        rewardDivisor=c.getInt("rewardDivisor","mining",20,1,1000000,"Divisor for hash credit; integer milli-NTC accounting.");
        c.save();
        market=Market.load(e.getModConfigurationDirectory());
        GameRegistry.registerTileEntity(Tiles.Rack.class,new ResourceLocation(ID,"rack"));
        GameRegistry.registerTileEntity(Tiles.Workbench.class,new ResourceLocation(ID,"workbench"));
        GameRegistry.registerTileEntity(Tiles.Computer.class,new ResourceLocation(ID,"computer"));
        GameRegistry.registerTileEntity(Tiles.Battery.class,new ResourceLocation(ID,"battery"));
        GameRegistry.registerTileEntity(Tiles.Station.class,new ResourceLocation(ID,"network_station"));
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new Menus.Handler());
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){e.getRegistry().registerAll(Hardware.BLOCKS);}
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){
        for(Block b:Hardware.BLOCKS){ItemBlock ib=new ItemBlock(b);ib.setRegistryName(b.getRegistryName());e.getRegistry().register(ib);}
        e.getRegistry().registerAll(Hardware.ITEMS);
    }
}
