package odc;
import static odc.EventJournal.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

/** Read-only device panels and DataOS. All actions are validated server-side. */
final class PanelMenu extends Container {
    final EntityPlayer player;final Tiles.Owned tile;final int type,bay,revision;final ItemStack opened;
    final TaskConsole console=new TaskConsole(); EventJournal.Query eventQuery=new EventJournal.Query("","","",false); UUID detailEvent=TaskConsole.NONE; UUID resourcePool=TaskConsole.NONE, detailServer=TaskConsole.NONE; DataOSUiState.Query serverQuery=DataOSUiState.Query.defaults(); int scrollRow; net.minecraft.nbt.NBTTagCompound view=new net.minecraft.nbt.NBTTagCompound(),lastView;
    final int[] data=new int[420],previous=new int[840];int tab,port,lanTier=1;long lastRefresh=Long.MIN_VALUE;
    PanelMenu(int id,EntityPlayer p,Tiles.Owned t){player=p;tile=t;type=id==2?0:id<40?1:2;bay=type==0?-1:id%20;revision=t instanceof Tiles.Rack?((Tiles.Rack)t).revision:0;opened=type==0?ItemStack.EMPTY:((Tiles.Rack)t).slots.get(bay);Arrays.fill(previous,-1);if(type==0&&!p.world.isRemote)EventSystem.terminalOpened((Tiles.Computer)t);}
    @Override public boolean canInteractWith(EntityPlayer p){BlockPos b=tile.getPos();if(tile.getWorld()!=p.world||p.world.getTileEntity(b)!=tile||!tile.allows(p)||p.getDistanceSq(b.getX()+.5,b.getY()+.5,b.getZ()+.5)>64)return false;if(type==0)return true;Tiles.Rack r=(Tiles.Rack)tile;return r.role!=-1&&r.revision==revision&&bay<r.capacity()&&(p.world.isRemote||r.slots.get(bay)==opened);}
    private int lastPowerState=-1;private boolean clientPowered;
    boolean terminalPowered(){return type!=0||(player.world.isRemote?clientPowered:((Tiles.Computer)tile).operational());}
    boolean canOperate(EntityPlayer p){return canInteractWith(p)&&terminalPowered();}
    @Override public ItemStack transferStackInSlot(EntityPlayer p,int i){return ItemStack.EMPTY;}
    static void putText(int[] a,int offset,int len,String text){for(int i=0;i<len&&i*2<text.length();i++)a[offset+i]=text.charAt(i*2)|(i*2+1<text.length()?text.charAt(i*2+1)<<16:0);}
    static String getText(int[] a,int offset,int len){StringBuilder s=new StringBuilder();for(int i=0;i<len;i++){int x=a[offset+i];char lo=(char)(x&65535),hi=(char)(x>>>16);if(lo==0)break;s.append(lo);if(hi==0)break;s.append(hi);}return s.toString();}
    static int nextCap(int current,int[] values){for(int i=0;i<values.length;i++)if(values[i]==current)return values[(i+1)%values.length];return values[0];}
    static int cap(long x){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,x));}
    static void putLong(int[] a,int i,long x){a[i]=(int)x;a[i+1]=(int)(x>>>32);}
    static long getLong(int[] a,int i){return (a[i]&0xffffffffL)|((long)a[i+1]<<32);}
    boolean setPool(EntityPlayer p,String name){if(type!=1||!canInteractWith(p)||p.world.isRemote)return false;boolean ok=TaskData.get(p.world).setPool((Tiles.Rack)tile,bay,name);lastRefresh=Long.MIN_VALUE;return ok;}
    boolean rename(EntityPlayer p,int expected,String name){if(!canInteractWith(p)||name==null)return false;name=name.trim();if(name.isEmpty()||name.length()>32||name.chars().anyMatch(c->c<32||c==167||c==127))return false;if(type==1&&expected==bay){Tiles.Rack r=(Tiles.Rack)tile;r.slots.get(bay).setStackDisplayName(name);r.changed();lastRefresh=Long.MIN_VALUE;return true;}return false;}
    @Override public boolean enchantItem(EntityPlayer p,int action){if(p.world.isRemote||!canOperate(p))return false;lastRefresh=Long.MIN_VALUE;
        if(type==1){
            if(action==4) return TaskData.get(p.world).maintenance(ClusterScheduler.serverId(opened),true)>=0;
            return action==1&&((Tiles.Rack)tile).toggle(bay);
        }
        if(type==2){Tiles.Rack r=(Tiles.Rack)tile;if(action>=61&&action<=63){lanTier=action-60;return true;}if(action==64)return r.upgrade(port,lanTier,p);if(action>=10&&action<18){int n=action-10;if(n>=Hardware.ports(r.switchTier()))return false;port=n;return true;}if(action>=20&&action<29){int b=action-20;if(r.portOf(b)>=0)return false;return r.connect(b,port,p,lanTier);}if(action==30&&r.ports[port]>0)return r.connect(r.ports[port]-1,port,p);return false;}
        if(action>=1&&action<=8){setTab(action-1);scrollRow=0;return true;}return false;
    }
    void setTab(int next){tab=next;}
    static boolean buy(EntityPlayer p,TaskData d,UUID wallet,int index){if(index<0||index>=ODC.market.length)return false;Market.Entry e=ODC.market[index];ItemStack s=e.stack();if(s.isEmpty()||d.wallet(wallet)[0]<e.priceMilli)return false;int room=0;for(ItemStack old:p.inventory.mainInventory){if(old.isEmpty())room+=s.getMaxStackSize();else if(ItemStack.areItemsEqual(old,s)&&ItemStack.areItemStackTagsEqual(old,s))room+=Math.max(0,old.getMaxStackSize()-old.getCount());if(room>=s.getCount())break;}if(room<s.getCount())return false;d.wallet(wallet)[0]-=e.priceMilli;d.markDirty();p.inventory.addItemStackToInventory(s);if(!s.isEmpty())p.dropItem(s,false);p.inventory.markDirty();d.record(p.getUniqueID(),wallet,null,Level.INFO,Category.FINANCE,Target.NONE,"purchase","Purchased "+e.item,0,-e.priceMilli);return true;}
    void refresh(){Arrays.fill(data,0);if(type!=0){Tiles.Rack r=(Tiles.Rack)tile;data[0]=r.capacity();data[1]=bay;data[2]=(r.enabledMask>>bay)&1;data[3]=(r.poweredMask>>bay)&1;data[4]=r.status[bay];data[5]=r.powerUsed[bay];data[6]=r.cpuUsed[bay];data[7]=r.gpuUsed[bay];data[8]=r.ramUsed[bay];data[9]=r.netUsed[bay];if(type==1){int[] p=Hardware.spec(r.slots.get(bay));System.arraycopy(p,0,data,20,p.length);data[10]=Hardware.units(r.slots.get(bay));data[11]=DataOSView.id(r,bay);data[12]=TaskData.get(player.world).maintenance(ClusterScheduler.serverId(opened),false);putText(data,100,20,r.slots.get(bay).getDisplayName());putText(data,120,20,TaskData.get(player.world).poolName(ClusterScheduler.serverId(opened)));}else{data[10]=r.switchTier();data[11]=port;data[12]=r.switchOnline?1:0;data[13]=lanTier;for(int i=1;i<=3;i++)data[13+i]=LanCables.count(player,i);for(int i=0;i<8;i++){data[20+i]=r.ports[i];data[30+i]=r.cableTier(i);}for(int i=0;i<r.capacity();i++){data[40+i]=Hardware.units(r.slots.get(i));data[50+i]=r.portOf(i)+1;data[60+i]=Hardware.nic(r.slots.get(i));}}return;}
        data[0]=tab;
    }
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();if(player.world.isRemote)return;if(type==0){int power=terminalPowered()?1:0;if(power!=lastPowerState){lastPowerState=power;lastRefresh=Long.MIN_VALUE;for(IContainerListener l:listeners)l.sendWindowProperty(this,TerminalPower.PROPERTY,power);}if(power==0)return;}long now=player.world.getTotalWorldTime();if(lastRefresh!=Long.MIN_VALUE&&now-lastRefresh<5)return;lastRefresh=now;if(type==0){net.minecraft.nbt.NBTTagCompound next=DataOSView.snapshot(this);if(!next.equals(lastView)){lastView=next;RackInteraction.channel.sendTo(new DataOSPackets.View(windowId,next),(net.minecraft.entity.player.EntityPlayerMP)player);}return;}refresh();for(int i=0;i<data.length;i++){int lo=data[i]&65535,hi=data[i]>>>16;if(previous[i*2]!=lo){for(IContainerListener l:listeners)l.sendWindowProperty(this,i*2,lo);previous[i*2]=lo;}if(previous[i*2+1]!=hi){for(IContainerListener l:listeners)l.sendWindowProperty(this,i*2+1,hi);previous[i*2+1]=hi;}}}
    @Override public void updateProgressBar(int id,int v){if(id==TerminalPower.PROPERTY){clientPowered=v!=0;return;}if(id<0||id>=data.length*2)return;int i=id/2;data[i]=id%2==0?(data[i]&0xffff0000)|(v&65535):(data[i]&65535)|((v&65535)<<16);}
}
