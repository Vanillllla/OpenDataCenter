package odc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import java.util.*;

public final class Menus {
    static final int W=248,H=232;
    static final int RACK=0, ASSEMBLY=1, CONTROL=2, MARKET=3, SWITCH=4, STATION=5;
    public static final class Handler implements IGuiHandler {
        private Menu menu(int id,EntityPlayer p,World w,int x,int y,int z){
            TileEntity t=w.getTileEntity(new BlockPos(x,y,z));
            boolean correct=(id==RACK&&t instanceof Tiles.Rack)||(id==ASSEMBLY&&t instanceof Tiles.Assembler)||((id==CONTROL||id==MARKET)&&t instanceof Tiles.Computer)||(id==SWITCH&&t instanceof Tiles.Switch)||(id==STATION&&t instanceof Tiles.Station);
            if(correct){Tiles.Owned tile=(Tiles.Owned)t;if(w.isRemote||tile.allows(p))return new Menu(id,p,tile);}return null;
        }
        @Override public Object getServerGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){return menu(id,p,w,x,y,z);}
        @Override public Object getClientGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){Menu m=menu(id,p,w,x,y,z);return m==null?null:new Client.Screen(m);}
    }
    public static final class Menu extends Container {
        final int type,machineSlots;final EntityPlayer player;final Tiles.Owned tile;
        final InventoryBasic offers=new InventoryBasic("Offers",false,4);
        final int[] data=new int[40],previous=new int[80];
        Menu(int type,EntityPlayer p,Tiles.Owned t){this.type=type;player=p;tile=t;Arrays.fill(previous,-1);
            if(type==RACK){Tiles.Rack r=(Tiles.Rack)t;for(int i=0;i<4;i++)addSlotToContainer(new Slot(r,i,14,49+i*23){@Override public int getSlotStackLimit(){return 1;}});machineSlots=4;}
            else if(type==ASSEMBLY){Tiles.Assembler a=(Tiles.Assembler)t;addSlotToContainer(new Slot(a,0,20,43));addSlotToContainer(new Slot(a,1,62,43));for(int i=0;i<4;i++)addSlotToContainer(new Slot(a,i+2,20+i*43,86));addSlotToContainer(new Slot(a,6,212,43){@Override public boolean isItemValid(ItemStack s){return false;}});machineSlots=7;}
            else if(type==MARKET){for(int i=0;i<4;i++){if(!p.world.isRemote&&i<ODC.market.length)offers.setInventorySlotContents(i,ODC.market[i].stack());addSlotToContainer(new Slot(offers,i,26+i*60,82){@Override public boolean isItemValid(ItemStack s){return false;}@Override public boolean canTakeStack(EntityPlayer p){return false;}});}machineSlots=4;}
            else machineSlots=0;
            for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlotToContainer(new Slot(p.inventory,c+r*9+9,43+c*18,155+r*18));
            for(int c=0;c<9;c++)addSlotToContainer(new Slot(p.inventory,c,43+c*18,213));
        }
        @Override public boolean canInteractWith(EntityPlayer p){return tile.getWorld()!=null&&tile.getWorld().getTileEntity(tile.getPos())==tile&&p.getDistanceSq(tile.getPos().getX()+.5,tile.getPos().getY()+.5,tile.getPos().getZ()+.5)<=64&&tile.allows(p);}
        @Override public ItemStack slotClick(int slot,int drag,ClickType click,EntityPlayer p){if(type==MARKET&&slot>=0&&slot<4)return ItemStack.EMPTY;return super.slotClick(slot,drag,click,p);}
        @Override public ItemStack transferStackInSlot(EntityPlayer p,int index){
            if(index<0||index>=inventorySlots.size()||(type!=RACK&&type!=ASSEMBLY))return ItemStack.EMPTY;
            Slot sl=inventorySlots.get(index);if(!sl.getHasStack()||!sl.canTakeStack(p))return ItemStack.EMPTY;
            ItemStack stack=sl.getStack(),old=stack.copy();
            if(index<machineSlots){if(!mergeItemStack(stack,machineSlots,inventorySlots.size(),true))return ItemStack.EMPTY;}
            else {int max=type==ASSEMBLY?6:4;if(!mergeItemStack(stack,0,max,false))return ItemStack.EMPTY;}
            if(stack.isEmpty())sl.putStack(ItemStack.EMPTY);else sl.onSlotChanged();if(stack.getCount()==old.getCount())return ItemStack.EMPTY;sl.onTake(p,stack);return old;
        }
        void open(EntityPlayer p,int id){BlockPos b=tile.getPos();p.openGui(ODC.instance,id,p.world,b.getX(),b.getY(),b.getZ());}
        @Override public boolean enchantItem(EntityPlayer p,int id){
            if(p.world.isRemote||!canInteractWith(p))return false;
            if(type==ASSEMBLY)return id==0&&((Tiles.Assembler)tile).assemble();
            if(type==RACK){Tiles.Rack r=(Tiles.Rack)tile;if(id==9)return r.cycleController();return id>=1&&id<=4&&r.cycleTask(id-1);}
            if(type==CONTROL){Tiles.Computer c=(Tiles.Computer)tile;if(id==20){open(p,MARKET);return true;}if(c.online&&id>=1&&id<=3){c.toggle(id);return true;}return false;}
            if(type==MARKET){if(id==21){open(p,CONTROL);return true;}return ((Tiles.Computer)tile).online&&id>=10&&id<14&&Market.buy(p,id-10);}
            return false;
        }
        void refresh(){if(player.world.isRemote)return;
            if(type==RACK){Tiles.Rack r=(Tiles.Rack)tile;data[0]=r.energy.getEnergyStored();data[1]=r.lastPower;data[2]=r.totalMbps;data[3]=r.controller==null?0:r.controllerPosition==null?1:2;
                BlockPos pos=r.controllerPosition;data[4]=pos==null?0:pos.getX();data[5]=pos==null?0:pos.getY();data[6]=pos==null?0:pos.getZ();
                for(int i=0;i<4;i++){data[7+i]=r.startAt(i)+1;data[11+i]=r.assignments[i];data[15+i]=r.status[i];data[19+i]=r.usedMbps[i];data[23+i]=r.neededMbps[i];data[27+i]=r.work[i];}data[31]=r.totalReward;
            }else if(type==CONTROL||type==MARKET){Tiles.Computer c=(Tiles.Computer)tile;data[0]=Market.Ledger.get(player.world).balance(player.getUniqueID());data[1]=c.online?1:0;data[2]=c.energy.getEnergyStored();
                if(type==MARKET){for(int i=0;i<4;i++)data[3+i]=i<ODC.market.length&&!offers.getStackInSlot(i).isEmpty()?ODC.market[i].priceMilli:0;}
                else {data[3]=c.racks;data[4]=c.servers;data[5]=c.uplink;data[6]=c.switching;data[7]=c.networkUsed;data[8]=c.enabledMask;data[27]=c.clusterPower;
                    for(int i=0;i<3;i++)for(int j=0;j<6;j++)data[9+i*6+j]=c.jobStats[i][j];}
            }else if(type==SWITCH||type==STATION){Tiles.Powered p=(Tiles.Powered)tile;data[0]=p.online?1:0;data[1]=type==SWITCH?ODC.switchBandwidth:ODC.stationBandwidth;data[2]=p.energy.getEnergyStored();data[3]=p.idlePower();}
        }
        @Override public void detectAndSendChanges(){super.detectAndSendChanges();if(player.world.isRemote)return;refresh();
            for(int i=0;i<data.length;i++){int low=data[i]&65535,hi=data[i]>>>16;
                if(previous[i*2]!=low){for(IContainerListener l:listeners)l.sendWindowProperty(this,i*2,low);previous[i*2]=low;}
                if(previous[i*2+1]!=hi){for(IContainerListener l:listeners)l.sendWindowProperty(this,i*2+1,hi);previous[i*2+1]=hi;}}
        }
        @Override public void updateProgressBar(int id,int value){if(id<0||id>=data.length*2)return;int i=id/2,v=value&65535;data[i]=(id%2==0)?(data[i]&0xffff0000)|v:(data[i]&65535)|(v<<16);}
    }
}
