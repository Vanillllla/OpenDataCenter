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
    static void init(){channel=NetworkRegistry.INSTANCE.newSimpleChannel("odc_rack");channel.registerMessage(RemoveHandler.class,Remove.class,0,Side.SERVER);channel.registerMessage(RenameHandler.class,Rename.class,1,Side.SERVER);channel.registerMessage(DoorHandler.class,DoorClick.class,8,Side.SERVER);DataOSPackets.register(channel);NetworkMapPackets.register(channel);channel.registerMessage(NetworkEditCommand.Handler.class,NetworkEditCommand.class,7,Side.SERVER);channel.registerMessage(UplinkCommand.Handler.class,UplinkCommand.class,5,Side.SERVER);}
    public static final class DoorClick implements IMessage {
        long pos;public DoorClick(){}DoorClick(BlockPos p){pos=p.toLong();}
        public void fromBytes(ByteBuf b){pos=b.readLong();}public void toBytes(ByteBuf b){b.writeLong(pos);}
    }
    static double doorHit(Tiles.Rack r,Vec3d eye,Vec3d end,RayTraceResult obstruction){
        if(r==null||r.role==-1||!r.glassRack())return -1;
        BlockPos p=r.getPos();EnumFacing front=r.getWorld().getBlockState(p).getValue(Blocks.FACING);
        int facing=front.getHorizontalIndex();
        double[] a=RackLayout.Door.local(eye.x-p.getX(),eye.y-p.getY(),eye.z-p.getZ(),facing);
        double[] b=RackLayout.Door.local(end.x-p.getX(),end.y-p.getY(),end.z-p.getZ(),facing);
        double t=RackLayout.Door.hit(a,b,RackLayout.Door.angle(r.doorProgress(0)),r.role==1?2:1);
        if(t<0)return -1;
        if(obstruction!=null&&obstruction.typeOfHit==RayTraceResult.Type.BLOCK){
            double distance=eye.distanceTo(end)*t,wall=eye.distanceTo(obstruction.hitVec);
            BlockPos q=obstruction.getBlockPos();
            boolean ownFront=(q.equals(p)||r.role==1&&q.equals(p.up()))&&obstruction.sideHit==front;
            if(wall+.015<distance&&!(ownFront&&wall+.16>=distance))return -1;
        }
        return t;
    }
    public static final class DoorHandler implements IMessageHandler<DoorClick,IMessage>{
        public IMessage onMessage(DoorClick m,MessageContext ctx){
            EntityPlayerMP p=ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(()->{
                World w=p.world;BlockPos pos=BlockPos.fromLong(m.pos);Tiles.Rack r=RackMultiblock.at(w,pos);
                if(r==null||r.role==-1||!r.glassRack()||!r.allows(p)||p.isSpectator())return;
                double reach=Math.min(6,p.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());
                Vec3d eye=p.getPositionEyes(1),look=p.getLook(1),end=eye.addVector(look.x*reach,look.y*reach,look.z*reach);
                if(doorHit(r,eye,end,w.rayTraceBlocks(eye,end,false,false,false))>=0)r.toggleDoor();
            });return null;
        }
    }
    public static final class Remove implements IMessage {long pos;public Remove(){}Remove(BlockPos p){pos=p.toLong();}public void fromBytes(ByteBuf b){pos=b.readLong();}public void toBytes(ByteBuf b){b.writeLong(pos);}}
    public static final class RemoveHandler implements IMessageHandler<Remove,IMessage>{public IMessage onMessage(Remove m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{World w=p.world;BlockPos pos=BlockPos.fromLong(m.pos);if(!w.isBlockLoaded(pos)||p.getDistanceSq(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>36)return;Vec3d eye=p.getPositionEyes(1),look=p.getLook(1),end=new Vec3d(eye.x+look.x*6,eye.y+look.y*6,eye.z+look.z*6);RayTraceResult hit=w.rayTraceBlocks(eye,end,false,false,false);if(hit!=null&&hit.typeOfHit==RayTraceResult.Type.BLOCK&&pos.equals(hit.getBlockPos()))interact(w,pos,p,hit.sideHit,(float)(hit.hitVec.y-pos.getY()),true);});return null;}}
    public static final class Rename implements IMessage {int window,expected;String name;public Rename(){}Rename(int w,int e,String n){window=w;expected=e;name=n;}public void fromBytes(ByteBuf b){window=b.readInt();expected=b.readInt();name=net.minecraftforge.fml.common.network.ByteBufUtils.readUTF8String(b);}public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(expected);net.minecraftforge.fml.common.network.ByteBufUtils.writeUTF8String(b,name);}}
    public static final class RenameHandler implements IMessageHandler<Rename,IMessage>{public IMessage onMessage(Rename m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(p.openContainer instanceof PanelMenu&&p.openContainer.windowId==m.window)((PanelMenu)p.openContainer).rename(p,m.expected,m.name);});return null;}}
    static int bay(Tiles.Rack local,Tiles.Rack master,float y){double pitch=master.capacity()==9?1.875/9:.875/4;double v=y+(local.role==-1?1:0);return Math.max(0,Math.min(master.capacity()-1,(int)Math.floor((v-.0625)/pitch)));}
    static boolean interact(World w,BlockPos pos,EntityPlayer p,EnumFacing side,float y,boolean remove){if(w.isRemote)return true;Tiles.Rack local=RackMultiblock.at(w,pos),r=local==null?null:local.master();if(r==null||!r.allows(p)||side!=w.getBlockState(pos).getValue(Blocks.FACING))return true;if(r.glassRack()){if(!remove&&!r.doorOpen){r.toggleDoor();return true;}if(!r.doorAllowsAccess())return true;}int slot=bay(local,r,y),start=r.startAt(slot);ItemStack hand=p.getHeldItem(EnumHand.MAIN_HAND);
        if(remove){if(start<0)return true;ItemStack item=r.removeStackFromSlot(start);if(!item.isEmpty()&&!p.inventory.addItemStackToInventory(item)&&!item.isEmpty())p.dropItem(item,false);p.inventory.markDirty();return true;}
        if(start<0){if(Hardware.rackUnits(hand)>0&&r.isItemValidForSlot(slot,hand)){ItemStack one=hand.copy();one.setCount(1);if(p.capabilities.isCreativeMode&&Hardware.units(one)>0&&one.hasTagCompound())one.getTagCompound().removeTag("ServerIdMost");if(p.capabilities.isCreativeMode&&Hardware.units(one)>0&&one.hasTagCompound())one.getTagCompound().removeTag("ServerIdLeast");if(p.capabilities.isCreativeMode&&Hardware.switchTier(one)>0&&one.hasTagCompound()){one.getTagCompound().removeTag("SwitchIdMost");one.getTagCompound().removeTag("SwitchIdLeast");}r.setInventorySlotContents(slot,one);if(!p.capabilities.isCreativeMode)hand.shrink(1);p.inventory.markDirty();}return true;}
        // No general rack GUI. The physical module determines the screen.
        BlockPos q=r.getPos();p.openGui(ODC.instance,(Hardware.switchTier(r.slots.get(start))>0?40:20)+start,w,q.getX(),q.getY(),q.getZ());return true;
    }
}
