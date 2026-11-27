package odc;
import java.util.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
/** Window-bound editor operations; client supplies only IDs and coordinates. */
public final class NetworkEditCommand implements IMessage {
 int window,op,x,y; UUID a,b;
 public NetworkEditCommand(){}
 NetworkEditCommand(int window,int op,UUID a,UUID b,int x,int y){this.window=window;this.op=op;this.a=a==null?new UUID(0,0):a;this.b=b==null?new UUID(0,0):b;this.x=x;this.y=y;}
 private static void put(ByteBuf b,UUID id){b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());}
 private static UUID get(ByteBuf b){return new UUID(b.readLong(),b.readLong());}
 public void toBytes(ByteBuf buf){buf.writeInt(window);buf.writeInt(op);put(buf,a);put(buf,b);buf.writeInt(x);buf.writeInt(y);}
 public void fromBytes(ByteBuf buf){window=buf.readInt();op=buf.readInt();a=get(buf);b=get(buf);x=buf.readInt();y=buf.readInt();}
 public static final class Handler implements IMessageHandler<NetworkEditCommand,IMessage>{public IMessage onMessage(NetworkEditCommand m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(!(p.openContainer instanceof Menus.Menu)||p.openContainer.windowId!=m.window)return;Menus.Menu menu=(Menus.Menu)p.openContainer;if(!menu.networkMap()||!menu.canInteractWith(p))return;Tiles.NetworkTerminal root=(Tiles.NetworkTerminal)menu.tile;boolean ok=PhysicalNetwork.editMap(p,root,m.op,m.a,m.b,m.x,m.y);menu.mapFeedback=ok?1:2;menu.mapEditSerial++;menu.lastMapTick=Long.MIN_VALUE;});return null;}}
}
