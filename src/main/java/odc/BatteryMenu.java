package odc;
import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class BatteryMenu extends Container {
    final Tiles.Battery tile;final EntityPlayer player;
    final int[] data=new int[3],previous=new int[6];
    private BatteryMenu(EntityPlayer p,Tiles.Battery t){player=p;tile=t;Arrays.fill(previous,-1);}
    static BatteryMenu open(EntityPlayer p,World w,BlockPos pos){
        if(!w.isBlockLoaded(pos))return null;TileEntity t=w.getTileEntity(pos);
        if(!(t instanceof Tiles.Battery))return null;
        BatteryMenu b=new BatteryMenu(p,(Tiles.Battery)t);return w.isRemote||b.canInteractWith(p)?b:null;
    }
    @Override public boolean canInteractWith(EntityPlayer p){
        BlockPos q=tile.getPos();return p.world==tile.getWorld()&&Network.live(p.world,tile)&&tile.allows(p)&&p.getDistanceSq(q.getX()+.5,q.getY()+.5,q.getZ()+.5)<=64;
    }
    boolean save(EntityPlayer p,int watts){return !p.world.isRemote&&canInteractWith(p)&&tile.configure(watts);}
    @Override public boolean enchantItem(EntityPlayer p,int action){
        if(p.world.isRemote||action!=1||!canInteractWith(p))return false;
        PowerGrid.Domain d=PowerGrid.domain(p.world,tile.getPos());return d!=null&&d.state!=GridProtection.ON&&d.toggle();
    }
    @Override public void detectAndSendChanges(){
        super.detectAndSendChanges();if(player.world.isRemote||!canInteractWith(player))return;
        PowerGrid.Domain grid=PowerGrid.domain(player.world,tile.getPos());data[0]=tile.watts();data[1]=grid==null?GridProtection.OFF:grid.state;data[2]=grid!=null&&grid.complete?1:0;
        for(int i=0;i<data.length;i++)for(int part=0;part<2;part++){int id=i*2+part,value=(data[i]>>>(part*16))&65535;if(previous[id]!=value){for(IContainerListener l:listeners)l.sendWindowProperty(this,id,value);previous[id]=value;}}
    }
    @Override public void updateProgressBar(int id,int value){if(id<0||id>=previous.length)return;int i=id/2,v=value&65535;data[i]=id%2==0?(data[i]&0xffff0000)|v:(data[i]&65535)|(v<<16);}
}
