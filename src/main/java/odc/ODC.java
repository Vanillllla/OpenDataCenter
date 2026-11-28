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

@Mod(modid=ODC.ID, name="OpenDataCenter", version="0.6.2.2-alpha", acceptedMinecraftVersions="[1.12.2]", dependencies="required-after:forge@[14.23.5.2859,)")
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class ODC {
    public static final String ID="odc";
    @Mod.Instance(ID) public static ODC instance;
    static int batteryOutput=50000;
    static final int[] transformerWatts=TransformerCore.DEFAULT_WATTS.clone();
    static int heMilliJoules=50,feMilliJoules=50;
    static int transformerWatts(int tier){return transformerWatts[TransformerCore.tierIndex(tier)];}
    static int conversionRate(TransformerCore.Input input){return input==TransformerCore.Input.HE?heMilliJoules:feMilliJoules;}
    static int journalLimit=256;
    static boolean requireEnergy=true;
    static Market.Entry[] market;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e) {
        Configuration c=new Configuration(e.getSuggestedConfigurationFile());
        c.load();
        requireEnergy=c.getBoolean("requireEnergy","energy",true,"Require electricity for computers, racks and network devices.");
        batteryOutput=c.getInt("creativeBatteryOutput","energy",50000,1,1000000,"Maximum watts from each creative battery (0.6.2+; previous numeric setting retained).");
        for(int tier=1;tier<=3;tier++)transformerWatts[tier-1]=c.getInt("transformerT"+tier+"Watts","energy",TransformerCore.defaultWatts(tier),1,TransformerCore.MAX_WATTS,"Maximum transformer output in watts.");
        heMilliJoules=c.getInt("heMilliJoulesPerUnit","energy",50,1,100000000,"Provisional millijoules per HE; adapter comes in 0.6.2.5.");
        feMilliJoules=c.getInt("feMilliJoulesPerUnit","energy",50,1,100000000,"Provisional millijoules per FE; adapter comes in 0.6.2.5.");
        journalLimit=c.getInt("journalLimit","dataos",256,16,4096,"Maximum retained events per data center.");
        ContractMarket.configure(c);c.save();
        market=Market.load(e.getModConfigurationDirectory());
        GameRegistry.registerTileEntity(Tiles.Rack.class,new ResourceLocation(ID,"rack"));
        GameRegistry.registerTileEntity(Tiles.Workbench.class,new ResourceLocation(ID,"workbench"));
        GameRegistry.registerTileEntity(Tiles.Computer.class,new ResourceLocation(ID,"computer"));
        GameRegistry.registerTileEntity(Tiles.Battery.class,new ResourceLocation(ID,"battery"));
        GameRegistry.registerTileEntity(Tiles.Transformer.class,new ResourceLocation(ID,"transformer"));
        GameRegistry.registerTileEntity(Tiles.NetworkTerminal.class,new ResourceLocation(ID,"network_terminal"));
        GameRegistry.registerTileEntity(Tiles.Station.class,new ResourceLocation(ID,"network_station"));
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new Menus.Handler());
        RackInteraction.init();
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){e.getRegistry().registerAll(Hardware.BLOCKS);}
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){
        for(Block b:Hardware.BLOCKS){ItemBlock ib=b instanceof Blocks.NetworkTerminal?new Hardware.NetworkTerminalItem((Blocks.NetworkTerminal)b):b instanceof Blocks.Transformer?new Hardware.TransformerItem((Blocks.Transformer)b):new ItemBlock(b);ib.setRegistryName(b.getRegistryName());e.getRegistry().register(ib);}
        e.getRegistry().registerAll(Hardware.ITEMS.toArray(new Item[0]));
    }
}
