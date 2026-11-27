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
        int window,tab,offset,action,value;UUID id;String text="";
        public Request(){} Request(int w,int t,int o,int a,int v,UUID i){window=w;tab=t;offset=o;action=a;value=v;id=i==null?new UUID(0,0):i;}
        Request(int w,int t,int o,int a,int v,UUID i,String text){this(w,t,o,a,v,i);this.text=text;}
        public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(tab);b.writeInt(offset);b.writeInt(action);b.writeInt(value);b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());ByteBufUtils.writeUTF8String(b,text); }
        public void fromBytes(ByteBuf b){window=b.readInt();tab=b.readInt();offset=b.readInt();action=b.readInt();value=b.readInt();id=new UUID(b.readLong(),b.readLong());text=ByteBufUtils.readUTF8String(b); }
    }
    public static final class RequestHandler implements IMessageHandler<Request,IMessage>{public IMessage onMessage(Request m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(!(p.openContainer instanceof PanelMenu)||p.openContainer.windowId!=m.window)return;PanelMenu menu=(PanelMenu)p.openContainer;if(!menu.canOperate(p))return;if(menu.type==1){if(m.action==2)menu.setPool(p,m.text);return;}if(menu.type!=0||m.tab<0||m.tab>=8||m.offset<0)return;if(m.action==0){menu.setTab(m.tab);menu.scrollRow=m.offset;if(m.tab==1)menu.serverQuery=DataOSUiState.Query.decode(m.text);if(m.tab==7)menu.eventQuery=EventJournal.Query.decode(m.text);}else if(m.tab==menu.tab)DataOSView.action(menu,m.id,m.action,m.value,m.text);menu.lastRefresh=Long.MIN_VALUE;});return null;}}
    public static final class View implements IMessage {int window;NBTTagCompound tag;public View(){}View(int w,NBTTagCompound t){window=w;tag=t;}public void toBytes(ByteBuf b){b.writeInt(window);ByteBufUtils.writeTag(b,tag);}public void fromBytes(ByteBuf b){window=b.readInt();tag=ByteBufUtils.readTag(b);}}
    public static final class ViewHandler implements IMessageHandler<View,IMessage>{public IMessage onMessage(View m,MessageContext ctx){net.minecraftforge.fml.common.FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(()->ClientView.accept(m));return null;}}
    @net.minecraftforge.fml.relauncher.SideOnly(Side.CLIENT)
    static final class ClientView {static void accept(View m){net.minecraft.client.entity.EntityPlayerSP p=net.minecraft.client.Minecraft.getMinecraft().player;if(p!=null&&p.openContainer instanceof PanelMenu&&p.openContainer.windowId==m.window&&m.tag!=null)((PanelMenu)p.openContainer).view=m.tag;}}
}
