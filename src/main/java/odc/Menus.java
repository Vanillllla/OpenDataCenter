package odc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import java.util.*;

public final class Menus {
    static final int W=300,H=240;
    static final int RACK=0,WORKBENCH=1,CONTROL=2,MARKET=3,STATION=5;
    public static final class Handler implements IGuiHandler {
        private Menu menu(int id,EntityPlayer p,World w,int x,int y,int z){TileEntity t=w.getTileEntity(new BlockPos(x,y,z));boolean correct=id==RACK&&t instanceof Tiles.Rack||id==WORKBENCH&&t instanceof Tiles.Workbench||(id==CONTROL||id==MARKET)&&t instanceof Tiles.Computer||id==STATION&&t instanceof Tiles.Station;
            if(!correct)return null;Tiles.Owned tile=(Tiles.Owned)t;if(!w.isRemote&&(!tile.allows(p)||p.getDistanceSq(x+.5,y+.5,z+.5)>64||id==WORKBENCH&&((Tiles.Workbench)t).editor!=null))return null;return new Menu(id,p,tile);}
        private PanelMenu panel(int id,EntityPlayer p,World w,int x,int y,int z){TileEntity t=w.getTileEntity(new BlockPos(x,y,z));if(id==CONTROL&&!(t instanceof Tiles.Computer)||id!=CONTROL&&!(t instanceof Tiles.Rack))return null;Tiles.Owned a=(Tiles.Owned)t;if(!w.isRemote&&(!a.allows(p)||p.getDistanceSq(x+.5,y+.5,z+.5)>64))return null;if(id!=CONTROL){Tiles.Rack r=(Tiles.Rack)t;int bay=id%20;if(!w.isRemote&&(bay>=r.capacity()||(id<40?!r.isServer(bay):Hardware.switchTier(r.slots.get(bay))==0)))return null;}return new PanelMenu(id,p,a);}
        private boolean isPanel(int id){return id==CONTROL||id>=20&&id<=28||id>=40&&id<=48;}
        @Override public Object getServerGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){return isPanel(id)?panel(id,p,w,x,y,z):menu(id,p,w,x,y,z);}
        @Override public Object getClientGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){if(isPanel(id)){PanelMenu a=panel(id,p,w,x,y,z);return a==null?null:new PanelScreen(a);}Menu m=menu(id,p,w,x,y,z);return m==null?null:new Client.Screen(m);}
    }
    /** A write-through inventory view. Never stores a duplicate of the chassis contents. */
    static final class Parts extends InventoryBasic {
        final Tiles.Workbench bench;
        Parts(Tiles.Workbench b){super("Hardware",false,ServerSpec.COUNT);bench=b;}
        @Override public ItemStack getStackInSlot(int i){return bench.parts().get(i);}
        @Override public boolean isEmpty(){for(ItemStack s:bench.parts())if(!s.isEmpty())return false;return true;}
        @Override public ItemStack decrStackSize(int i,int count){if(count<=0)return ItemStack.EMPTY;NonNullList<ItemStack> p=bench.parts();ItemStack s=p.get(i);p.set(i,ItemStack.EMPTY);bench.write(p);return s;}
        @Override public ItemStack removeStackFromSlot(int i){return decrStackSize(i,1);}
        @Override public void setInventorySlotContents(int i,ItemStack s){if(bench.chassis()==0)return;NonNullList<ItemStack> p=bench.parts();ItemStack one=s.copy();if(!one.isEmpty())one.setCount(1);p.set(i,one);bench.write(p);}
        @Override public int getInventoryStackLimit(){return 1;}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return bench.chassis()>0&&Hardware.accepts(i,s)&&ServerSpec.fits(bench.chassis(),Hardware.spec(bench.slots.get(0))[0],i);}
    }
    static final class Target {final Tiles.Rack rack;final int bay;final ItemStack item;Target(Tiles.Rack r,int b){rack=r;bay=b;item=r.slots.get(b);}}
    public static final class Menu extends Container {
        final int type,machineSlots,revision;final EntityPlayer player;final Tiles.Owned tile;final Parts parts;
        final InventoryBasic offers=new InventoryBasic("Offers",false,4);
        final int[] data=new int[100],previous=new int[200];int page,selectedPort;boolean pageDirty=true;long lastTopology=Long.MIN_VALUE;final List<Target> visible=new ArrayList<>();List<Target> snapshot=new ArrayList<>(),reachable=new ArrayList<>();
        Menu(int id,EntityPlayer p,Tiles.Owned t){type=id;player=p;tile=t;revision=t instanceof Tiles.Rack?((Tiles.Rack)t).revision:0;Arrays.fill(previous,-1);
            if(id==WORKBENCH){Tiles.Workbench a=(Tiles.Workbench)t;if(!p.world.isRemote)a.editor=this;parts=new Parts(a);addSlotToContainer(new Slot(a,0,12,37));for(int i=0;i<ServerSpec.COUNT;i++){final int j=i;addSlotToContainer(new Slot(parts,i,partX(i),partY(i)){
                    @Override public int getSlotStackLimit(){return 1;}
                    @Override public boolean isItemValid(ItemStack s){return parts.isItemValidForSlot(j,s);}
                    @Override public boolean canTakeStack(EntityPlayer p){return j!=0||ServerSpec.canRemoveBoard(Hardware.spec(a.slots.get(0)));}
                });}machineSlots=1+ServerSpec.COUNT;
            }else{parts=null;if(id==RACK){Tiles.Rack r=(Tiles.Rack)t;for(int i=0;i<9;i++){final int j=i;addSlotToContainer(new Slot(r,i,rackX(i),rackY(i)){
                    @Override public int getSlotStackLimit(){return 1;}
                    @Override public boolean isItemValid(ItemStack s){return r.isItemValidForSlot(j,s);}
                    @Override public boolean isEnabled(){return j<r.capacity();}
                    @Override public boolean canTakeStack(EntityPlayer p){return j<r.capacity();}
                });}machineSlots=9;}
                else if(id==MARKET){for(int i=0;i<4;i++){if(!p.world.isRemote&&i<ODC.market.length)offers.setInventorySlotContents(i,ODC.market[i].stack());addSlotToContainer(new Slot(offers,i,25+i*68,72){@Override public boolean isItemValid(ItemStack s){return false;}@Override public boolean canTakeStack(EntityPlayer p){return false;}});}machineSlots=4;}else machineSlots=0;}
            for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlotToContainer(new Slot(p.inventory,c+r*9+9,70+c*18,162+r*18));for(int c=0;c<9;c++)addSlotToContainer(new Slot(p.inventory,c,70+c*18,220));
        }
        static int rackX(int i){return i<5?12:156;}static int rackY(int i){return 37+(i<5?i:i-5)*20;}
        static int partX(int i){if(i==0)return 46;if(i==1)return 82;if(i<ServerSpec.SATA)return 12+(i-2)*18;if(i<ServerSpec.NVME)return 12+(i-10)*18;if(i<ServerSpec.GPU)return 183+(i-18)*18;if(i<ServerSpec.PSU)return 183+(i-20)*18;if(i<ServerSpec.NIC)return 183+(i-24)*18;return 118;}
        static int partY(int i){return i<2||i==ServerSpec.NIC?37:i<ServerSpec.SATA?70:i<ServerSpec.NVME?105:i<ServerSpec.GPU?37:i<ServerSpec.PSU?70:105;}
        int chassis(){return type==WORKBENCH?((Tiles.Workbench)tile).chassis():0;}
        boolean fits(int i){return type==WORKBENCH&&ServerSpec.fits(chassis(),Hardware.spec(((Tiles.Workbench)tile).slots.get(0))[0],i);}
        @Override public void onContainerClosed(EntityPlayer p){super.onContainerClosed(p);if(type==WORKBENCH&&!p.world.isRemote&&((Tiles.Workbench)tile).editor==this)((Tiles.Workbench)tile).editor=null;}
        @Override public boolean canInteractWith(EntityPlayer p){return tile.getWorld()==p.world&&p.world.getTileEntity(tile.getPos())==tile&&p.getDistanceSq(tile.getPos().getX()+.5,tile.getPos().getY()+.5,tile.getPos().getZ()+.5)<=64&&tile.allows(p)&&(type!=RACK||((Tiles.Rack)tile).role!=-1&&((Tiles.Rack)tile).revision==revision)&&(type!=WORKBENCH||p.world.isRemote||((Tiles.Workbench)tile).editor==this);}
        @Override public ItemStack slotClick(int slot,int drag,ClickType click,EntityPlayer p){if(!p.world.isRemote&&!canInteractWith(p)||type==MARKET&&slot>=0&&slot<4)return ItemStack.EMPTY;
            if(slot>=0&&slot<machineSlots){if(click==ClickType.QUICK_CRAFT||click==ClickType.SWAP||click==ClickType.PICKUP_ALL||click==ClickType.CLONE)return ItemStack.EMPTY;if(type==RACK&&slot>=((Tiles.Rack)tile).capacity())return ItemStack.EMPTY;}
            // Double click/drag from player inventory could otherwise bypass protected slots.
            if((type==WORKBENCH||type==RACK)&&(click==ClickType.PICKUP_ALL||click==ClickType.QUICK_CRAFT))return ItemStack.EMPTY;
            return super.slotClick(slot,drag,click,p);
        }
        @Override public ItemStack transferStackInSlot(EntityPlayer p,int index){if(!canInteractWith(p)||index<0||index>=inventorySlots.size()||type!=RACK&&type!=WORKBENCH)return ItemStack.EMPTY;Slot src=inventorySlots.get(index);if(!src.getHasStack()||!src.canTakeStack(p))return ItemStack.EMPTY;ItemStack stack=src.getStack(),old=stack.copy();
            if(index<machineSlots){if(!mergeItemStack(stack,machineSlots,inventorySlots.size(),true))return ItemStack.EMPTY;src.putStack(stack.isEmpty()?ItemStack.EMPTY:stack);}
            else{boolean moved=false;for(int i=0;i<machineSlots&&!stack.isEmpty();i++){Slot dest=inventorySlots.get(i);if(!dest.getHasStack()&&dest.isItemValid(stack)){dest.putStack(stack.splitStack(1));moved=true;}}if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())src.putStack(ItemStack.EMPTY);else src.onSlotChanged();}return old;}
        void open(EntityPlayer p,int id){BlockPos b=tile.getPos();p.openGui(ODC.instance,id,p.world,b.getX(),b.getY(),b.getZ());}
        List<Target> targets(){List<Target> out=new ArrayList<>();Network.Domain d=Network.scan(player.world,tile.getPos());if(d!=null)for(Tiles.Owned t:d.devices)if(t instanceof Tiles.Rack&&Objects.equals(tile.owner,t.owner)){Tiles.Rack r=(Tiles.Rack)t;for(int i=0;i<r.capacity();i++)if(r.isServer(i))out.add(new Target(r,i));}return out;}
        @Override public boolean enchantItem(EntityPlayer p,int id){if(p.world.isRemote||!canInteractWith(p))return false;
            if(type==RACK){Tiles.Rack r=(Tiles.Rack)tile;if(id>=10&&id<19)return r.toggle(id-10);if(id>=30&&id<39){int b=id-30;return r.connect(b,r.portOf(b)>=0?r.portOf(b):selectedPort,p);}if(id>=50&&id<58){int port=id-50;if(port>=Hardware.ports(r.switchTier()))return false;selectedPort=port;return true;}return false;}
            if(type==CONTROL){Tiles.Computer c=(Tiles.Computer)tile;if(id==2){open(p,MARKET);return true;}if(id==100){page=Math.max(0,page-1);pageDirty=true;return true;}if(id==101){page++;pageDirty=true;return true;}if(id==102){snapshot=targets();reachable=snapshot;pageDirty=true;lastTopology=p.world.getTotalWorldTime();return true;}if(!c.online)return false;
                if(id==1||id==3){for(Target t:targets()){if(id==1)t.rack.miningMask|=1<<t.bay;else t.rack.miningMask&=~(1<<t.bay);t.rack.changed();}return true;}
                int row=id>=30&&id<35?id-30:id>=20&&id<25?id-20:-1;if(!pageDirty&&row>=0&&row<visible.size()){Target t=visible.get(row);boolean valid=false;for(Target current:targets())if(current.rack==t.rack&&current.bay==t.bay&&current.item==t.item){valid=true;break;}if(!valid)return false;if(id>=30)return t.rack.toggle(t.bay);t.rack.miningMask^=1<<t.bay;t.rack.changed();return true;}return false;}
            if(type==MARKET){if(id==2){open(p,CONTROL);return true;}return ((Tiles.Computer)tile).online&&id>=10&&id<14&&Market.buy(p,id-10);}return false;}
        void refresh(){Arrays.fill(data,0);if(type==RACK){Tiles.Rack r=(Tiles.Rack)tile;data[0]=r.capacity();data[1]=r.energy.getEnergyStored();data[2]=r.lastPower;data[3]=r.switchTier();data[4]=r.switchOnline?1:0;selectedPort=Math.min(selectedPort,Math.max(0,Hardware.ports(r.switchTier())-1));data[5]=selectedPort;data[6]=r.enabledMask;data[7]=r.poweredMask;data[8]=r.runningMask;for(int i=0;i<9;i++){data[10+i]=r.startAt(i)+1;data[20+i]=r.portOf(i)+1;data[30+i]=r.status[i];}for(int i=0;i<8;i++)data[40+i]=r.ports[i];}
            else if(type==CONTROL||type==MARKET){Tiles.Computer c=(Tiles.Computer)tile;data[0]=Market.Ledger.get(player.world).balance(player.getUniqueID());data[1]=c.online?1:0;data[2]=c.clusterPower;
                if(type==MARKET){for(int i=0;i<4;i++)data[3+i]=i<ODC.market.length&&!offers.getStackInSlot(i).isEmpty()?ODC.market[i].priceMilli:0;}
                else {data[3]=c.racks;data[4]=c.servers;data[5]=c.uplink;data[6]=c.switching;data[7]=c.networkUsed;data[8]=c.miningStats[2];data[9]=c.miningStats[4];long now=player.world.getTotalWorldTime();if(lastTopology==Long.MIN_VALUE){snapshot=targets();reachable=snapshot;lastTopology=now;}else if(now-lastTopology>=20){reachable=targets();lastTopology=now;}
                    int pages=Math.max(1,(snapshot.size()+4)/5);page=Math.max(0,Math.min(page,pages-1));data[10]=page;data[11]=pages;if(pageDirty){visible.clear();for(int i=page*5;i<Math.min(snapshot.size(),page*5+5);i++)visible.add(snapshot.get(i));pageDirty=false;}
                    for(int i=0;i<visible.size();i++){Target t=visible.get(i);int k=20+i*8;boolean valid=false;for(Target live:reachable)if(live.rack==t.rack&&live.bay==t.bay&&live.item==t.item){valid=true;break;}if(!valid)continue;data[k]=1;BlockPos pos=t.rack.getPos();data[k+1]=pos.getX();data[k+2]=pos.getY();data[k+3]=pos.getZ();data[k+4]=t.bay+1;data[k+5]=((t.rack.enabledMask>>t.bay)&1)|(((t.rack.miningMask>>t.bay)&1)<<1);data[k+6]=t.rack.status[t.bay];data[k+7]=t.rack.work[t.bay];}}}
            else if(type==STATION){Tiles.Station s=(Tiles.Station)tile;data[0]=s.online?1:0;data[1]=s.bandwidth();data[2]=s.energy.getEnergyStored();data[3]=s.idlePower();}}
        @Override public void detectAndSendChanges(){super.detectAndSendChanges();if(player.world.isRemote)return;refresh();for(int i=0;i<data.length;i++){int lo=data[i]&65535,hi=data[i]>>>16;if(previous[i*2]!=lo){for(IContainerListener l:listeners)l.sendWindowProperty(this,i*2,lo);previous[i*2]=lo;}if(previous[i*2+1]!=hi){for(IContainerListener l:listeners)l.sendWindowProperty(this,i*2+1,hi);previous[i*2+1]=hi;}}}
        @Override public void updateProgressBar(int id,int value){if(id<0||id>=data.length*2)return;int i=id/2,v=value&65535;data[i]=id%2==0?(data[i]&0xffff0000)|v:(data[i]&65535)|(v<<16);}
    }
}
