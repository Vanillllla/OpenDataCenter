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
    static final PropertyBool ACTIVE=PropertyBool.create("active");
    static final PropertyBool[] LINK={PropertyBool.create("down"),PropertyBool.create("up"),PropertyBool.create("north"),PropertyBool.create("south"),PropertyBool.create("west"),PropertyBool.create("east")};
    static abstract class Oriented extends BlockContainer {
        Oriented(String id){super(Material.IRON);setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setHardness(2.5f);setResistance(8);setSoundType(SoundType.METAL);setCreativeTab(Hardware.TAB);}
        @Override public EnumBlockRenderType getRenderType(IBlockState s){return EnumBlockRenderType.MODEL;}
        @Override public IBlockState getStateFromMeta(int m){return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(m&3));}
        @Override public int getMetaFromState(IBlockState s){return s.getValue(FACING).getHorizontalIndex();}
        @Override public IBlockState getStateForPlacement(World w,BlockPos p,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase e){return getDefaultState().withProperty(FACING,e.getHorizontalFacing().getOpposite());}
        @Override public void onBlockPlacedBy(World w,BlockPos p,IBlockState s,EntityLivingBase e,ItemStack item){super.onBlockPlacedBy(w,p,s,e,item);if(!w.isRemote&&e instanceof EntityPlayer){TileEntity t=w.getTileEntity(p);if(t instanceof Tiles.Owned)((Tiles.Owned)t).setOwner(((EntityPlayer)e).getUniqueID());}}
        @Override public void breakBlock(World w,BlockPos p,IBlockState s){TileEntity t=w.getTileEntity(p);if(!w.isRemote&&t instanceof Tiles.InventoryTile)InventoryHelper.dropInventoryItems(w,p,(Tiles.InventoryTile)t);super.breakBlock(w,p,s);}
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
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){if(!w.isRemote){Tiles.Rack r=RackMultiblock.at(w,p);Tiles.Rack m=r==null?null:r.master();if(m!=null&&m.allows(player)){BlockPos q=m.getPos();player.openGui(ODC.instance,0,w,q.getX(),q.getY(),q.getZ());}}return true;}
        @Override int gui(){return 0;}
    }
    static final class Machine extends Oriented {
        final int type;
        Machine(String id,int type){super(id);this.type=type;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return type==1?new Tiles.Workbench():type==2?new Tiles.Computer():type==3?new Tiles.Battery():new Tiles.Station();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);return s.withProperty(ACTIVE,t instanceof Tiles.Powered?((Tiles.Powered)t).online:t instanceof Tiles.Battery);}
        @Override int gui(){return type==1?1:type==2?2:type>=5?5:-1;}
    }
    static final class Decor extends Block {
        Decor(String id){super(Material.IRON);setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setHardness(2.0f);setResistance(8);setSoundType(SoundType.METAL);setCreativeTab(Hardware.TAB);}
    }
    static final class Cable extends Block {
        final boolean power,relay;
        Cable(String id,boolean power,boolean relay){super(Material.IRON);this.power=power;this.relay=relay;setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setCreativeTab(Hardware.TAB);setHardness(0.5f);setSoundType(SoundType.METAL);IBlockState s=blockState.getBaseState();for(PropertyBool p:LINK)s=s.withProperty(p,false);setDefaultState(s.withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,LINK[0],LINK[1],LINK[2],LINK[3],LINK[4],LINK[5],ACTIVE);}
        @Override public IBlockState getStateFromMeta(int m){return getDefaultState().withProperty(ACTIVE,relay&&(m&1)!=0);}
        @Override public int getMetaFromState(IBlockState s){return relay&&s.getValue(ACTIVE)?1:0;}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){for(EnumFacing f:EnumFacing.VALUES){Block b=w.getBlockState(p.offset(f)).getBlock();boolean yes=b==Hardware.RACK||b==Hardware.RACK_GLASS||b==Hardware.COMPUTER||b==Hardware.STATION||b==Hardware.SATELLITE||(power?(b==Hardware.POWER_CABLE||b==Hardware.RELAY||b==Hardware.BATTERY):b==Hardware.DATA_CABLE);s=s.withProperty(LINK[f.getIndex()],yes);}return s;}
        @Override public boolean isOpaqueCube(IBlockState s){return false;}
        @Override public boolean isFullCube(IBlockState s){return false;}
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w,IBlockState s,BlockPos p,EnumFacing f){return BlockFaceShape.UNDEFINED;}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){s=getActualState(s,w,p);double lo=relay?.25:.375,hi=1-lo;return new AxisAlignedBB(s.getValue(LINK[4])?0:lo,s.getValue(LINK[0])?0:lo,s.getValue(LINK[2])?0:lo,s.getValue(LINK[5])?1:hi,s.getValue(LINK[1])?1:hi,s.getValue(LINK[3])?1:hi);}
        @Nullable @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return getBoundingBox(s,w,p);}
        @Override public void addCollisionBoxToList(IBlockState s,World w,BlockPos p,AxisAlignedBB entityBox,java.util.List<AxisAlignedBB> boxes,@Nullable net.minecraft.entity.Entity entity,boolean actual){s=getActualState(s,w,p);double lo=relay?.25:.375,hi=1-lo;addCollisionBoxToList(p,entityBox,boxes,new AxisAlignedBB(lo,lo,lo,hi,hi,hi));for(EnumFacing f:EnumFacing.VALUES)if(s.getValue(LINK[f.getIndex()]))addCollisionBoxToList(p,entityBox,boxes,new AxisAlignedBB(f==EnumFacing.WEST?0:.375,f==EnumFacing.DOWN?0:.375,f==EnumFacing.NORTH?0:.375,f==EnumFacing.EAST?1:.625,f==EnumFacing.UP?1:.625,f==EnumFacing.SOUTH?1:.625));}
        void updateRelay(World w,BlockPos p,IBlockState s){if(relay&&!w.isRemote&&s.getValue(ACTIVE)!=w.isBlockPowered(p)){w.setBlockState(p,s.withProperty(ACTIVE,w.isBlockPowered(p)),3);Network.revokeDisconnected(w);}}
        @Override public void onBlockAdded(World w,BlockPos p,IBlockState s){super.onBlockAdded(w,p,s);updateRelay(w,p,s);}
        @Override public void neighborChanged(IBlockState s,World w,BlockPos p,Block b,BlockPos from){super.neighborChanged(s,w,p,b,from);updateRelay(w,p,s);}
    }
}
