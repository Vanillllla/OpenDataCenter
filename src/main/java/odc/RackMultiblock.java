package odc;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Arrays;
import java.util.Objects;

/** Pair on placement only: stable pairing, no silent relocation of existing modules. */
final class RackMultiblock {
    static Tiles.Rack at(World w,BlockPos p){if(!w.isBlockLoaded(p))return null;TileEntity t=w.getTileEntity(p);return t instanceof Tiles.Rack?(Tiles.Rack)t:null;}
    static void placed(World w,BlockPos p){if(w.isRemote)return;Tiles.Rack r=at(w,p);if(r==null||r.role!=0)return;if(!merge(w,at(w,p.down()),r))merge(w,r,at(w,p.up()));}
    static boolean merge(World w,Tiles.Rack low,Tiles.Rack high){
        if(low==null||high==null||low.role!=0||high.role!=0||!Objects.equals(low.owner,high.owner))return false;
        IBlockState a=w.getBlockState(low.getPos()),b=w.getBlockState(high.getPos());if(a.getBlock()!=b.getBlock()||a.getValue(Blocks.FACING)!=b.getValue(Blocks.FACING))return false;
        boolean keepLowSwitch=low.switchTier()>0;int returned=low.clearPorts()+high.clearPorts();
        for(int i=0;i<4;i++){ItemStack item=high.slots.get(i);if(keepLowSwitch&&Hardware.switchTier(item)>0)InventoryHelper.spawnItemStack(w,high.getPos().getX()+.5,high.getPos().getY()+.5,high.getPos().getZ()+.5,item);else low.slots.set(i+5,item);high.slots.set(i,ItemStack.EMPTY);low.remainder[i+5]=high.remainder[i];high.remainder[i]=0;}
        low.enabledMask|=high.enabledMask<<5;low.miningMask|=high.miningMask<<5;low.poweredMask=low.runningMask=0;high.enabledMask=high.miningMask=high.poweredMask=high.runningMask=0;
        low.role=1;high.role=-1;low.revision++;high.revision++;low.refund(returned,null);low.changed();high.changed();Network.invalidate(w);return true;
    }
    /** Called before the broken tile is removed. Master owns every item exactly once. */
    static void breakRack(World w,BlockPos p){Tiles.Rack r=at(w,p);if(r==null||w.isRemote)return;Tiles.Rack master=r.master();if(master==null)return;
        Tiles.Rack survivor=master.role==1?at(w,p.equals(master.getPos())?p.up():p.down()):null;
        int refunded=master.clearPorts();master.refund(refunded,null);
        ItemStack[] retained=new ItemStack[4];Arrays.fill(retained,ItemStack.EMPTY);boolean keepUpper=survivor!=null&&survivor!=master;
        int mask=0,mining=0;int[] rem=new int[4];
        for(int i=0;i<9;i++){ItemStack stack=master.slots.get(i);if(stack.isEmpty())continue;int k=survivor==null?-1:RackLayout.retainedIndex(i,Hardware.rackUnits(stack),keepUpper);if(k>=0){retained[k]=stack;rem[k]=master.remainder[i];if((master.enabledMask&(1<<i))!=0)mask|=1<<k;if((master.miningMask&(1<<i))!=0)mining|=1<<k;}else InventoryHelper.spawnItemStack(w,p.getX()+.5,p.getY()+.5,p.getZ()+.5,stack);master.slots.set(i,ItemStack.EMPTY);}
        Arrays.fill(master.remainder,0);master.revision++;if(survivor!=null){for(int i=0;i<9;i++)survivor.slots.set(i,i<4?retained[i]:ItemStack.EMPTY);Arrays.fill(survivor.remainder,0);System.arraycopy(rem,0,survivor.remainder,0,4);survivor.role=0;survivor.enabledMask=mask;survivor.miningMask=mining;survivor.poweredMask=survivor.runningMask=0;survivor.switchOnline=false;survivor.revision++;survivor.changed();}
    }
}
