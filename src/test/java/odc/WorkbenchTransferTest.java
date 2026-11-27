package odc;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Exercise the normal vanilla QUICK_MOVE click and the actual chassis NBT inventory. */
public final class WorkbenchTransferTest {
    private static final class BenchWorld extends World {
        Tiles.Workbench bench;
        BenchWorld() {
            super(new SaveHandlerMP(),new WorldInfo(new WorldSettings(0,GameType.SURVIVAL,false,false,WorldType.FLAT),"test"),
                    new WorldProviderSurface(),new Profiler(),false);
        }
        protected IChunkProvider createChunkProvider(){return null;}
        protected boolean isChunkLoaded(int x,int z,boolean empty){return false;}
        public BlockPos getSpawnPoint(){return BlockPos.ORIGIN;}
        public TileEntity getTileEntity(BlockPos pos){return bench;}
        public IBlockState getBlockState(BlockPos pos){return Blocks.AIR.getDefaultState();}
        public void notifyBlockUpdate(BlockPos p,IBlockState a,IBlockState b,int flags){}
        public void markChunkDirty(BlockPos p,TileEntity tile){}
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    private static Item model(String name) {
        return Hardware.MODELS.entrySet().stream().filter(e->e.getValue().name.equals(name)).findFirst().get().getKey();
    }
    private static void move(Menus.Menu menu,int slot) { menu.slotClick(slot,0,ClickType.QUICK_MOVE,menu.player); }
    private static int inChassis(ItemStack chassis,Item item) {
        int count=0;
        for(ItemStack s:Hardware.parts(chassis)) if(s.getItem()==item) count+=s.getCount();
        return count;
    }
    public static void main(String[] args) {
        Bootstrap.register();
        for(Item item:Hardware.ITEMS) ForgeRegistries.ITEMS.register(item);
        BenchWorld world=new BenchWorld();
        Tiles.Workbench bench=new Tiles.Workbench();
        world.bench=bench; bench.setWorld(world);
        EntityPlayer player=new EntityPlayer(world,new GameProfile(UUID.randomUUID(),"assembler")){
            public boolean isSpectator(){return false;}
            public boolean isCreative(){return false;}
        };
        player.setPosition(.5,.5,.5);
        Menus.Menu menu=new Menus.Menu(Menus.WORKBENCH,player,bench);
        int bag=menu.machineSlots;
        Item board=model("ASBrick H81-S"), ram=model("KingStoned Value 4");
        menu.getSlot(bag).putStack(new ItemStack(Hardware.CHASSIS1));
        move(menu,bag);
        menu.getSlot(bag).putStack(new ItemStack(board));
        move(menu,bag);
        menu.getSlot(bag).putStack(new ItemStack(ram,5));
        move(menu,bag);move(menu,bag);
        check(menu.getSlot(bag).getStack().getCount()==3 && inChassis(bench.slots.get(0),ram)==2,"two DIMMs and three spare");
        move(menu,1+ServerSpec.BOARD);
        check(inChassis(bench.slots.get(0),board)==1,"populated board protected");
        for(int i=bag+1;i<menu.inventorySlots.size();i++) menu.getSlot(i).putStack(new ItemStack(Items.APPLE,64));
        menu.getSlot(bag).putStack(new ItemStack(ram,64));
        move(menu,1+ServerSpec.RAM);
        check(inChassis(bench.slots.get(0),ram)==2,"full bag leaves chassis intact");
        menu.getSlot(bag).putStack(new ItemStack(ram,63));
        move(menu,1+ServerSpec.RAM);move(menu,1+ServerSpec.RAM);
        check(inChassis(bench.slots.get(0),ram)==1 && menu.getSlot(bag).getStack().getCount()==64,"partial stack accepted exactly one DIMM");
        menu.getSlot(bag+1).putStack(ItemStack.EMPTY);
        move(menu,0);move(menu,0);
        ItemStack packed=menu.getSlot(bag+1).getStack();
        check(bench.chassis()==0 && inChassis(packed,ram)==1 && inChassis(packed,board)==1,"packed chassis transferred exactly once");
        check(menu.getSlot(1+ServerSpec.RAM+1).getStack().isEmpty(),"detached slots empty");
        move(menu,bag+1);
        check(menu.getSlot(bag+1).getStack().isEmpty() && inChassis(bench.slots.get(0),ram)==1,"reinserted chassis keeps one DIMM");
        System.out.println("PASS workbench QUICK_MOVE");
    }
}
