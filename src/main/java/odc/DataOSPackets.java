package odc;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Window-scoped messages. Scroll offsets never grant control of arbitrary remote devices. */
public final class DataOSPackets {
    static void register(SimpleNetworkWrapper c){c.registerMessage(TaskCommand.Handler.class,TaskCommand.class,4,Side.SERVER);c.registerMessage(RequestHandler.class,Request.class,2,Side.SERVER);c.registerMessage(ViewHandler.class,View.class,3,Side.CLIENT);}
    public static final class Request implements IMessage {
        int window,tab,offset,action,value;UUID id;
        public Request(){} Request(int w,int t,int o,int a,int v,UUID i){window=w;tab=t;offset=o;action=a;value=v;id=i==null?new UUID(0,0):i;}
        public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(tab);b.writeInt(offset);b.writeInt(action);b.writeInt(value);b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());}
        public void fromBytes(ByteBuf b){window=b.readInt();tab=b.readInt();offset=b.readInt();action=b.readInt();value=b.readInt();id=new UUID(b.readLong(),b.readLong());}
    }
    public static final class RequestHandler implements IMessageHandler<Request,IMessage>{public IMessage onMessage(Request m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(!(p.openContainer instanceof PanelMenu)||p.openContainer.windowId!=m.window)return;PanelMenu menu=(PanelMenu)p.openContainer;if(menu.type!=0||!menu.canInteractWith(p)||m.tab<0||m.tab>=9||m.offset<0)return;if(m.action==0){menu.tab=m.tab;menu.scrollRow=m.offset;}else if(m.tab==menu.tab)DataOSView.action(menu,m.id,m.action,m.value);menu.lastRefresh=Long.MIN_VALUE;});return null;}}
    public static final class View implements IMessage {int window;NBTTagCompound tag;public View(){}View(int w,NBTTagCompound t){window=w;tag=t;}public void toBytes(ByteBuf b){b.writeInt(window);ByteBufUtils.writeTag(b,tag);}public void fromBytes(ByteBuf b){window=b.readInt();tag=ByteBufUtils.readTag(b);}}
    public static final class ViewHandler implements IMessageHandler<View,IMessage>{public IMessage onMessage(View m,MessageContext ctx){net.minecraftforge.fml.common.FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(()->ClientView.accept(m));return null;}}
    @net.minecraftforge.fml.relauncher.SideOnly(Side.CLIENT)
    static final class ClientView {static void accept(View m){net.minecraft.client.entity.EntityPlayerSP p=net.minecraft.client.Minecraft.getMinecraft().player;if(p!=null&&p.openContainer instanceof PanelMenu&&p.openContainer.windowId==m.window&&m.tag!=null)((PanelMenu)p.openContainer).view=m.tag;}}
}
