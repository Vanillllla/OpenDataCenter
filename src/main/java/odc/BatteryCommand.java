package odc;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
public final class BatteryCommand implements IMessage {
    int window,watts;
    public BatteryCommand(){}
    BatteryCommand(int window,int watts){this.window=window;this.watts=watts;}
    @Override public void fromBytes(ByteBuf b){window=b.readInt();watts=b.readInt();}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(watts);}
    static void register(SimpleNetworkWrapper c){c.registerMessage(Handler.class,BatteryCommand.class,9,Side.SERVER);}
    public static final class Handler implements IMessageHandler<BatteryCommand,IMessage>{
        @Override public IMessage onMessage(BatteryCommand m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{
            if(p.openContainer instanceof BatteryMenu&&p.openContainer.windowId==m.window)((BatteryMenu)p.openContainer).save(p,m.watts);
        });return null;}
    }
}
