package odc;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraft.entity.player.EntityPlayerMP;

public final class TaskCommand implements IMessage {
    int window,revision,op,value;UUID key;int[] limits=new int[14];
    public TaskCommand(){}
    TaskCommand(int w,int r,int o,int v,UUID k,int[] l){window=w;revision=r;op=o;value=v;key=k;if(l!=null)limits=java.util.Arrays.copyOf(l,14);}
    public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(revision);b.writeInt(op);b.writeInt(value);b.writeLong(key.getMostSignificantBits());b.writeLong(key.getLeastSignificantBits());for(int n:limits)b.writeInt(n);}
    public void fromBytes(ByteBuf b){window=b.readInt();revision=b.readInt();op=b.readInt();value=b.readInt();key=new UUID(b.readLong(),b.readLong());for(int i=0;i<limits.length;i++)limits[i]=b.readInt();}
    public static class Handler implements IMessageHandler<TaskCommand,IMessage>{
        public IMessage onMessage(TaskCommand msg,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!(player.openContainer instanceof PanelMenu))return;
                PanelMenu m=(PanelMenu)player.openContainer;
                if(m.type==0 && m.tab==2 && m.windowId==msg.window && m.canInteractWith(player) && m.console.revision==msg.revision){
                    if(((Tiles.Computer)m.tile).online)m.console.command(m,TaskData.get(player.world),msg);m.console.revision++;m.lastRefresh=Long.MIN_VALUE;
                }
            });return null;
        }
    }
}
