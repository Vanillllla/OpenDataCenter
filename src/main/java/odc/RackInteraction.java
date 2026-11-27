package odc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Ordinary RMB uses the vanilla block interaction; modifier RMB uses a server-validated ray trace. */
public final class RackInteraction {
    static SimpleNetworkWrapper channel;
    static void init(){channel=NetworkRegistry.INSTANCE.newSimpleChannel("odc_rack");channel.registerMessage(RemoveHandler.class,Remove.class,0,Side.SERVER);channel.registerMessage(RenameHandler.class,Rename.class,1,Side.SERVER);}
    public static final class Remove implements IMessage {long pos;public Remove(){}Remove(BlockPos p){pos=p.toLong();}public void fromBytes(ByteBuf b){pos=b.readLong();}public void toBytes(ByteBuf b){b.writeLong(pos);}}
    public static final class RemoveHandler implements IMessageHandler<Remove,IMessage>{public IMessage onMessage(Remove m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{World w=p.world;BlockPos pos=BlockPos.fromLong(m.pos);if(!w.isBlockLoaded(pos)||p.getDistanceSq(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>36)return;Vec3d eye=p.getPositionEyes(1),look=p.getLook(1),end=new Vec3d(eye.x+look.x*6,eye.y+look.y*6,eye.z+look.z*6);RayTraceResult hit=w.rayTraceBlocks(eye,end,false,false,false);if(hit!=null&&hit.typeOfHit==RayTraceResult.Type.BLOCK&&pos.equals(hit.getBlockPos()))interact(w,pos,p,hit.sideHit,(float)(hit.hitVec.y-pos.getY()),true);});return null;}}
    public static final class Rename implements IMessage {int window,expected;String name;public Rename(){}Rename(int w,int e,String n){window=w;expected=e;name=n;}public void fromBytes(ByteBuf b){window=b.readInt();expected=b.readInt();name=net.minecraftforge.fml.common.network.ByteBufUtils.readUTF8String(b);}public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(expected);net.minecraftforge.fml.common.network.ByteBufUtils.writeUTF8String(b,name);}}
    public static final class RenameHandler implements IMessageHandler<Rename,IMessage>{public IMessage onMessage(Rename m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(p.openContainer instanceof PanelMenu&&p.openContainer.windowId==m.window)((PanelMenu)p.openContainer).rename(p,m.expected,m.name);});return null;}}
    static int bay(Tiles.Rack local,Tiles.Rack master,float y){double pitch=master.capacity()==9?1.875/9:.875/4;double v=y+(local.role==-1?1:0);return Math.max(0,Math.min(master.capacity()-1,(int)Math.floor((v-.0625)/pitch)));}
    static boolean interact(World w,BlockPos pos,EntityPlayer p,EnumFacing side,float y,boolean remove){if(w.isRemote)return true;Tiles.Rack local=RackMultiblock.at(w,pos),r=local==null?null:local.master();if(r==null||!r.allows(p)||side!=w.getBlockState(pos).getValue(Blocks.FACING))return true;int slot=bay(local,r,y),start=r.startAt(slot);ItemStack hand=p.getHeldItem(EnumHand.MAIN_HAND);
        if(remove){if(start<0)return true;ItemStack item=r.removeStackFromSlot(start);if(!item.isEmpty()&&!p.inventory.addItemStackToInventory(item)&&!item.isEmpty())p.dropItem(item,false);p.inventory.markDirty();return true;}
        if(start<0){if(Hardware.rackUnits(hand)>0&&r.isItemValidForSlot(slot,hand)){ItemStack one=hand.copy();one.setCount(1);if(p.capabilities.isCreativeMode&&Hardware.units(one)>0&&one.hasTagCompound())one.getTagCompound().removeTag("ServerIdMost");if(p.capabilities.isCreativeMode&&Hardware.units(one)>0&&one.hasTagCompound())one.getTagCompound().removeTag("ServerIdLeast");r.setInventorySlotContents(slot,one);if(!p.capabilities.isCreativeMode)hand.shrink(1);p.inventory.markDirty();}return true;}
        // No general rack GUI. The physical module determines the screen.
        BlockPos q=r.getPos();p.openGui(ODC.instance,(Hardware.switchTier(r.slots.get(start))>0?40:20)+start,w,q.getX(),q.getY(),q.getZ());return true;
    }
}
