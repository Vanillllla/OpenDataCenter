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

/** Regression tests for cursor clicks, QUICK_MOVE and the actual chassis NBT inventory. */
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
    private static void click(Menus.Menu menu,int slot,int button) { menu.slotClick(slot,button,ClickType.PICKUP,menu.player); }
    private static void cursor(EntityPlayer p,Item item,int count,String message){
        ItemStack held=p.inventory.getItemStack();check(count==0?held.isEmpty():held.getItem()==item&&held.getCount()==count,message);
    }
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
        // Reproduce the reported bug: click a single DIMM, place it, then click a second slot.
        // Use a fresh bench so no contents from the shift-click tests can hide a duplicate.
        menu.onContainerClosed(player);
        player.inventory.clear();
        bench=new Tiles.Workbench();world.bench=bench;bench.setWorld(world);
        menu=new Menus.Menu(Menus.WORKBENCH,player,bench);
        bag=menu.machineSlots;
        player.inventory.setItemStack(new ItemStack(Hardware.CHASSIS1));
        click(menu,0,0);cursor(player,Hardware.CHASSIS1,0,"chassis must leave cursor");
        player.inventory.setItemStack(new ItemStack(board));
        click(menu,1+ServerSpec.BOARD,0);cursor(player,board,0,"board must leave cursor");
        int dimm=1+ServerSpec.RAM;
        menu.getSlot(bag).putStack(new ItemStack(ram));
        click(menu,bag,0);cursor(player,ram,1,"one DIMM picked up");
        click(menu,dimm,0);cursor(player,ram,0,"last DIMM must leave cursor");
        click(menu,dimm+1,0);check(inChassis(bench.slots.get(0),ram)==1,"empty cursor cannot duplicate DIMM");
        click(menu,dimm,0);cursor(player,ram,1,"removed DIMM comes back exactly once");
        check(inChassis(bench.slots.get(0),ram)==0,"normal removal updates NBT");
        click(menu,dimm+1,1);cursor(player,ram,0,"right-click also consumes final item");
        menu.getSlot(bag).putStack(ItemStack.EMPTY);
        for(int i=bag+1;i<menu.inventorySlots.size();i++)menu.getSlot(i).putStack(new ItemStack(Items.APPLE,64));
        move(menu,dimm+1);move(menu,dimm+1);
        check(menu.getSlot(bag).getStack().getCount()==1&&inChassis(bench.slots.get(0),ram)==0,"shift extraction cannot recover phantom copy");
        click(menu,bag,0);click(menu,dimm,0);
        player.inventory.setItemStack(new ItemStack(ram,3));
        click(menu,dimm,0);cursor(player,ram,3,"full identical slot does not consume or duplicate");
        click(menu,dimm+1,1);cursor(player,ram,2,"stack insertion consumes precisely one");
        check(inChassis(bench.slots.get(0),ram)==2,"two physical DIMMs");
        click(menu,1+ServerSpec.BOARD,0);cursor(player,ram,2,"incompatible placement rejected");
        player.inventory.setItemStack(ItemStack.EMPTY);
        click(menu,1+ServerSpec.BOARD,0);check(menu.getSlot(1+ServerSpec.BOARD).getHasStack(),"populated board cannot be removed");
        Item otherRam=model("KingStoned Value 8");
        player.inventory.setItemStack(new ItemStack(otherRam));
        click(menu,dimm,0);cursor(player,ram,1,"swap returns exactly the previous DIMM");
        check(inChassis(bench.slots.get(0),otherRam)==1&&inChassis(bench.slots.get(0),ram)==1,"swap commits to chassis NBT");
        player.inventory.setItemStack(ItemStack.EMPTY);
        click(menu,0,0);cursor(player,Hardware.CHASSIS1,1,"packed chassis is on cursor");
        check(bench.chassis()==0&&menu.getSlot(dimm).getStack().isEmpty(),"removal clears virtual slots");
        check(inChassis(player.inventory.getItemStack(),ram)==1&&inChassis(player.inventory.getItemStack(),otherRam)==1,"chassis retains both real DIMMs");
        click(menu,0,1);cursor(player,Hardware.CHASSIS1,0,"reinserting packed chassis consumes cursor");
        check(inChassis(bench.slots.get(0),otherRam)==1,"packed chassis survives reinsertion");
        // Real cursor, shift-click and direct inventory validation with a 3-slot GPU.
        menu.onContainerClosed(player);player.inventory.clear();
        bench=new Tiles.Workbench();world.bench=bench;bench.setWorld(world);
        menu=new Menus.Menu(Menus.WORKBENCH,player,bench);bag=menu.machineSlots;
        Item largeBoard=model("ASBrick Rack EP2C612 WS"),gpu=model("NoVideo RTX 3080"),smallGpu=model("NoVideo Teslo M10"),nic=model("FakeTek G1000");
        bench.setInventorySlotContents(0,new ItemStack(Hardware.CHASSIS4));
        menu.parts.setInventorySlotContents(ServerSpec.BOARD,new ItemStack(largeBoard));
        int pci=1+ServerSpec.PCIE;
        player.inventory.setItemStack(new ItemStack(nic));click(menu,pci+1,0);
        player.inventory.setItemStack(new ItemStack(gpu));click(menu,pci,0);cursor(player,gpu,1,"fragmented placement rejected");
        player.inventory.setItemStack(ItemStack.EMPTY);click(menu,pci+1,0);cursor(player,nic,1,"remove obstruction");
        player.inventory.setItemStack(new ItemStack(gpu));click(menu,pci,0);cursor(player,gpu,0,"GPU inserted");
        check(menu.getSlot(pci).isEnabled()&&!menu.getSlot(pci+1).isEnabled()&&!menu.getSlot(pci+2).isEnabled()&&menu.getSlot(pci+3).isEnabled(),"GUI follower slots disabled");
        player.inventory.setItemStack(new ItemStack(nic));click(menu,pci+1,0);click(menu,pci+2,1);cursor(player,nic,1,"blocked left/right click");
        menu.parts.setInventorySlotContents(ServerSpec.PCIE+1,new ItemStack(nic));
        check(menu.getSlot(pci+1).getStack().isEmpty(),"server inventory setter rejects covered slot");
        menu.getSlot(bag).putStack(new ItemStack(nic));move(menu,bag);
        check(menu.getSlot(pci+3).getStack().getItem()==nic&&menu.getSlot(pci+1).getStack().isEmpty()&&menu.getSlot(bag).getStack().isEmpty(),"shift click skips blocked slots");
        menu.parts.setInventorySlotContents(ServerSpec.BOARD,new ItemStack(board));
        check(menu.getSlot(1+ServerSpec.BOARD).getStack().getItem()==largeBoard,"direct populated board replacement rejected");
        // Persist the real chassis NBT; reopening derives the lock again without placeholder items.
        ItemStack saved=new ItemStack(bench.slots.get(0).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        menu.onContainerClosed(player);bench.setInventorySlotContents(0,saved);
        menu=new Menus.Menu(Menus.WORKBENCH,player,bench);bag=menu.machineSlots;
        check(!menu.getSlot(pci+1).isEnabled()&&!menu.getSlot(pci+2).isEnabled()&&inChassis(saved,gpu)==1,"reopen and NBT round trip");
        player.inventory.setItemStack(new ItemStack(smallGpu));click(menu,pci,0);cursor(player,gpu,1,"narrower card swap");
        check(!menu.getSlot(pci+1).isEnabled()&&menu.getSlot(pci+2).isEnabled(),"swap unlocks excess");
        player.inventory.setItemStack(ItemStack.EMPTY);click(menu,pci,0);cursor(player,smallGpu,1,"GPU extracted");
        check(menu.getSlot(pci+1).isEnabled()&&menu.getSlot(pci+2).isEnabled(),"all followers unlocked");
        // A legacy overlapping item remains visible and extractable for manual repair.
        net.minecraft.util.NonNullList<ItemStack> legacy=bench.parts();
        legacy.set(ServerSpec.PCIE,new ItemStack(gpu));legacy.set(ServerSpec.PCIE+1,new ItemStack(nic));bench.write(legacy);
        check(menu.getSlot(pci+1).isEnabled()&&!menu.fits(ServerSpec.PCIE+1),"legacy item visible but insertion blocked");
        player.inventory.setItemStack(ItemStack.EMPTY);click(menu,pci+1,0);cursor(player,nic,1,"legacy obstruction recoverable");
        check(!menu.getSlot(pci+1).isEnabled(),"legacy repair restores lock");
        // New models share height but have independent constraints and stable item ids.
        ItemStack compact=new ItemStack(Hardware.CHASSIS.get(ChassisSpec.COMPACT_2));
        Hardware.ensureId(compact);UUID compactId=compact.getTagCompound().getUniqueId("ServerId");
        bench.setInventorySlotContents(0,compact);
        check(bench.chassis()==2&&bench.chassisModel()==ChassisSpec.COMPACT_2,"model independent of rack height");
        menu.parts.setInventorySlotContents(ServerSpec.BOARD,new ItemStack(largeBoard));
        check(menu.parts.getStackInSlot(ServerSpec.BOARD).isEmpty(),"compact rejects dual board");
        menu.parts.setInventorySlotContents(ServerSpec.BOARD,new ItemStack(board));
        check(menu.parts.getStackInSlot(ServerSpec.BOARD).getItem()==board&&!menu.fits(ServerSpec.PSU+1),"model-specific PSU limit");
        ItemStack restored=new ItemStack(bench.slots.get(0).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        check(Hardware.chassis(restored)==ChassisSpec.COMPACT_2&&restored.getTagCompound().getUniqueId("ServerId").equals(compactId),"model and UUID NBT");
        check(Hardware.parts(restored).size()==ServerSpec.COUNT&&Hardware.spec(restored)[ServerSpec.BOARD]==Hardware.spec(bench.slots.get(0))[ServerSpec.BOARD],"extended slots stored");
        // Render state is a pure function of the stored chassis, including after NBT reload.
        odc.Blocks.Workbench block=(odc.Blocks.Workbench)Hardware.WORKBENCH;
        Item[] cases={Hardware.CHASSIS1,Hardware.CHASSIS2,Hardware.CHASSIS4};
        for(int i=0;i<cases.length;i++){
            bench.setInventorySlotContents(0,new ItemStack(cases[i]));
            check(block.getActualState(block.getDefaultState(),world,BlockPos.ORIGIN).getValue(odc.Blocks.Workbench.CHASSIS)==i+1,"model matches chassis size");
        }
        bench.removeStackFromSlot(0);
        check(block.getActualState(block.getDefaultState(),world,BlockPos.ORIGIN).getValue(odc.Blocks.Workbench.CHASSIS)==0,"empty bench model");
        System.out.println("PASS workbench PICKUP, QUICK_MOVE, swap and render state");
    }
}
