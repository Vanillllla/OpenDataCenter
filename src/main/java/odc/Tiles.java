package odc;
import static odc.EventJournal.*;

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
        @Override public void onLoad(){super.onLoad();Network.invalidate(world);PhysicalNetwork.changed(world,pos);}
        @Override public void invalidate(){super.invalidate();Network.invalidate(world);}
        void setOwner(UUID id){owner=id;markDirty();}
        boolean allows(EntityPlayer p){return owner==null||owner.equals(p.getUniqueID())||p.capabilities.isCreativeMode;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);owner=n.hasUniqueId("Owner")?n.getUniqueId("Owner"):null;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);if(owner!=null)n.setUniqueId("Owner",owner);return n;}
        void changed(){markDirty();if(world!=null&&!world.isRemote){PhysicalNetwork.changed(world,pos);net.minecraft.block.state.IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3);}}
        @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
        @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
        @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity pkt){readFromNBT(pkt.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,this instanceof Computer?pos.up():this instanceof Station&&((Station)this).equipment()==NetworkEquipment.TOWER?pos.up(2):pos);}
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
        final int[] ports=new int[8], cableTiers=new int[8];
        final UUID[] cableIds=new UUID[8]; // 0 empty, otherwise bay+1
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
        boolean networkConnected(int bay){return isServer(bay)&&switchBay()>=0&&Hardware.nic(slots.get(bay))>0&&PortMap.connected(ports,Hardware.ports(switchTier()),bay);}
        int serverCount(){int n=0;for(int i=0;i<capacity();i++)if(Hardware.units(slots.get(i))>0)n++;return n;}
        boolean isServer(int bay){return bay>=0&&bay<capacity()&&Hardware.units(slots.get(bay))>0;}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return role!=-1&&i<capacity()&&slots.get(i).isEmpty()&&RackLayout.canInsert(layout(),i,Hardware.rackUnits(s))&&(Hardware.switchTier(s)==0||switchTier()==0);}
        int cableTier(int port){return LanPorts.tier(ports,cableTiers,port);}
        int patchMbps(int port){return LogicalLinks.speed(cableTier(port));}
        int patchLimit(int bay){return !isServer(bay)?0:Math.min(Math.min(Hardware.nic(slots.get(bay)),Hardware.portMbps(switchTier())),patchMbps(portOf(bay)));}
        void clearPort(int port,EntityPlayer player){if(port<0||port>=8||ports[port]==0)return;int tier=LanPorts.remove(ports,cableTiers,port);cableIds[port]=null;LanCables.refund(world,pos,player,tier);}
        void clearPorts(){for(int p=0;p<8;p++)clearPort(p,null);}
        void removeUplinks(ItemStack sw){if(world!=null&&!world.isRemote&&Hardware.switchTier(sw)>0)PhysicalNetwork.removeUplinks(world,pos,Hardware.switchId(sw));}
        void detach(int i){if(world==null||world.isRemote)return;if(!slots.get(i).isEmpty())EventSystem.rack(this,i,Level.INFO,Category.HARDWARE,"equipment_removed","Removed "+slots.get(i).getDisplayName());if(isServer(i)){TaskData d=TaskData.get(world);UUID id=ClusterScheduler.serverId(slots.get(i));d.serverStopped(id,false);d.networkState.remove(id);}OfflineState.invalidateAt(world,pos);if(Hardware.switchTier(slots.get(i))>0){clearPorts();removeUplinks(slots.get(i));}else clearPort(portOf(i),null);int bit=~(1<<i);enabledMask&=bit;miningMask&=bit;poweredMask&=bit;runningMask&=bit;remainder[i]=0;work[i]=0;status[i]=0;fault[i]=0;cpuUsed[i]=diskUsed[i]=gpuUsed[i]=ramUsed[i]=netUsed[i]=powerUsed[i]=0;activeJobs.remove(i);}
        @Override public void setInventorySlotContents(int i,ItemStack s){
            if(world!=null&&!world.isRemote&&!s.isEmpty()&&!ItemStack.areItemStacksEqual(slots.get(i),s)&&!isItemValidForSlot(i,s))return;
            boolean changedItem=!ItemStack.areItemStacksEqual(slots.get(i),s);if(changedItem)detach(i);if(world!=null&&!world.isRemote)Hardware.ensureId(s);if(world!=null&&!world.isRemote&&Hardware.switchTier(s)>0)Hardware.switchId(s);super.setInventorySlotContents(i,s);if(changedItem&&!s.isEmpty())EventSystem.rack(this,i,Level.SUCCESS,Category.HARDWARE,"equipment_installed","Installed "+s.getDisplayName());
        }
        @Override public ItemStack decrStackSize(int i,int count){if(count>0)detach(i);return super.decrStackSize(i,count);}
        @Override public ItemStack removeStackFromSlot(int i){detach(i);return super.removeStackFromSlot(i);}
        boolean toggle(int bay){if(!isServer(bay))return false;int bit=1<<bay;if((enabledMask&bit)!=0 && TaskData.get(world).maintenance(ClusterScheduler.serverId(slots.get(bay)),false)>0)return false;if((enabledMask&bit)==0&&Hardware.state(slots.get(bay))!=ServerSpec.READY)return false;OfflineState.invalidateAt(world,pos);enabledMask^=bit;fault[bay]=0;if((enabledMask&bit)==0){TaskData.get(world).serverStopped(ClusterScheduler.serverId(slots.get(bay)),true);poweredMask&=~bit;runningMask&=~bit;}EventSystem.rack(this,bay,Level.INFO,Category.POWER,(enabledMask&bit)!=0?"server_on":"server_off",(enabledMask&bit)!=0?"Server enabled":"Server disabled");changed();return true;}
        boolean connect(int bay,int port,EntityPlayer p){return connect(bay,port,p,1);}
        boolean connect(int bay,int port,EntityPlayer p,int tier){if(!isServer(bay)||port<0||port>=Hardware.ports(switchTier())||!allows(p))return false;
            if(ports[port]==bay+1){clearPort(port,p);OfflineState.invalidateAt(world,pos);EventSystem.rack(this,bay,Level.WARNING,Category.NETWORK,"port_disconnected","Patch cable disconnected");changed();return true;}
            if(Hardware.nic(slots.get(bay))==0||!LanPorts.canConnect(ports,Hardware.ports(switchTier()),port,bay,tier)||!LanCables.take(p,tier))return false;
            ports[port]=bay+1;cableTiers[port]=tier;cableIds[port]=UUID.randomUUID();OfflineState.invalidateAt(world,pos);EventSystem.rack(this,bay,Level.INFO,Category.NETWORK,"port_connected","Patch cable connected: "+patchMbps(port)+" Mb/s");changed();return true;
        }
        boolean upgrade(int port,int tier,EntityPlayer p){if(!allows(p)||!LanPorts.canUpgrade(ports,cableTiers,Hardware.ports(switchTier()),port,tier)||!LanCables.take(p,tier))return false;int old=cableTier(port);cableTiers[port]=tier;LanCables.refund(world,pos,p,old);OfflineState.invalidateAt(world,pos);EventSystem.rack(this,ports[port]-1,Level.INFO,Category.NETWORK,"port_upgraded","Patch cable replaced: LAN T"+old+" -> T"+tier);changed();return true;}
        @Override public boolean hasCapability(Capability<?> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY&&master()!=null||super.hasCapability(c,f);}
        @Nullable @Override public <T>T getCapability(Capability<T> c,@Nullable EnumFacing f){Rack m=master();return c==CapabilityEnergy.ENERGY?(m==null?null:CapabilityEnergy.ENERGY.cast(m.energy)):super.getCapability(c,f);}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Energy",energy.getEnergyStored());n.setInteger("Role",role);n.setUniqueId("RackId",id);n.setInteger("Enabled",enabledMask);n.setInteger("Mining",miningMask);n.setInteger("Powered",poweredMask);n.setInteger("Running",runningMask);n.setBoolean("SwitchOnline",switchOnline);n.setIntArray("Fault",fault);n.setIntArray("Ports",ports);n.setIntArray("CableTiers",cableTiers);for(int i=0;i<8;i++)if(cableIds[i]!=null)n.setUniqueId("CableId"+i,cableIds[i]);n.setIntArray("Remainder",remainder);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);energy.load(n.getInteger("Energy"));role=n.getInteger("Role");if(n.hasUniqueId("RackId"))id=n.getUniqueId("RackId");enabledMask=n.getInteger("Enabled");miningMask=n.getInteger("Mining");poweredMask=n.getInteger("Powered");runningMask=n.getInteger("Running");switchOnline=n.getBoolean("SwitchOnline");Arrays.fill(ports,0);Arrays.fill(remainder,0);int[] f=n.getIntArray("Fault");Arrays.fill(fault,0);System.arraycopy(f,0,fault,0,Math.min(9,f.length));int[] a=n.getIntArray("Ports"),b=n.getIntArray("Remainder");System.arraycopy(a,0,ports,0,Math.min(8,a.length));int[] tiers=n.getIntArray("CableTiers");for(int i=0;i<8;i++){cableTiers[i]=ports[i]==0?0:i<tiers.length&&LogicalLinks.speed(tiers[i])>0?tiers[i]:1;cableIds[i]=ports[i]==0?null:n.hasUniqueId("CableId"+i)?n.getUniqueId("CableId"+i):null;}System.arraycopy(b,0,remainder,0,Math.min(9,b.length));}
        @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox(){return new net.minecraft.util.math.AxisAlignedBB(pos,pos.add(1,role==1?2:1,1));}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){poweredMask=runningMask=0;switchOnline=false;}}
    }
    /** The only persistent inventory is the chassis. Parts are read/written inside its NBT. */
    public static final class Workbench extends InventoryTile {
        Object editor;
        public Workbench(){super(1);}
        @Override void changed(){if(world!=null)super.changed();}
        int chassis(){return Hardware.units(slots.get(0));}
        NonNullList<ItemStack> parts(){return Hardware.parts(slots.get(0));}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return i==0&&Hardware.units(s)>0;}
        @Override public void setInventorySlotContents(int i,ItemStack s){if(world!=null&&!world.isRemote)Hardware.ensureId(s);super.setInventorySlotContents(i,s);}
        void write(NonNullList<ItemStack> parts){
            Hardware.writeParts(slots.get(0),parts);
            // Only the chassis size affects the world model. Container synchronization handles parts.
            if(world!=null)markDirty();
        }
    }
    /** Powered devices consume their own idle load each game tick. */
    public static abstract class Powered extends Owned implements ITickable {
        final Buffer energy=new Buffer(this,480);boolean online=false;
        abstract int idlePower();
        boolean operational(){return TerminalPower.operational(online,energy.fed());}
        @Override public void update(){if(!world.isRemote){boolean now=energy.use(idlePower());if(online!=now){online=now;if(this instanceof Computer)EventSystem.terminalPower((Computer)this,now);changed();if(this instanceof Computer)world.markBlockRangeForRenderUpdate(pos,pos.up());else if(this instanceof Station&&((Station)this).equipment()==NetworkEquipment.TOWER)world.markBlockRangeForRenderUpdate(pos,pos.up(2));}}}
        @Override public boolean hasCapability(Capability<?> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY||super.hasCapability(c,f);}
        @Nullable @Override public <T>T getCapability(Capability<T> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(energy):super.getCapability(c,f);}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Energy",energy.getEnergyStored());n.setBoolean("Online",online);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);energy.load(n.getInteger("Energy"));online=n.getBoolean("Online");}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){online=false;markDirty();}}
    }
    public static final class Computer extends Powered {
        UUID id=UUID.randomUUID();
        @Override void setOwner(UUID player){
            super.setOwner(player);
            if(player!=null && world!=null && !world.isRemote){
                TaskData d=TaskData.get(world);
                NBTTagCompound book=d.market.owner(player);
                String location="Terminal"+pos.toLong();
                if(book.hasUniqueId(location))id=book.getUniqueId(location);else book.setUniqueId(location,id);
                d.markDirty();markDirty();
            }
        }
        int[] miningStats=new int[6]; // assigned, active, effective work, allocated Mbps, milli-NTC/s, requested Mbps
        int racks, servers, uplink, switching, networkUsed, clusterPower;
        @Override int idlePower(){return 5;}
        void resetCycle(){Arrays.fill(miningStats,0);racks=servers=uplink=switching=networkUsed=clusterPower=0;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setUniqueId("ControllerId",id);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);if(n.hasUniqueId("ControllerId"))id=n.getUniqueId("ControllerId");}
    }
    
    public static abstract class NetworkDevice extends Powered {
        UUID networkId=UUID.randomUUID();
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setUniqueId("NetworkId",networkId);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);if(n.hasUniqueId("NetworkId"))networkId=n.getUniqueId("NetworkId");}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote)markDirty();}
    }
    public static final class NetworkTerminal extends NetworkDevice {NetworkEquipment equipment(){NetworkEquipment e=world==null?null:NetworkEquipment.byId(world.getBlockState(pos).getBlock().getRegistryName().toString());return e!=null&&e.terminal()?e:NetworkEquipment.TERMINAL_1;} int bandwidth(){return equipment().mbps;} @Override int idlePower(){return equipment().fe;}}
    public static final class Station extends NetworkDevice {NetworkEquipment equipment(){NetworkEquipment e=world==null?null:NetworkEquipment.byId(world.getBlockState(pos).getBlock().getRegistryName().toString());return e!=null&&!e.terminal()?e:NetworkEquipment.RADIO;} boolean satellite(){return equipment()==NetworkEquipment.SATELLITE;} int bandwidth(){return equipment().mbps;} @Override int idlePower(){return equipment().fe;}}
    public static final class Battery extends Owned implements ITickable {
        int round=0;
        @Override public void update(){if(world.isRemote||world.getTotalWorldTime()%5!=0)return;List<Network.Endpoint> ends=Network.power(world,pos);if(ends.isEmpty())return;int budget=ODC.batteryOutput*5;int start=Math.floorMod(round++,ends.size());for(int i=0;i<ends.size()&&budget>0;i++){Network.Endpoint end=ends.get((start+i)%ends.size());budget-=end.energy.receiveEnergy(budget,false);}}
    }
}
