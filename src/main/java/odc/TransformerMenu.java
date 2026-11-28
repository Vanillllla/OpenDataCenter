package odc;

import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Owner-checked, server-authoritative, usable without grid power. */
final class TransformerMenu extends Container {
    static final int TOGGLE=1;
    final Tiles.Transformer tile;
    final EntityPlayer player;
    // tier, rating, funded output, paid input, enabled, grid funded, grid rated, HE rate, FE rate
    final int[] data=new int[10],previous=new int[20];
    private TransformerMenu(EntityPlayer p,Tiles.Transformer t){player=p;tile=t;Arrays.fill(previous,-1);}
    static TransformerMenu open(EntityPlayer p,World w,BlockPos pos){
        if(!w.isBlockLoaded(pos))return null;
        TileEntity t=w.getTileEntity(pos);
        if(!(t instanceof Tiles.Transformer))return null;
        TransformerMenu menu=new TransformerMenu(p,(Tiles.Transformer)t);
        return w.isRemote||menu.canInteractWith(p)?menu:null;
    }
    @Override public boolean canInteractWith(EntityPlayer p){
        BlockPos pos=tile.getPos();
        return p.world==tile.getWorld()&&Network.live(p.world,tile)&&tile.allows(p)&&p.getDistanceSq(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;
    }
    @Override public boolean enchantItem(EntityPlayer p,int action){
        if(p.world.isRemote||action!=TOGGLE||!canInteractWith(p))return false;
        PowerGrid.Domain d=PowerGrid.domain(p.world,tile.getPos());return d!=null&&d.toggle();
    }
    private static int bounded(long value){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,value));}
    @Override public void detectAndSendChanges(){
        super.detectAndSendChanges();
        if(player.world.isRemote||!canInteractWith(player))return;
        PowerGrid.Domain d=PowerGrid.domain(player.world,tile.getPos());
        data[0]=tile.tier();data[1]=bounded(tile.ratedWatts());data[2]=bounded(tile.actualOutputWatts());
        data[3]=bounded(tile.actualInputUnits());data[4]=d==null?GridProtection.OFF:d.state;
        data[5]=d==null?0:bounded(d.budget.capacityWatts);data[6]=d==null?0:bounded(d.ratedWatts());
        data[9]=d!=null&&d.complete?1:0;data[7]=ODC.conversionRate(TransformerCore.Input.HE);data[8]=ODC.conversionRate(TransformerCore.Input.FE);
        for(int i=0;i<data.length;i++)for(int part=0;part<2;part++){
            int id=i*2+part,value=(data[i]>>>(part*16))&65535;
            if(previous[id]!=value){for(IContainerListener listener:listeners)listener.sendWindowProperty(this,id,value);previous[id]=value;}
        }
    }
    @Override public void updateProgressBar(int id,int value){
        if(id<0||id>=previous.length)return;
        int i=id/2,v=value&65535;
        data[i]=id%2==0?(data[i]&0xffff0000)|v:(data[i]&65535)|(v<<16);
    }
}
