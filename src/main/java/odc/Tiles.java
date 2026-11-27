package odc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import javax.annotation.Nullable;
import java.util.*;

public final class Tiles {
    public static abstract class Owned extends TileEntity {
        UUID owner;
        @Override public void onLoad(){super.onLoad();Network.invalidate(world);}
        @Override public void invalidate(){super.invalidate();Network.invalidate(world);}
        void setOwner(UUID id){owner=id;markDirty();}
        boolean allows(EntityPlayer p){return owner==null||owner.equals(p.getUniqueID())||p.capabilities.isCreativeMode;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);owner=n.hasUniqueId("Owner")?n.getUniqueId("Owner"):null;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);if(owner!=null)n.setUniqueId("Owner",owner);return n;}
        void changed(){markDirty();if(world!=null&&!world.isRemote){net.minecraft.block.state.IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3);}}
        @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
        @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
        @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity pkt){readFromNBT(pkt.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,this instanceof Computer?pos.up():pos);}
    }
    public static abstract class InventoryTile extends Owned implements ISidedInventory {
        final NonNullList<ItemStack> slots;
        InventoryTile(int count){slots=NonNullList.withSize(count,ItemStack.EMPTY);}
        @Override public int[] getSlotsForFace(EnumFacing side){return new int[0];}
        @Override public boolean canInsertItem(int i,ItemStack s,EnumFacing side){return false;}
        @Override public boolean canExtractItem(int i,ItemStack s,EnumFacing side){return false;}
        @Override public int getSizeInventory(){return slots.size();}
        @Override public boolean isEmpty(){for(ItemStack s:slots)if(!s.isEmpty())return false;return true;}
        @Override public ItemStack getStackInSlot(int i){return slots.get(i);}
        @Override public ItemStack decrStackSize(int i,int count){ItemStack s=ItemStackHelper.getAndSplit(slots,i,count);if(!s.isEmpty())changed();return s;}
        @Override public ItemStack removeStackFromSlot(int i){ItemStack s=ItemStackHelper.getAndRemove(slots,i);if(!s.isEmpty())changed();return s;}
        @Override public void setInventorySlotContents(int i,ItemStack s){slots.set(i,s);if(s.getCount()>getInventoryStackLimit())s.setCount(getInventoryStackLimit());changed();}
        @Override public int getInventoryStackLimit(){return 1;}
        @Override public boolean isUsableByPlayer(EntityPlayer p){return world!=null&&world.getTileEntity(pos)==this&&p.getDistanceSq(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&allows(p);}
        @Override public void openInventory(EntityPlayer p){} @Override public void closeInventory(EntityPlayer p){}
        @Override public int getField(int id){return 0;} @Override public void setField(int id,int value){} @Override public int getFieldCount(){return 0;}
        @Override public void clear(){slots.clear();changed();}
        @Override public String getName(){return "OpenDataCenter";} @Override public boolean hasCustomName(){return false;}
        @Override public net.minecraft.util.text.ITextComponent getDisplayName(){return new net.minecraft.util.text.TextComponentString(getName());}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);ItemStackHelper.saveAllItems(n,slots);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);slots.clear();ItemStackHelper.loadAllItems(n,slots);}
    }
    static final class Buffer extends EnergyStorage {
        final TileEntity tile; long lastFeed=Long.MIN_VALUE;
        Buffer(TileEntity tile,int capacity){super(capacity,50000,0);this.tile=tile;}
        @Override public int receiveEnergy(int n,boolean simulate){if(n<=0)return 0;if(!simulate&&tile.getWorld()!=null)lastFeed=tile.getWorld().getTotalWorldTime();int v=super.receiveEnergy(n,simulate);if(v>0&&!simulate)tile.markDirty();return v;}
        boolean fed(){return !ODC.requireEnergy||tile.getWorld()!=null&&lastFeed!=Long.MIN_VALUE&&tile.getWorld().getTotalWorldTime()-lastFeed<=5;}
        void load(int n){energy=Math.max(0,Math.min(capacity,n));}
        boolean use(int n){if(n<0)return false;if(!ODC.requireEnergy)return true;if(!fed()||energy<n)return false;energy-=n;tile.markDirty();return true;}
    }
    public static final class Rack extends InventoryTile {
        final Buffer energy=new Buffer(this,1000000);
        final int[] ports=new int[8]; // 0 empty, otherwise bay+1
        final java.util.Map<Integer,java.util.List<Integer>> activeJobs=new java.util.HashMap<>();
        final int[] status=new int[9], work=new int[9], remainder=new int[9], fault=new int[9], cpuUsed=new int[9], diskUsed=new int[9], gpuUsed=new int[9], ramUsed=new int[9], netUsed=new int[9], powerUsed=new int[9];
        int role=0, enabledMask, miningMask, poweredMask, runningMask, lastPower, revision;
        boolean switchOnline;
        UUID id=UUID.randomUUID();
        public Rack(){super(9);}
        int capacity(){return role==1?9:4;}
        Rack master(){if(role!=-1)return this;if(world==null||!world.isBlockLoaded(pos.down()))return null;TileEntity t=world.getTileEntity(pos.down());return t instanceof Rack&&((Rack)t).role==1?(Rack)t:null;}
        int[] layout(){int[] a=new int[capacity()];for(int i=0;i<a.length;i++)a[i]=Hardware.rackUnits(slots.get(i));return a;}
        int startAt(int bay){return RackLayout.startAt(layout(),bay);}
        int switchTier(){for(ItemStack s:slots){int t=Hardware.switchTier(s);if(t>0)return t;}return 0;}
        int switchBay(){for(int i=0;i<capacity();i++)if(Hardware.switchTier(slots.get(i))>0)return i;return -1;}
        int portOf(int bay){return PortMap.portOf(ports,bay);}
        int serverCount(){int n=0;for(int i=0;i<capacity();i++)if(Hardware.units(slots.get(i))>0)n++;return n;}
        boolean isServer(int bay){return bay>=0&&bay<capacity()&&Hardware.units(slots.get(bay))>0;}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return role!=-1&&i<capacity()&&slots.get(i).isEmpty()&&RackLayout.canInsert(layout(),i,Hardware.rackUnits(s))&&(Hardware.switchTier(s)==0||switchTier()==0);}
        void refund(int count,EntityPlayer player){if(count<=0||world==null||world.isRemote)return;ItemStack stack=new ItemStack(Hardware.PATCH,count);if(player!=null)player.inventory.addItemStackToInventory(stack);if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);}
        int clearPorts(){return PortMap.clear(ports);}
        void detach(int i){if(world==null||world.isRemote)return;if(isServer(i))TaskData.get(world).serverStopped(ClusterScheduler.serverId(slots.get(i)),false);OfflineState.invalidateAt(world,pos);int n=0;if(Hardware.switchTier(slots.get(i))>0)n=clearPorts();else n=PortMap.detach(ports,i);refund(n,null);int bit=~(1<<i);enabledMask&=bit;miningMask&=bit;poweredMask&=bit;runningMask&=bit;remainder[i]=0;work[i]=0;status[i]=0;fault[i]=0;cpuUsed[i]=diskUsed[i]=gpuUsed[i]=ramUsed[i]=netUsed[i]=powerUsed[i]=0;activeJobs.remove(i);}
        @Override public void setInventorySlotContents(int i,ItemStack s){
            if(world!=null&&!world.isRemote&&!s.isEmpty()&&!ItemStack.areItemStacksEqual(slots.get(i),s)&&!isItemValidForSlot(i,s))return;
            if(!ItemStack.areItemStacksEqual(slots.get(i),s))detach(i);if(world!=null&&!world.isRemote)Hardware.ensureId(s);super.setInventorySlotContents(i,s);
        }
        @Override public ItemStack decrStackSize(int i,int count){if(count>0)detach(i);return super.decrStackSize(i,count);}
        @Override public ItemStack removeStackFromSlot(int i){detach(i);return super.removeStackFromSlot(i);}
        boolean toggle(int bay){if(!isServer(bay))return false;int bit=1<<bay;if((enabledMask&bit)!=0 && TaskData.get(world).maintenance(ClusterScheduler.serverId(slots.get(bay)),false)>0)return false;if((enabledMask&bit)==0&&Hardware.state(slots.get(bay))!=ServerSpec.READY)return false;OfflineState.invalidateAt(world,pos);enabledMask^=bit;fault[bay]=0;if((enabledMask&bit)==0){TaskData.get(world).serverStopped(ClusterScheduler.serverId(slots.get(bay)),true);poweredMask&=~bit;runningMask&=~bit;}changed();return true;}
        boolean connect(int bay,int port,EntityPlayer p){if(!isServer(bay)||Hardware.nic(slots.get(bay))==0)return false;
            int found=-1;for(int i=0;i<p.inventory.mainInventory.size();i++)if(!p.inventory.mainInventory.get(i).isEmpty()&&p.inventory.mainInventory.get(i).getItem()==Hardware.PATCH){found=i;break;}
            int result=PortMap.toggle(ports,Hardware.ports(switchTier()),port,bay,found>=0);if(result==PortMap.REJECT)return false;OfflineState.invalidateAt(world,pos);
            if(result==PortMap.CONNECTED){p.inventory.mainInventory.get(found).shrink(1);p.inventory.markDirty();}else refund(1,p);changed();return true;
        }
        @Override public boolean hasCapability(Capability<?> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY&&master()!=null||super.hasCapability(c,f);}
        @Nullable @Override public <T>T getCapability(Capability<T> c,@Nullable EnumFacing f){Rack m=master();return c==CapabilityEnergy.ENERGY?(m==null?null:CapabilityEnergy.ENERGY.cast(m.energy)):super.getCapability(c,f);}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Energy",energy.getEnergyStored());n.setInteger("Role",role);n.setUniqueId("RackId",id);n.setInteger("Enabled",enabledMask);n.setInteger("Mining",miningMask);n.setInteger("Powered",poweredMask);n.setInteger("Running",runningMask);n.setBoolean("SwitchOnline",switchOnline);n.setIntArray("Fault",fault);n.setIntArray("Ports",ports);n.setIntArray("Remainder",remainder);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);energy.load(n.getInteger("Energy"));role=n.getInteger("Role");if(n.hasUniqueId("RackId"))id=n.getUniqueId("RackId");enabledMask=n.getInteger("Enabled");miningMask=n.getInteger("Mining");poweredMask=n.getInteger("Powered");runningMask=n.getInteger("Running");switchOnline=n.getBoolean("SwitchOnline");Arrays.fill(ports,0);Arrays.fill(remainder,0);int[] f=n.getIntArray("Fault");Arrays.fill(fault,0);System.arraycopy(f,0,fault,0,Math.min(9,f.length));int[] a=n.getIntArray("Ports"),b=n.getIntArray("Remainder");System.arraycopy(a,0,ports,0,Math.min(8,a.length));System.arraycopy(b,0,remainder,0,Math.min(9,b.length));}
        @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox(){return new net.minecraft.util.math.AxisAlignedBB(pos,pos.add(1,role==1?2:1,1));}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){poweredMask=runningMask=0;switchOnline=false;}}
    }
    /** The only persistent inventory is the chassis. Parts are read/written inside its NBT. */
    public static final class Workbench extends InventoryTile {
        Object editor;
        public Workbench(){super(1);}
        int chassis(){return Hardware.units(slots.get(0));}
        NonNullList<ItemStack> parts(){return Hardware.parts(slots.get(0));}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return i==0&&Hardware.units(s)>0;}
        @Override public void setInventorySlotContents(int i,ItemStack s){if(world!=null&&!world.isRemote)Hardware.ensureId(s);super.setInventorySlotContents(i,s);}
        void write(NonNullList<ItemStack> parts){Hardware.writeParts(slots.get(0),parts);changed();}
    }
    /** Powered devices consume their own idle load each game tick. */
    public static abstract class Powered extends Owned implements ITickable {
        final Buffer energy=new Buffer(this,480);boolean online=false;
        abstract int idlePower();
        @Override public void update(){if(!world.isRemote){boolean now=energy.use(idlePower());if(online!=now){online=now;changed();}}}
        @Override public boolean hasCapability(Capability<?> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY||super.hasCapability(c,f);}
        @Nullable @Override public <T>T getCapability(Capability<T> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(energy):super.getCapability(c,f);}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Energy",energy.getEnergyStored());n.setBoolean("Online",online);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);energy.load(n.getInteger("Energy"));online=n.getBoolean("Online");}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){online=false;markDirty();}}
    }
    public static final class Computer extends Powered {
        UUID id=UUID.randomUUID();
        int[] miningStats=new int[6]; // assigned, active, effective work, allocated Mbps, milli-NTC/s, requested Mbps
        int racks, servers, uplink, switching, networkUsed, clusterPower;
        @Override int idlePower(){return 5;}
        void resetCycle(){Arrays.fill(miningStats,0);racks=servers=uplink=switching=networkUsed=clusterPower=0;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setUniqueId("ControllerId",id);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);if(n.hasUniqueId("ControllerId"))id=n.getUniqueId("ControllerId");}
    }
    
    public static final class Station extends Powered {boolean satellite(){return world!=null&&world.getBlockState(pos).getBlock()==Hardware.SATELLITE;} int bandwidth(){return satellite()?512:128;} @Override int idlePower(){return satellite()?40:12;}}
    public static final class Battery extends Owned implements ITickable {
        int round=0;
        @Override public void update(){if(world.isRemote||world.getTotalWorldTime()%5!=0)return;List<Network.Endpoint> ends=Network.power(world,pos);if(ends.isEmpty())return;int budget=ODC.batteryOutput*5;int start=Math.floorMod(round++,ends.size());for(int i=0;i<ends.size()&&budget>0;i++){Network.Endpoint end=ends.get((start+i)%ends.size());budget-=end.energy.receiveEnergy(budget,false);}}
    }
}
