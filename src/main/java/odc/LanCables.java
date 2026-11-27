package odc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.inventory.InventoryHelper;
final class LanCables {
 static int count(EntityPlayer p,int tier){int n=0;for(ItemStack s:p.inventory.mainInventory)if(!s.isEmpty()&&s.getItem()==Hardware.lan(tier))n+=s.getCount();return n;}
 static boolean take(EntityPlayer p,int tier){if(LogicalLinks.speed(tier)==0)return false;for(ItemStack s:p.inventory.mainInventory)if(!s.isEmpty()&&s.getItem()==Hardware.lan(tier)){s.shrink(1);p.inventory.markDirty();return true;}return false;}
 static void refund(World w,BlockPos pos,EntityPlayer p,int tier){if(LogicalLinks.speed(tier)==0||w==null||w.isRemote)return;ItemStack s=new ItemStack(Hardware.lan(tier));if(p!=null){p.inventory.addItemStackToInventory(s);p.inventory.markDirty();}if(!s.isEmpty())InventoryHelper.spawnItemStack(w,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,s);}
}
