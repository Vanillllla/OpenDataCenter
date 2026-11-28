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
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import javax.annotation.Nullable;
import java.util.*;

public final class Tiles {
    public static abstract class Owned extends TileEntity {
        UUID owner;
        // Render-only, synchronized through the existing tile update packet, never persisted.
        int powerLamp=GridProtection.LAMP_OFF;
        void setPowerLamp(int lamp){
            if(powerLamp==lamp)return;powerLamp=lamp;
            if(world!=null&&!world.isRemote){net.minecraft.block.state.IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,2);}
        }
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote)powerLamp=GridProtection.LAMP_OFF;Network.invalidate(world);if(this instanceof Rack||this instanceof Chiller)LiquidNetwork.invalidate(world);PhysicalNetwork.changed(world,pos);}
        @Override public void invalidate(){super.invalidate();Network.invalidate(world);if(this instanceof Rack||this instanceof Chiller)LiquidNetwork.invalidate(world);}
        void setOwner(UUID id){owner=id;markDirty();}
        boolean allows(EntityPlayer p){return owner==null||owner.equals(p.getUniqueID())||p.capabilities.isCreativeMode;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);owner=n.hasUniqueId("Owner")?n.getUniqueId("Owner"):null;powerLamp=Math.max(0,Math.min(2,n.getInteger("PowerLamp")));}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);if(owner!=null)n.setUniqueId("Owner",owner);return n;}
        void changed(){markDirty();if(world!=null&&!world.isRemote){PhysicalNetwork.changed(world,pos);net.minecraft.block.state.IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3);}}
        @Override public NBTTagCompound getUpdateTag(){NBTTagCompound n=writeToNBT(new NBTTagCompound());if(this instanceof Rack||this instanceof Transformer)n.setInteger("PowerLamp",powerLamp);return n;}
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
    public static final class Rack extends InventoryTile {
        final PowerGrid.Port energy=new PowerGrid.Port(this);
        final ThermalRuntime.Entry[] thermal=new ThermalRuntime.Entry[9];
        boolean manifold;
        void setManifold(boolean value){if(manifold==value)return;manifold=value;LiquidNetwork.invalidate(world);if(world!=null&&!world.isRemote){OfflineState.invalidateAt(world,pos);changed();if(role==1)world.notifyBlockUpdate(pos.up(),world.getBlockState(pos.up()),world.getBlockState(pos.up()),3);}}
        final int[] ports=new int[8], cableTiers=new int[8];
        final UUID[] cableIds=new UUID[8]; // 0 empty, otherwise bay+1
        final java.util.Map<Integer,java.util.List<Integer>> activeJobs=new java.util.HashMap<>();
        final int[] status=new int[9], work=new int[9], remainder=new int[9], fault=new int[9], cpuUsed=new int[9], diskUsed=new int[9], gpuUsed=new int[9], ramUsed=new int[9], netUsed=new int[9], powerUsed=new int[9];
        int role=0, enabledMask, miningMask, poweredMask, runningMask, lastPower, revision;
        boolean switchOnline;
        boolean doorOpen;
        double doorFrom;
        long doorStart;
        boolean glassRack(){return world!=null&&world.getBlockState(pos).getBlock()==Hardware.RACK_GLASS;}
        double doorProgress(float partial){return RackLayout.Door.progress(doorOpen,doorFrom,world==null?0:world.getTotalWorldTime()-doorStart+partial);}
        boolean doorAllowsAccess(){return RackLayout.Door.accessible(glassRack(),doorOpen,doorProgress(0));}
        void resetDoor(){doorOpen=false;doorFrom=0;doorStart=0;}
        void toggleDoor(){
            if(world==null||world.isRemote||role==-1||!glassRack())return;
            long now=world.getTotalWorldTime();if(doorStart==now&&doorFrom!=(doorOpen?1:0))return;
            doorFrom=doorProgress(0);doorStart=now;doorOpen=!doorOpen;markDirty();
            net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);
            world.playSound(null,pos,doorOpen?net.minecraft.init.SoundEvents.BLOCK_IRON_DOOR_OPEN:net.minecraft.init.SoundEvents.BLOCK_IRON_DOOR_CLOSE,net.minecraft.util.SoundCategory.BLOCKS,.65f,1f);
        }
        UUID id=UUID.randomUUID();
        public Rack(){super(9);}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote&&role!=-1)PersistentThermal.reconcile(OfflineState.get(world),this);}
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
        void detach(int i){if(world==null||world.isRemote)return;PersistentThermal.reconcile(OfflineState.get(world),this);ThermalRuntime.checkpoint(this,i);thermal[i]=null;if(!slots.get(i).isEmpty())EventSystem.rack(this,i,Level.INFO,Category.HARDWARE,"equipment_removed","Removed "+slots.get(i).getDisplayName());if(isServer(i)){TaskData d=TaskData.get(world);UUID id=ClusterScheduler.serverId(slots.get(i));d.serverStopped(id,false);d.networkState.remove(id);}OfflineState.invalidateAt(world,pos);if(Hardware.switchTier(slots.get(i))>0){clearPorts();removeUplinks(slots.get(i));}else clearPort(portOf(i),null);int bit=~(1<<i);enabledMask&=bit;miningMask&=bit;poweredMask&=bit;runningMask&=bit;remainder[i]=0;work[i]=0;status[i]=0;fault[i]=0;cpuUsed[i]=diskUsed[i]=gpuUsed[i]=ramUsed[i]=netUsed[i]=powerUsed[i]=0;activeJobs.remove(i);}
        @Override public void setInventorySlotContents(int i,ItemStack s){
            if(world!=null&&!world.isRemote&&!s.isEmpty()&&!ItemStack.areItemStacksEqual(slots.get(i),s)&&!isItemValidForSlot(i,s))return;
            boolean changedItem=!ItemStack.areItemStacksEqual(slots.get(i),s);if(changedItem)detach(i);if(world!=null&&!world.isRemote)Hardware.ensureId(s);if(world!=null&&!world.isRemote&&Hardware.switchTier(s)>0)Hardware.switchId(s);super.setInventorySlotContents(i,s);if(changedItem&&!s.isEmpty())EventSystem.rack(this,i,Level.SUCCESS,Category.HARDWARE,"equipment_installed","Installed "+s.getDisplayName());
        }
        @Override public ItemStack decrStackSize(int i,int count){if(count>0)detach(i);return super.decrStackSize(i,count);}
        @Override public ItemStack removeStackFromSlot(int i){detach(i);return super.removeStackFromSlot(i);}
        boolean toggle(int bay){if(world!=null&&!world.isRemote)PersistentThermal.reconcile(OfflineState.get(world),this);if(!isServer(bay))return false;int bit=1<<bay;if((enabledMask&bit)!=0 && TaskData.get(world).maintenance(ClusterScheduler.serverId(slots.get(bay)),false)>0)return false;if((enabledMask&bit)==0){if(OfflineState.get(world).pendingThermal.containsKey(ClusterScheduler.serverId(slots.get(bay)))||Hardware.state(slots.get(bay))!=ServerSpec.READY||!ThermalRuntime.canRestart(this,bay))return false;HardwareDamage.latch(slots.get(bay),0);}OfflineState.invalidateAt(world,pos);enabledMask^=bit;fault[bay]=0;if((enabledMask&bit)==0){TaskData.get(world).serverStopped(ClusterScheduler.serverId(slots.get(bay)),true);poweredMask&=~bit;runningMask&=~bit;lastPower=Math.max(0,lastPower-powerUsed[bay]);powerUsed[bay]=0;}EventSystem.rack(this,bay,Level.INFO,Category.POWER,(enabledMask&bit)!=0?"server_on":"server_off",(enabledMask&bit)!=0?"Server enabled":"Server disabled");changed();return true;}
        boolean connect(int bay,int port,EntityPlayer p){return connect(bay,port,p,1);}
        boolean connect(int bay,int port,EntityPlayer p,int tier){if(!isServer(bay)||port<0||port>=Hardware.ports(switchTier())||!allows(p))return false;
            if(ports[port]==bay+1){clearPort(port,p);OfflineState.invalidateAt(world,pos);EventSystem.rack(this,bay,Level.WARNING,Category.NETWORK,"port_disconnected","Patch cable disconnected");changed();return true;}
            if(Hardware.nic(slots.get(bay))==0||!LanPorts.canConnect(ports,Hardware.ports(switchTier()),port,bay,tier)||!LanCables.take(p,tier))return false;
            ports[port]=bay+1;cableTiers[port]=tier;cableIds[port]=UUID.randomUUID();OfflineState.invalidateAt(world,pos);EventSystem.rack(this,bay,Level.INFO,Category.NETWORK,"port_connected","Patch cable connected: "+patchMbps(port)+" Mb/s");changed();return true;
        }
        boolean upgrade(int port,int tier,EntityPlayer p){if(!allows(p)||!LanPorts.canUpgrade(ports,cableTiers,Hardware.ports(switchTier()),port,tier)||!LanCables.take(p,tier))return false;int old=cableTier(port);cableTiers[port]=tier;LanCables.refund(world,pos,p,old);OfflineState.invalidateAt(world,pos);EventSystem.rack(this,ports[port]-1,Level.INFO,Category.NETWORK,"port_upgraded","Patch cable replaced: LAN T"+old+" -> T"+tier);changed();return true;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){if(world!=null&&!world.isRemote)for(int i=0;i<capacity();i++)if(isServer(i))ThermalRuntime.checkpoint(this,i);super.writeToNBT(n);n.setInteger("Role",role);n.setBoolean("Manifold",manifold);n.setUniqueId("RackId",id);n.setInteger("Enabled",enabledMask);n.setInteger("Mining",miningMask);n.setInteger("Powered",poweredMask);n.setInteger("Running",runningMask);n.setBoolean("SwitchOnline",switchOnline);n.setBoolean("DoorOpen",doorOpen);n.setDouble("DoorFrom",doorFrom);n.setLong("DoorStart",doorStart);n.setIntArray("Fault",fault);n.setIntArray("Ports",ports);n.setIntArray("CableTiers",cableTiers);for(int i=0;i<8;i++)if(cableIds[i]!=null)n.setUniqueId("CableId"+i,cableIds[i]);n.setIntArray("Remainder",remainder);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);role=n.getInteger("Role");manifold=role!=-1&&n.getBoolean("Manifold");if(n.hasUniqueId("RackId"))id=n.getUniqueId("RackId");enabledMask=n.getInteger("Enabled");miningMask=n.getInteger("Mining");poweredMask=n.getInteger("Powered");runningMask=n.getInteger("Running");switchOnline=n.getBoolean("SwitchOnline");doorOpen=n.getBoolean("DoorOpen");doorFrom=n.hasKey("DoorFrom")?RackLayout.Door.clamp(n.getDouble("DoorFrom")):doorOpen?1:0;doorStart=n.getLong("DoorStart");Arrays.fill(ports,0);Arrays.fill(remainder,0);int[] f=n.getIntArray("Fault");Arrays.fill(fault,0);System.arraycopy(f,0,fault,0,Math.min(9,f.length));int[] a=n.getIntArray("Ports"),b=n.getIntArray("Remainder");System.arraycopy(a,0,ports,0,Math.min(8,a.length));int[] tiers=n.getIntArray("CableTiers");for(int i=0;i<8;i++){cableTiers[i]=ports[i]==0?0:i<tiers.length&&LogicalLinks.speed(tiers[i])>0?tiers[i]:1;cableIds[i]=ports[i]==0?null:n.hasUniqueId("CableId"+i)?n.getUniqueId("CableId"+i):null;}System.arraycopy(b,0,remainder,0,Math.min(9,b.length));}
        @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox(){return glassRack()&&role!=-1?new net.minecraft.util.math.AxisAlignedBB(pos.add(-1,0,-1),pos.add(2,role==1?2:1,2)):new net.minecraft.util.math.AxisAlignedBB(pos,pos.add(1,role==1?2:1,1));}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){poweredMask=runningMask=0;switchOnline=false;}}
    }
    /** The only persistent inventory is the chassis. Parts are read/written inside its NBT. */
    public static final class Workbench extends InventoryTile {
        Object editor;
        public Workbench(){super(1);}
        @Override void changed(){if(world!=null)super.changed();}
        int chassis(){return Hardware.units(slots.get(0));}
        ChassisSpec chassisModel(){return Hardware.chassis(slots.get(0));}
        NonNullList<ItemStack> parts(){return Hardware.parts(slots.get(0));}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return i==0&&Hardware.units(s)>0;}
        @Override public void setInventorySlotContents(int i,ItemStack s){if(world!=null&&!world.isRemote)Hardware.ensureId(s);super.setInventorySlotContents(i,s);}
        void write(NonNullList<ItemStack> parts){
            Hardware.writeParts(slots.get(0),parts);
            // Only the chassis size affects the world model. Container synchronization handles parts.
            if(world!=null)markDirty();
        }
    }
    /** Powered devices reserve their full load once in the shared scheduler cycle. */
    public static abstract class Powered extends Owned {
        final PowerGrid.Port energy=new PowerGrid.Port(this);boolean online=false;
        abstract int idlePower();
        boolean operational(){return TerminalPower.operational(online,energy.fed());}
        void refreshPower(){setPowered(energy.useWatts(idlePower()));}
        void pauseForSnapshot(){if(online){online=false;changed();if(this instanceof Computer)world.markBlockRangeForRenderUpdate(pos,pos.up());else if(this instanceof Station&&((Station)this).equipment()==NetworkEquipment.TOWER)world.markBlockRangeForRenderUpdate(pos,pos.up(2));}}
        void setPowered(boolean now){if(online!=now){online=now;OfflineState.invalidateAt(world,pos);if(this instanceof Computer)EventSystem.terminalPower((Computer)this,now);changed();if(this instanceof Computer)world.markBlockRangeForRenderUpdate(pos,pos.up());else if(this instanceof Station&&((Station)this).equipment()==NetworkEquipment.TOWER)world.markBlockRangeForRenderUpdate(pos,pos.up(2));}}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setBoolean("Online",online);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);online=n.getBoolean("Online");}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){online=false;markDirty();}}
    }
    public static final class Chiller extends Powered {
        int tier(){if(world==null)return 1;net.minecraft.block.Block b=world.getBlockState(pos).getBlock();return b instanceof Blocks.Chiller?((Blocks.Chiller)b).tier:1;}
        @Override int idlePower(){return LiquidCore.electric(tier());}
        private PowerGrid.Domain reserved;
        private long tick=Long.MIN_VALUE,actualAC;
        double confirmedCooling;long confirmedCoolingTick=Long.MIN_VALUE;
        @Override void refreshPower(){
            reserved=null;actualAC=0;tick=world.getTotalWorldTime();
            LiquidNetwork.Contour contour=LiquidNetwork.contour(world,pos);
            if(contour==null||contour.racks.isEmpty()){setPowered(false);return;}
            PowerGrid.Domain d=energy.domain();
            if(d==null){setPowered(false);return;}
            long before=d.budget.usedUnits();
            if(!d.use(idlePower())){setPowered(false);return;}
            reserved=d;actualAC=ODC.requireEnergy?(d.budget.usedUnits()-before)/PowerEngine.TICKS_PER_SECOND:PowerEngine.scale(idlePower(),d.budget.factor);
            setPowered(true);
        }
        double available(long now){
            double capacity=LiquidCore.available(tier(),actualAC,tick==now&&online&&reserved!=null&&reserved.funded());
            reserved=null;confirmedCooling=capacity;confirmedCoolingTick=now;return capacity;
        }
        @Override void setPowered(boolean value){
            if(!value)confirmedCooling=0;
            // Cooling devices are not LAN endpoints; no network rediscovery on
            // every transition. The existing grid still clears them on failure.
            if(online!=value){online=value;markDirty();if(world!=null&&!world.isRemote){net.minecraft.block.state.IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,2);}}
        }
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
        @Override int idlePower(){return NetworkEquipment.DATAOS_WATTS;}
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
    public static final class NetworkTerminal extends NetworkDevice {NetworkEquipment equipment(){NetworkEquipment e=world==null?null:NetworkEquipment.byId(world.getBlockState(pos).getBlock().getRegistryName().toString());return e!=null&&e.terminal()?e:NetworkEquipment.TERMINAL_1;} int bandwidth(){return equipment().mbps;} @Override int idlePower(){return equipment().watts;}}
    public static final class Station extends NetworkDevice {NetworkEquipment equipment(){NetworkEquipment e=world==null?null:NetworkEquipment.byId(world.getBlockState(pos).getBlock().getRegistryName().toString());return e!=null&&!e.terminal()?e:NetworkEquipment.RADIO;} boolean satellite(){return equipment()==NetworkEquipment.SATELLITE;} int bandwidth(){return equipment().mbps;} @Override int idlePower(){return equipment().watts;}}
    @net.minecraftforge.fml.common.Optional.Interface(iface="api.hbm.energy.IEnergyConnector",modid="hbm")
    public static final class Transformer extends Owned implements PowerGrid.Source, net.minecraft.util.ITickable, api.hbm.energy.IEnergyConnector {
        ExternalWindow window;
        long lastOutput,lastHE,lastFE;
        private boolean loaded;
        @Override public void onLoad(){super.onLoad();loaded=true;}
        @Override public void onChunkUnload(){loaded=false;window=null;super.onChunkUnload();}
        @Override public void invalidate(){loaded=false;window=null;super.invalidate();}
        public boolean isLoaded(){return loaded&&!isInvalid();}
        private boolean accepting(){return world!=null&&!world.isRemote&&isLoaded()&&enabled&&ODC.requireEnergy&&window!=null&&PowerGrid.active(world,pos,window);}
        private long receive(long units,TransformerCore.Input input,boolean simulate){
            return accepting()?window.offer(this,units,ODC.conversionRate(input),input,simulate):0;
        }
        public long transferPower(long power){return power<=0?power:power-receive(power,TransformerCore.Input.HE,false);}
        public long getPower(){return 0;} // no HE inventory exposed
        public long getMaxPower(){return accepting()?receive(Long.MAX_VALUE,TransformerCore.Input.HE,true):0;}
        public long getTransferWeight(){return getMaxPower();}
        private final net.minecraftforge.energy.IEnergyStorage fe=new net.minecraftforge.energy.IEnergyStorage(){
            @Override public int receiveEnergy(int max,boolean simulate){return (int)receive(max,TransformerCore.Input.FE,simulate);}
            @Override public int extractEnergy(int max,boolean simulate){return 0;}
            @Override public int getEnergyStored(){return 0;}
            @Override public int getMaxEnergyStored(){return (int)Math.min(Integer.MAX_VALUE,receive(Long.MAX_VALUE,TransformerCore.Input.FE,true));}
            @Override public boolean canExtract(){return false;}
            @Override public boolean canReceive(){return true;}
        };
        @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap,EnumFacing side){
            return cap==net.minecraftforge.energy.CapabilityEnergy.ENERGY||super.hasCapability(cap,side);
        }
        @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,EnumFacing side){
            return cap==net.minecraftforge.energy.CapabilityEnergy.ENERGY?net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(fe):super.getCapability(cap,side);
        }
        @Override public void update(){
            if(world==null||world.isRemote||!loaded)return;
            if(net.minecraftforge.fml.common.Loader.isModLoaded("hbm"))try{HbmBridge.subscribe(this);}catch(LinkageError e){/* incompatible HBM fork: FE remains available */}
            if(!accepting())return;
            // Also support extract-only FE stores. Push-only cables use the capability above.
            for(EnumFacing side:EnumFacing.VALUES){
                int request=(int)Math.min(Integer.MAX_VALUE,receive(Long.MAX_VALUE,TransformerCore.Input.FE,true));
                if(request<=0)break;
                BlockPos q=pos.offset(side);if(!world.isBlockLoaded(q))continue;
                TileEntity other=world.getTileEntity(q);
                if(other==null||other instanceof PowerGrid.Source||!other.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,side.getOpposite()))continue;
                net.minecraftforge.energy.IEnergyStorage storage=other.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,side.getOpposite());
                if(storage==null||!storage.canExtract())continue;
                int paid=storage.extractEnergy(request,false);
                if(paid>0&&paid<=request)receive(paid,TransformerCore.Input.FE,false);
            }
        }
        void openWindow(ExternalWindow next){window=next;}
        void closeWindow(ExternalWindow old){
            lastOutput=lastHE=lastFE=0;
            if(old!=null){ExternalWindow.Receipt r=old.receipts.get(this);if(r!=null){lastOutput=r.mj/1000;lastHE=r.he;lastFE=r.fe;}}
            window=null;
        }
        boolean enabled=true;
        int breakerState=GridProtection.ON;
        private boolean legacy;
        boolean legacyOff(){return legacy&&!enabled;}
        @Override public void gridState(int state){boolean change=breakerState!=state||enabled!=(state==GridProtection.ON)||legacy;legacy=false;breakerState=state;enabled=state==GridProtection.ON;if(!enabled){window=null;lastOutput=lastHE=lastFE=0;}if(change)changed();}
        int tier(){
            if(world==null||!world.isBlockLoaded(pos))return 1;
            net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
            return block instanceof Blocks.Transformer?((Blocks.Transformer)block).tier:1;
        }
        @Override public long ratedWatts(){return ODC.transformerWatts(tier());}
        @Override public long outputWatts(){return enabled?lastOutput:0;}
        long actualOutputWatts(){return outputWatts();}
        long actualInputUnits(){return enabled?lastHE+lastFE:0;}
        void toggle(){
            if(world==null||world.isRemote)return;
            PowerGrid.Domain grid=PowerGrid.domain(world,pos);if(grid!=null)grid.toggle();
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setBoolean("BreakerEnabled",enabled);n.setInteger("GridState",breakerState);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);enabled=!n.hasKey("BreakerEnabled")||n.getBoolean("BreakerEnabled");legacy=!n.hasKey("GridState");breakerState=legacy?(enabled?GridProtection.ON:GridProtection.OFF):n.getInteger("GridState");}
    }
    public static final class Battery extends Owned implements PowerGrid.Source {
        int configuredWatts=-1;
        int breakerState=GridProtection.ON;
        int watts(){return configuredWatts<0?ODC.batteryOutput:configuredWatts;}
        @Override public long ratedWatts(){return watts();}
        @Override public long outputWatts(){return breakerState==GridProtection.ON?watts():0;}
        @Override public void gridState(int state){if(breakerState!=state){breakerState=state;changed();}}
        boolean configure(int watts){
            if(watts<1||watts>TransformerCore.MAX_WATTS)return false;
            configuredWatts=watts;OfflineState.invalidateAt(world,pos);markDirty();changed();return true;
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("OutputWatts",watts());n.setInteger("GridState",breakerState);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);configuredWatts=n.hasKey("OutputWatts")?Math.max(1,Math.min(TransformerCore.MAX_WATTS,n.getInteger("OutputWatts"))):-1;breakerState=n.getInteger("GridState");}
    }
}
