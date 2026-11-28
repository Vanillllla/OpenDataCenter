package odc;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import javax.annotation.Nullable;

final class Blocks {
    static final PropertyDirection FACING=PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL);
    static final PropertyInteger PART=PropertyInteger.create("part",0,2);
    static final PropertyBool UPPER=PropertyBool.create("upper");
    static final PropertyBool ACTIVE=PropertyBool.create("active");
    static final PropertyBool[] LINK={PropertyBool.create("down"),PropertyBool.create("up"),PropertyBool.create("north"),PropertyBool.create("south"),PropertyBool.create("west"),PropertyBool.create("east")};
    static abstract class Oriented extends BlockContainer {
        Oriented(String id){super(Material.IRON);setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setHardness(2.5f);setResistance(8);setSoundType(SoundType.METAL);setCreativeTab(Hardware.TAB);}
        @Override public EnumBlockRenderType getRenderType(IBlockState s){return EnumBlockRenderType.MODEL;}
        @Override public IBlockState getStateFromMeta(int m){return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(m&3));}
        @Override public int getMetaFromState(IBlockState s){return s.getValue(FACING).getHorizontalIndex();}
        @Override public IBlockState getStateForPlacement(World w,BlockPos p,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase e){return getDefaultState().withProperty(FACING,e.getHorizontalFacing().getOpposite());}
        @Override public void onBlockPlacedBy(World w,BlockPos p,IBlockState s,EntityLivingBase e,ItemStack item){super.onBlockPlacedBy(w,p,s,e,item);Network.invalidate(w);if(!w.isRemote&&e instanceof EntityPlayer){TileEntity t=w.getTileEntity(p);if(t instanceof Tiles.Owned)((Tiles.Owned)t).setOwner(((EntityPlayer)e).getUniqueID());}}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){TileEntity t=w.getTileEntity(p);if(!w.isRemote&&t instanceof Tiles.NetworkTerminal)PhysicalNetwork.removeUplinks(w,p,((Tiles.NetworkTerminal)t).networkId);if(!w.isRemote&&t instanceof Tiles.InventoryTile)InventoryHelper.dropInventoryItems(w,p,(Tiles.InventoryTile)t);super.breakBlock(w,p,s);Network.invalidate(w);PowerState.changed(w,p,true);OfflineState.invalidateAt(w,p);PhysicalNetwork.blockRemoved(w,p,this);}
        @Override public void onBlockAdded(World w,BlockPos p,IBlockState s){super.onBlockAdded(w,p,s);Network.invalidate(w);PowerState.changed(w,p,true);OfflineState.invalidateAt(w,p);PhysicalNetwork.blockAdded(w,p,this);}
        @Override public boolean isOpaqueCube(IBlockState s){return false;}
        @Override public boolean isFullCube(IBlockState s){return false;}
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w,IBlockState s,BlockPos p,EnumFacing f){return BlockFaceShape.UNDEFINED;}
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
            if(!w.isRemote){TileEntity t=w.getTileEntity(p);if(t instanceof Tiles.Owned&&((Tiles.Owned)t).owner==null)((Tiles.Owned)t).setOwner(player.getUniqueID());if(t instanceof Tiles.Owned&&!((Tiles.Owned)t).allows(player)){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Access denied: owner only"),true);return true;}int id=gui();if(id>=0)player.openGui(ODC.instance,id,w,p.getX(),p.getY(),p.getZ());}return true;
        }
        abstract int gui();
    }
    static final class Rack extends Oriented {
        final boolean glass;
        Rack(String id,boolean glass){super(id);this.glass=glass;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(PART,0));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,PART);}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);return s.withProperty(PART,t instanceof Tiles.Rack?((Tiles.Rack)t).role==1?1:((Tiles.Rack)t).role==-1?2:0:0);}
        @Override public BlockRenderLayer getRenderLayer(){return BlockRenderLayer.CUTOUT;}
        @Override public TileEntity createNewTileEntity(World w,int meta){return new Tiles.Rack();}
        @Override public void onBlockPlacedBy(World w,BlockPos p,IBlockState s,EntityLivingBase e,ItemStack i){super.onBlockPlacedBy(w,p,s,e,i);RackMultiblock.placed(w,p);}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){RackMultiblock.breakRack(w,p);super.breakBlock(w,p,s);}
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){if(hand==EnumHand.MAIN_HAND){if(LiquidNetwork.interact(w,p,s,player,side))return true;return RackInteraction.interact(w,p,player,side,y,false);}return true;}
        @Override int gui(){return 0;}
    }
    /** The model is derived from the real inventory, never from a second saved item. */
    static final class Chiller extends Oriented {
        final int tier;
        Chiller(int tier){super("liquid_chiller_t"+tier);this.tier=tier;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return new Tiles.Chiller();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);return s.withProperty(ACTIVE,t instanceof Tiles.Chiller&&((Tiles.Chiller)t).online);}
        @Override int gui(){return -1;}
    }
    static final class LiquidPipe extends Block {
        LiquidPipe(){super(Material.IRON);setRegistryName(ODC.ID,"liquid_pipe");setTranslationKey(ODC.ID+".liquid_pipe");setCreativeTab(Hardware.TAB);setHardness(.7f);setSoundType(SoundType.METAL);IBlockState s=blockState.getBaseState();for(PropertyBool p:LINK)s=s.withProperty(p,false);setDefaultState(s);}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,LINK[0],LINK[1],LINK[2],LINK[3],LINK[4],LINK[5]);}
        @Override public int getMetaFromState(IBlockState s){return 0;}
        @Override public IBlockState getStateFromMeta(int meta){return getDefaultState();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){for(EnumFacing f:EnumFacing.VALUES)s=s.withProperty(LINK[f.getIndex()],LiquidNetwork.pipeContact(w,p.offset(f),f.getOpposite()));return s;}
        @Override public boolean isOpaqueCube(IBlockState s){return false;}
        @Override public boolean isFullCube(IBlockState s){return false;}
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w,IBlockState s,BlockPos p,EnumFacing f){return BlockFaceShape.UNDEFINED;}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){s=getActualState(s,w,p);return new AxisAlignedBB(s.getValue(LINK[4])?0:.3125,s.getValue(LINK[0])?0:.3125,s.getValue(LINK[2])?0:.3125,s.getValue(LINK[5])?1:.6875,s.getValue(LINK[1])?1:.6875,s.getValue(LINK[3])?1:.6875);}
        @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return getBoundingBox(s,w,p);}
        @Override public void onBlockAdded(World w,BlockPos p,IBlockState s){super.onBlockAdded(w,p,s);LiquidNetwork.invalidate(w);OfflineState.invalidateAt(w,p);}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){super.breakBlock(w,p,s);LiquidNetwork.invalidate(w);OfflineState.invalidateAt(w,p);}
    }
    static final class Workbench extends Oriented {
        // Render index 3 denotes the 4U case (there is no 3U item).
        static final PropertyInteger CHASSIS=PropertyInteger.create("chassis",0,3);
        Workbench(){super("workbench");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(CHASSIS,0));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,CHASSIS);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return new Tiles.Workbench();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){
            TileEntity t=w.getTileEntity(p);int units=t instanceof Tiles.Workbench?((Tiles.Workbench)t).chassis():0;
            return s.withProperty(CHASSIS,units==4?3:units==2?2:units==1?1:0);
        }
        @Override int gui(){return Menus.WORKBENCH;}
    }
    static final class Machine extends Oriented {
        final int type;
        Machine(String id,int type){super(id);this.type=type;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return type==1?new Tiles.Workbench():type==2?new Tiles.Computer():type==3?new Tiles.Battery():new Tiles.Station();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);return s.withProperty(ACTIVE,t instanceof Tiles.Powered?((Tiles.Powered)t).online:t instanceof Tiles.Battery&&((Tiles.Battery)t).breakerState==GridProtection.ON);}
        @Override public BlockRenderLayer getRenderLayer(){return type>=5?BlockRenderLayer.CUTOUT:BlockRenderLayer.SOLID;}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return type>=5?new AxisAlignedBB(0.0625,0,0.0625,0.9375,1,0.9375):super.getBoundingBox(s,w,p);}
        @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return type>=5?getBoundingBox(s,w,p):super.getCollisionBoundingBox(s,w,p);}
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){return type>=5?false:super.onBlockActivated(w,p,s,player,hand,side,x,y,z);}
        @Override int gui(){return type==1?1:type==2?2:type==3?Menus.BATTERY:-1;}
    }
    static final class Transformer extends Oriented {
        static final PropertyInteger INDICATOR=PropertyInteger.create("indicator",0,3);
        final int tier;
        Transformer(int tier){super("transformer_t"+tier);this.tier=tier;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(INDICATOR,0));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,INDICATOR);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return new Tiles.Transformer();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){
            TileEntity t=w.getTileEntity(p);int model=0;
            if(t instanceof Tiles.Transformer){Tiles.Transformer transformer=(Tiles.Transformer)t;model=GridProtection.transformerModel(transformer.enabled,transformer.powerLamp);}
            return s.withProperty(INDICATOR,model);
        }
        @Override int gui(){return Menus.TRANSFORMER;}
    }
    static final class NetworkTerminal extends Oriented {
        final NetworkEquipment equipment;
        NetworkTerminal(NetworkEquipment e){super(e.id);equipment=e;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return new Tiles.NetworkTerminal();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);return s.withProperty(ACTIVE,t instanceof Tiles.NetworkTerminal&&((Tiles.NetworkTerminal)t).online);}
        @Override int gui(){return Menus.STATION;}
    }
    /** Three real blocks, one controller and one item. Only the base accepts cables. */
    static final class Tower extends Oriented {
        Tower(){super(NetworkEquipment.TOWER.id);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(PART,0).withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,PART,ACTIVE);}
        @Override public IBlockState getStateFromMeta(int m){return super.getStateFromMeta(m).withProperty(PART,Math.min(2,m>>2));}
        @Override public int getMetaFromState(IBlockState s){return super.getMetaFromState(s)|(s.getValue(PART)<<2);}
        @Override public boolean hasTileEntity(IBlockState s){return s.getValue(PART)==0;}
        @Override public TileEntity createNewTileEntity(World w,int meta){return (meta>>2)==0?new Tiles.Station():null;}
        static BlockPos base(IBlockAccess w,BlockPos p){IBlockState s=w.getBlockState(p);return s.getBlock() instanceof Tower?p.down(s.getValue(PART)):p;}
        @Override public boolean canPlaceBlockAt(World w,BlockPos p){return p.getY()<w.getHeight()-2&&super.canPlaceBlockAt(w,p)&&w.getBlockState(p.up()).getBlock().isReplaceable(w,p.up())&&w.getBlockState(p.up(2)).getBlock().isReplaceable(w,p.up(2));}
        @Override public void onBlockPlacedBy(World w,BlockPos p,IBlockState s,EntityLivingBase e,ItemStack item){super.onBlockPlacedBy(w,p,s,e,item);if(!w.isRemote&&s.getValue(PART)==0){w.setBlockState(p.up(),s.withProperty(PART,1),3);w.setBlockState(p.up(2),s.withProperty(PART,2),3);}}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(base(w,p));return s.withProperty(ACTIVE,t instanceof Tiles.Station&&((Tiles.Station)t).online);}
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){return false;}
        @Override public net.minecraft.item.Item getItemDropped(IBlockState s,java.util.Random r,int fortune){return s.getValue(PART)==0?super.getItemDropped(s,r,fortune):net.minecraft.init.Items.AIR;}
        @Override public void onBlockHarvested(World w,BlockPos p,IBlockState s,EntityPlayer player){if(!w.isRemote&&player.capabilities.isCreativeMode&&s.getValue(PART)!=0){BlockPos q=base(w,p);IBlockState b=w.getBlockState(q);if(b.getBlock()==this&&b.getValue(PART)==0)w.setBlockToAir(q);}super.onBlockHarvested(w,p,s,player);}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){int part=s.getValue(PART);BlockPos q=p.down(part);super.breakBlock(w,p,s);if(!w.isRemote){if(part!=0){IBlockState b=w.getBlockState(q);if(b.getBlock()==this&&b.getValue(PART)==0){w.destroyBlock(q,true);return;}}for(int i=1;i<=2;i++){BlockPos x=q.up(i);IBlockState b=w.getBlockState(x);if(!x.equals(p)&&b.getBlock()==this&&b.getValue(PART)==i)w.setBlockToAir(x);}}}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return s.getValue(PART)==0?new AxisAlignedBB(.125,0,.125,.875,1,.875):s.getValue(PART)==1?new AxisAlignedBB(.3125,0,.3125,.6875,1,.6875):new AxisAlignedBB(.125,0,.125,.875,1,.875);}
        @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return getBoundingBox(s,w,p);}
        @Override int gui(){return -1;}
    }
    /** One two-block terminal, with the controller and energy buffer in its lower half only. */
    static final class Terminal extends Oriented {
        Terminal(){super("computer");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false).withProperty(UPPER,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE,UPPER);}
        @Override public IBlockState getStateFromMeta(int m){return super.getStateFromMeta(m).withProperty(UPPER,(m&4)!=0);}
        @Override public int getMetaFromState(IBlockState s){return super.getMetaFromState(s)|(s.getValue(UPPER)?4:0);}
        @Override public boolean hasTileEntity(IBlockState s){return !s.getValue(UPPER);}
        @Nullable @Override public TileEntity createNewTileEntity(World w,int meta){return (meta&4)!=0?null:new Tiles.Computer();}
        static BlockPos base(IBlockAccess w,BlockPos p){IBlockState s=w.getBlockState(p);return s.getBlock() instanceof Terminal&&s.getValue(UPPER)?p.down():p;}
        @Override public boolean canPlaceBlockAt(World w,BlockPos p){return p.getY()<w.getHeight()-1&&super.canPlaceBlockAt(w,p)&&w.getBlockState(p.up()).getBlock().isReplaceable(w,p.up());}
        @Override public void onBlockPlacedBy(World w,BlockPos p,IBlockState s,EntityLivingBase e,ItemStack item){super.onBlockPlacedBy(w,p,s,e,item);if(!w.isRemote&&!s.getValue(UPPER))w.setBlockState(p.up(),s.withProperty(UPPER,true),3);}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(base(w,p));return s.withProperty(ACTIVE,t instanceof Tiles.Computer&&((Tiles.Computer)t).online);}
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){BlockPos q=base(w,p);return super.onBlockActivated(w,q,w.getBlockState(q),player,hand,side,x,y,z);}
        @Override public void onBlockHarvested(World w,BlockPos p,IBlockState s,EntityPlayer player){
            if(!w.isRemote&&player.capabilities.isCreativeMode&&s.getValue(UPPER)){BlockPos low=p.down();IBlockState other=w.getBlockState(low);if(other.getBlock()==this&&!other.getValue(UPPER))w.setBlockToAir(low);}
            super.onBlockHarvested(w,p,s,player);
        }
        @Override public net.minecraft.item.Item getItemDropped(IBlockState s,java.util.Random random,int fortune){return s.getValue(UPPER)?net.minecraft.init.Items.AIR:super.getItemDropped(s,random,fortune);}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){
            if(!w.isRemote){boolean upper=s.getValue(UPPER);if(!upper){TileEntity t=w.getTileEntity(p);if(t instanceof Tiles.Computer){Tiles.Computer c=(Tiles.Computer)t;TaskData d=TaskData.get(w);d.record(c.owner,c.id,c.id,EventJournal.Level.INFO,EventJournal.Category.HARDWARE,EventJournal.Target.NONE,"terminal_removed","DataOS terminal removed",-1,0);d.journal.move(c.owner,c.id,EventSystem.survivor(c));d.markDirty();}}BlockPos other=upper?p.down():p.up();IBlockState os=w.getBlockState(other);
                if(os.getBlock()==this&&os.getValue(UPPER)!=upper){if(upper)w.destroyBlock(other,true);else w.setBlockToAir(other);}}
            super.breakBlock(w,p,s);
        }
        @Override int gui(){return 2;}
    }
    static final class Decor extends Block {
        Decor(String id){super(Material.IRON);setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setHardness(2.0f);setResistance(8);setSoundType(SoundType.METAL);setCreativeTab(Hardware.TAB);}
    }
    static final class Cable extends Block {
        final boolean power,relay,fiber;
        Cable(String id,boolean power,boolean relay){this(id,power,relay,false);}
        Cable(String id,boolean power,boolean relay,boolean fiber){super(Material.IRON);this.power=power;this.relay=relay;this.fiber=fiber;setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setCreativeTab(Hardware.TAB);setHardness(0.5f);setSoundType(SoundType.METAL);IBlockState s=blockState.getBaseState();for(PropertyBool p:LINK)s=s.withProperty(p,false);setDefaultState(s.withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,LINK[0],LINK[1],LINK[2],LINK[3],LINK[4],LINK[5],ACTIVE);}
        @Override public IBlockState getStateFromMeta(int m){return getDefaultState().withProperty(ACTIVE,relay&&(m&1)!=0);}
        @Override public int getMetaFromState(IBlockState s){return relay&&s.getValue(ACTIVE)?1:0;}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){for(EnumFacing f:EnumFacing.VALUES){IBlockState neighbor=w.getBlockState(p.offset(f));Block b=neighbor.getBlock();boolean antenna=Hardware.antenna(b)&&(!(b instanceof Tower)||neighbor.getValue(PART)==0);boolean yes=fiber?(b==Hardware.FIBER_CABLE||Hardware.networkTerminal(b)||antenna):(b==Hardware.RACK||b==Hardware.RACK_GLASS||b==Hardware.COMPUTER||Hardware.networkTerminal(b)||(power?(antenna||b instanceof Chiller||b==Hardware.POWER_CABLE||b==Hardware.RELAY||b==Hardware.BATTERY||Hardware.transformer(b)):b==Hardware.DATA_CABLE));s=s.withProperty(LINK[f.getIndex()],yes);}return s;}
        @Override public boolean isOpaqueCube(IBlockState s){return false;}
        @Override public boolean isFullCube(IBlockState s){return false;}
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w,IBlockState s,BlockPos p,EnumFacing f){return BlockFaceShape.UNDEFINED;}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){s=getActualState(s,w,p);double lo=relay?.25:.375,hi=1-lo;return new AxisAlignedBB(s.getValue(LINK[4])?0:lo,s.getValue(LINK[0])?0:lo,s.getValue(LINK[2])?0:lo,s.getValue(LINK[5])?1:hi,s.getValue(LINK[1])?1:hi,s.getValue(LINK[3])?1:hi);}
        @Nullable @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return getBoundingBox(s,w,p);}
        @Override public void addCollisionBoxToList(IBlockState s,World w,BlockPos p,AxisAlignedBB entityBox,java.util.List<AxisAlignedBB> boxes,@Nullable net.minecraft.entity.Entity entity,boolean actual){s=getActualState(s,w,p);double lo=relay?.25:.375,hi=1-lo;addCollisionBoxToList(p,entityBox,boxes,new AxisAlignedBB(lo,lo,lo,hi,hi,hi));for(EnumFacing f:EnumFacing.VALUES)if(s.getValue(LINK[f.getIndex()]))addCollisionBoxToList(p,entityBox,boxes,new AxisAlignedBB(f==EnumFacing.WEST?0:.375,f==EnumFacing.DOWN?0:.375,f==EnumFacing.NORTH?0:.375,f==EnumFacing.EAST?1:.625,f==EnumFacing.UP?1:.625,f==EnumFacing.SOUTH?1:.625));}
        void updateRelay(World w,BlockPos p,IBlockState s){if(relay&&!w.isRemote&&s.getValue(ACTIVE)!=w.isBlockPowered(p)){OfflineState.invalidateAt(w,p);w.setBlockState(p,s.withProperty(ACTIVE,w.isBlockPowered(p)),3);PowerState.changed(w,p,false);Network.invalidate(w);Network.revokeDisconnected(w);}}
        @Override public void onBlockAdded(World w,BlockPos p,IBlockState s){super.onBlockAdded(w,p,s);Network.invalidate(w);PowerState.changed(w,p,true);OfflineState.invalidateAt(w,p);PhysicalNetwork.blockAdded(w,p,this);updateRelay(w,p,s);}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){super.breakBlock(w,p,s);Network.invalidate(w);PowerState.changed(w,p,true);OfflineState.invalidateAt(w,p);PhysicalNetwork.blockRemoved(w,p,this);}
        @Override public void neighborChanged(IBlockState s,World w,BlockPos p,Block b,BlockPos from){super.neighborChanged(s,w,p,b,from);updateRelay(w,p,s);}
    }
}
