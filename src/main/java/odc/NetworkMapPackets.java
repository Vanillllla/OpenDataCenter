package odc;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Bounded record batches, committed atomically on the client. Window scoped. */
public final class NetworkMapPackets {
    static void register(SimpleNetworkWrapper channel){channel.registerMessage(Handler.class,Page.class,6,Side.CLIENT);}
    static void send(Menus.Menu menu,NBTTagCompound snapshot){
        NBTTagCompound header=snapshot.copy();header.removeTag("Nodes");header.removeTag("Links");NBTTagList nodes=snapshot.getTagList("Nodes",10),links=snapshot.getTagList("Links",10);int sequence=++menu.mapSequence;
        header.setInteger("NodeCount",nodes.tagCount());header.setInteger("LinkCount",links.tagCount());send(menu,sequence,0,header);
        pages(menu,sequence,1,nodes);pages(menu,sequence,2,links);send(menu,sequence,3,new NBTTagCompound());
    }
    private static void pages(Menus.Menu m,int seq,int kind,NBTTagList list){for(int i=0;i<list.tagCount();i+=48){NBTTagCompound tag=new NBTTagCompound();NBTTagList part=new NBTTagList();for(int j=i;j<Math.min(i+48,list.tagCount());j++)part.appendTag(list.getCompoundTagAt(j));tag.setTag("Rows",part);send(m,seq,kind,tag);}}
    private static void send(Menus.Menu m,int seq,int kind,NBTTagCompound tag){RackInteraction.channel.sendTo(new Page(m.windowId,seq,kind,tag),(EntityPlayerMP)m.player);}
    public static final class Page implements IMessage {int window,seq,kind;NBTTagCompound tag;public Page(){}Page(int w,int s,int k,NBTTagCompound t){window=w;seq=s;kind=k;tag=t;}public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(seq);b.writeByte(kind);ByteBufUtils.writeTag(b,tag);}public void fromBytes(ByteBuf b){window=b.readInt();seq=b.readInt();kind=b.readUnsignedByte();tag=ByteBufUtils.readTag(b);}}
    public static final class Handler implements IMessageHandler<Page,IMessage>{public IMessage onMessage(Page m,MessageContext ctx){net.minecraftforge.fml.common.FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(()->Client.accept(m));return null;}}
    @SideOnly(Side.CLIENT) static final class Client {static void accept(Page m){net.minecraft.client.entity.EntityPlayerSP p=net.minecraft.client.Minecraft.getMinecraft().player;if(p!=null&&p.openContainer instanceof Menus.Menu&&p.openContainer.windowId==m.window&&m.tag!=null){Menus.Menu menu=(Menus.Menu)p.openContainer;if(menu.networkMap())menu.acceptMap(m.seq,m.kind,m.tag);}}}
}
