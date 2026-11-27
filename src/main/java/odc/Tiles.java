package odc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
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
        void setOwner(UUID id){owner=id;markDirty();}
        boolean allows(EntityPlayer p){return owner==null||owner.equals(p.getUniqueID())||p.capabilities.isCreativeMode;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);owner=n.hasUniqueId("Owner")?n.getUniqueId("Owner"):null;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);if(owner!=null)n.setUniqueId("Owner",owner);return n;}
        void changed(){markDirty();if(world!=null&&!world.isRemote){net.minecraft.block.state.IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3);}}
        @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
        @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
        @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity pkt){readFromNBT(pkt.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    }
    public static abstract class InventoryTile extends Owned implements IInventory {
        final NonNullList<ItemStack> slots;
        InventoryTile(int count){slots=NonNullList.withSize(count,ItemStack.EMPTY);}
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
        final TileEntity tile;
        Buffer(TileEntity tile,int capacity){super(capacity,50000,0);this.tile=tile;}
        @Override public int receiveEnergy(int n,boolean simulate){if(n<=0)return 0;int v=super.receiveEnergy(n,simulate);if(v>0&&!simulate)tile.markDirty();return v;}
        void load(int n){energy=Math.max(0,Math.min(capacity,n));}
        boolean use(int n){if(n<0)return false;if(!ODC.requireEnergy)return true;if(energy<n)return false;energy-=n;tile.markDirty();return true;}
    }
    public static final class Rack extends InventoryTile {
        final Buffer energy=new Buffer(this,12000);
        final int[] assignments=new int[4], remainder=new int[4];
        final Object[] editors=new Object[4];
        final int[] status=new int[4], usedMbps=new int[4], neededMbps=new int[4], work=new int[4];
        UUID controller;
        BlockPos controllerPosition;
        int runningMask, nextMask, lastPower, totalMbps, totalReward;
        public Rack(){super(4);}
        int[] layout(){int[] size=new int[4];for(int i=0;i<4;i++)size[i]=Hardware.units(slots.get(i));return size;}
        int startAt(int bay){return RackLayout.startAt(layout(),bay);}
        int bayType(int bay){int i=startAt(bay);if(i<0)return 0;int type=Hardware.gpus(slots.get(i))>0?2:1;return type+((runningMask&(1<<i))!=0?2:0);}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){return editors[i]==null&&RackLayout.canInsert(layout(),i,Hardware.units(s));}
        int hash(){int n=0;for(ItemStack s:slots)n+=Hardware.hash(s);return n;}
        int watts(){int n=0;for(ItemStack s:slots)n+=Hardware.power(s);return n;}
        int serverCount(){int n=0;for(int i=0;i<4;i++)if(startAt(i)==i)n++;return n;}
        private void resetBay(int i){assignments[i]=0;remainder[i]=0;runningMask&=~(1<<i);nextMask&=~(1<<i);status[i]=0;usedMbps[i]=neededMbps[i]=work[i]=0;}
        @Override public void setInventorySlotContents(int i,ItemStack s){if((world==null||!world.isRemote)&&(s.isEmpty()||!ItemStack.areItemStacksEqual(slots.get(i),s)))resetBay(i);super.setInventorySlotContents(i,s);}
        @Override public ItemStack decrStackSize(int i,int count){if((world==null||!world.isRemote)&&count>0&&!slots.get(i).isEmpty())resetBay(i);return super.decrStackSize(i,count);}
        @Override public ItemStack removeStackFromSlot(int i){if(world==null||!world.isRemote)resetBay(i);return super.removeStackFromSlot(i);}
        @Override public void clear(){if(world==null||!world.isRemote)for(int i=0;i<4;i++)resetBay(i);super.clear();}
        boolean cycleController(){List<Computer> choices=Network.controllers(world,this);int at=-1;for(int i=0;i<choices.size();i++)if(choices.get(i).id.equals(controller))at=i;
            // Including an explicit disconnected state after the last controller.
            int next=at+1;Computer c=next<choices.size()?choices.get(next):null;
            UUID id=c==null?null:c.id;if(Objects.equals(controller,id))return false;
            controller=id;controllerPosition=c==null?null:c.getPos();for(int i=0;i<4;i++)resetBay(i);changed();return true;}
        boolean cycleTask(int bay){if(bay<0||bay>=4||startAt(bay)!=bay||editors[bay]!=null)return false;
            if(controller==null){List<Computer> choices=Network.controllers(world,this);if(choices.isEmpty())return false;controller=choices.get(0).id;controllerPosition=choices.get(0).getPos();}
            assignments[bay]=(assignments[bay]+1)%4;runningMask&=~(1<<bay);nextMask&=~(1<<bay);status[bay]=0;changed();return true;}
        boolean beginEdit(int bay,Object token){if(bay<0||bay>=4||startAt(bay)!=bay||editors[bay]!=null)return false;editors[bay]=token;runningMask&=~(1<<bay);nextMask&=~(1<<bay);status[bay]=ComputeEngine.MAINTENANCE;changed();return true;}
        void endEdit(int bay,Object token){if(editors[bay]==token){editors[bay]=null;changed();}}
        boolean editing(int bay){return editors[bay]!=null;}
        void partsChanged(int bay){runningMask&=~(1<<bay);nextMask&=~(1<<bay);status[bay]=ComputeEngine.MAINTENANCE;remainder[bay]=0;usedMbps[bay]=neededMbps[bay]=work[bay]=0;changed();}
        void resetCycle(){nextMask=0;lastPower=totalMbps=totalReward=0;controllerPosition=null;
            for(int i=0;i<4;i++){status[i]=editing(i)?ComputeEngine.MAINTENANCE:!slots.get(i).isEmpty()&&Hardware.state(slots.get(i))!=ServerSpec.READY?ComputeEngine.INCOMPLETE:assignments[i]==0?ComputeEngine.IDLE:ComputeEngine.NO_CONTROLLER;usedMbps[i]=neededMbps[i]=work[i]=0;}}
        void finishCycle(){if(runningMask!=nextMask){runningMask=nextMask;changed();}}
        @Override public boolean hasCapability(Capability<?> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY||super.hasCapability(c,f);}
        @Nullable @Override public <T>T getCapability(Capability<T> c,@Nullable EnumFacing f){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(energy):super.getCapability(c,f);}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Energy",energy.getEnergyStored());if(controller!=null)n.setUniqueId("Controller",controller);n.setIntArray("Tasks",assignments);n.setIntArray("RewardRemainders",remainder);n.setInteger("RunningMask",runningMask);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);energy.load(n.getInteger("Energy"));controller=n.hasUniqueId("Controller")?n.getUniqueId("Controller"):null;int[] tasks=n.getIntArray("Tasks"),rem=n.getIntArray("RewardRemainders");
            for(int i=0;i<4;i++){assignments[i]=i<tasks.length&&tasks[i]>=1&&tasks[i]<=3&&startAt(i)==i&&controller!=null?tasks[i]:0;remainder[i]=i<rem.length?Math.max(0,Math.min(999,rem[i])):0;}runningMask=n.getInteger("RunningMask")&15;}
        @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){runningMask=0;nextMask=0;}}
    }
    public static final class Assembler extends InventoryTile {
        // Keep legacy slots 0..6 unchanged: chassis, CPU, GPUs, output.
        static final int[] PART_SLOTS={7,1,8,9,10,11,12,13,2,3,4,5};
        public Assembler(){super(14);}
        int chassis(){return Hardware.size(slots.get(0));}
        NonNullList<ItemStack> parts(){NonNullList<ItemStack> p=Hardware.emptyParts();for(int i=0;i<p.size();i++)p.set(i,slots.get(PART_SLOTS[i]));return p;}
        @Override public boolean isItemValidForSlot(int i,ItemStack s){
            if(i==0)return Hardware.size(s)>0;
            for(int j=0;j<PART_SLOTS.length;j++)if(PART_SLOTS[j]==i){int u=chassis(),board=Hardware.partTier(0,slots.get(7));return Hardware.accepts(j,s)&&(u==0||ServerSpec.fits(u,board,j));}return false;
        }
        boolean assemble(){int u=chassis();if(u==0||!slots.get(6).isEmpty())return false;
            NonNullList<ItemStack> p=parts();int state=ServerSpec.state(u,Hardware.tiers(p));
            // Allow incomplete chassis for fitting inside rack, but never illegal contents.
            if(state==ServerSpec.INVALID||state==ServerSpec.CAPACITY)return false;
            slots.set(6,Hardware.build(u,p));slots.set(0,ItemStack.EMPTY);for(int i:PART_SLOTS)slots.set(i,ItemStack.EMPTY);changed();return true;
        }
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
        UUID id=UUID.randomUUID();int enabledMask;
        final int[][] jobStats=new int[3][6]; // assigned, active, effective work, allocated Mbps, milli-NTC/s, requested Mbps
        int racks, servers, uplink, switching, networkUsed, clusterPower;
        @Override int idlePower(){return 5;}
        boolean enabled(int task){return task>=1&&task<=3&&(enabledMask&(1<<(task-1)))!=0;}
        void toggle(int task){if(task>=1&&task<=3){enabledMask^=1<<(task-1);markDirty();}}
        void resetCycle(){for(int[] s:jobStats)Arrays.fill(s,0);racks=servers=uplink=switching=networkUsed=clusterPower=0;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setUniqueId("ControllerId",id);n.setInteger("EnabledTasks",enabledMask);return n;}
        @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);if(n.hasUniqueId("ControllerId"))id=n.getUniqueId("ControllerId");enabledMask=n.getInteger("EnabledTasks")&7;}
    }
    public static final class Switch extends Powered {@Override int idlePower(){return 8;}}
    public static final class Station extends Powered {@Override int idlePower(){return 12;}}
    public static final class Battery extends Owned implements ITickable {
        int round=0;
        @Override public void update(){if(world.isRemote||world.getTotalWorldTime()%5!=0)return;List<Network.Endpoint> ends=Network.power(world,pos);if(ends.isEmpty())return;int budget=ODC.batteryOutput*5;int start=Math.floorMod(round++,ends.size());for(int i=0;i<ends.size()&&budget>0;i++){Network.Endpoint end=ends.get((start+i)%ends.size());budget-=end.energy.receiveEnergy(budget,false);}}
    }
}
