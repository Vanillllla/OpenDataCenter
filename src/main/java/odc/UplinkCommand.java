package odc;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
public final class UplinkCommand implements IMessage {
 int window,op,tier; UUID target;
 public UplinkCommand(){}
 UplinkCommand(int w,UUID id,int op,int tier){window=w;target=id;this.op=op;this.tier=tier;}
 public void toBytes(ByteBuf b){b.writeInt(window);b.writeLong(target.getMostSignificantBits());b.writeLong(target.getLeastSignificantBits());b.writeInt(op);b.writeInt(tier);}
 public void fromBytes(ByteBuf b){window=b.readInt();target=new UUID(b.readLong(),b.readLong());op=b.readInt();tier=b.readInt();}
 public static final class Handler implements IMessageHandler<UplinkCommand,IMessage>{public IMessage onMessage(UplinkCommand m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(!(p.openContainer instanceof Menus.Menu)||p.openContainer.windowId!=m.window)return;Menus.Menu menu=(Menus.Menu)p.openContainer;if(menu.type==Menus.STATION&&menu.tile instanceof Tiles.NetworkTerminal&&menu.canInteractWith(p))PhysicalNetwork.editUplink(p,(Tiles.NetworkTerminal)menu.tile,m.target,m.op,m.tier);});return null;}}
}
