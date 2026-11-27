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
    static final PropertyInteger[] BAY={PropertyInteger.create("bay0",0,4),PropertyInteger.create("bay1",0,4),PropertyInteger.create("bay2",0,4),PropertyInteger.create("bay3",0,4)};
    static final PropertyBool ACTIVE=PropertyBool.create("active");
    static final PropertyBool[] LINK={PropertyBool.create("down"),PropertyBool.create("up"),PropertyBool.create("north"),PropertyBool.create("south"),PropertyBool.create("west"),PropertyBool.create("east")};
    static abstract class Oriented extends BlockContainer {
        Oriented(String id){super(Material.IRON);setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setHardness(2.5f);setResistance(8);setSoundType(SoundType.METAL);setCreativeTab(CreativeTabs.REDSTONE);}
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
        Rack(String id,boolean glass){super(id);this.glass=glass;IBlockState s=blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH);for(PropertyInteger p:BAY)s=s.withProperty(p,0);setDefaultState(s);}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,BAY[0],BAY[1],BAY[2],BAY[3]);}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;for(int i=0;i<4;i++)s=s.withProperty(BAY[i],r.bayType(i));}return s;}
        @Override public BlockRenderLayer getRenderLayer(){return glass?BlockRenderLayer.CUTOUT:BlockRenderLayer.SOLID;}
        @Override public TileEntity createNewTileEntity(World w,int meta){return new Tiles.Rack();}
        @Override int gui(){return 0;}
    }
    static final class Machine extends Oriented {
        final int type;
        Machine(String id,int type){super(id);this.type=type;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,ACTIVE);}
        @Override public TileEntity createNewTileEntity(World w,int meta){return type==1?new Tiles.Assembler():type==2?new Tiles.Computer():type==3?new Tiles.Battery():type==4?new Tiles.Switch():new Tiles.Station();}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){TileEntity t=w.getTileEntity(p);return s.withProperty(ACTIVE,t instanceof Tiles.Powered?((Tiles.Powered)t).online:t instanceof Tiles.Battery);}
        @Override int gui(){return type==1?1:type==2?2:type==4?4:type==5?5:-1;}
    }
    static final class Decor extends Block {
        Decor(String id){super(Material.IRON);setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setHardness(2.0f);setResistance(8);setSoundType(SoundType.METAL);setCreativeTab(CreativeTabs.REDSTONE);}
    }
    static final class Cable extends Block {
        final boolean power;
        Cable(String id,boolean power){super(Material.IRON);this.power=power;setRegistryName(ODC.ID,id);setTranslationKey(ODC.ID+"."+id);setCreativeTab(CreativeTabs.REDSTONE);setHardness(0.5f);setSoundType(SoundType.METAL);IBlockState s=blockState.getBaseState();for(PropertyBool p:LINK)s=s.withProperty(p,false);setDefaultState(s);}
        @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,LINK);}
        @Override public IBlockState getStateFromMeta(int m){return getDefaultState();}
        @Override public int getMetaFromState(IBlockState s){return 0;}
        @Override public IBlockState getActualState(IBlockState s,IBlockAccess w,BlockPos p){for(EnumFacing f:EnumFacing.VALUES){Block b=w.getBlockState(p.offset(f)).getBlock();boolean yes=b==this||b==Hardware.RACK||b==Hardware.RACK_GLASS||b==Hardware.COMPUTER||b==Hardware.SWITCH||b==Hardware.STATION||(power&&b==Hardware.BATTERY);s=s.withProperty(LINK[f.getIndex()],yes);}return s;}
        @Override public boolean isOpaqueCube(IBlockState s){return false;}
        @Override public boolean isFullCube(IBlockState s){return false;}
        @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w,IBlockState s,BlockPos p,EnumFacing f){return BlockFaceShape.UNDEFINED;}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){s=getActualState(s,w,p);return new AxisAlignedBB(s.getValue(LINK[4])?0:.375,s.getValue(LINK[0])?0:.375,s.getValue(LINK[2])?0:.375,s.getValue(LINK[5])?1:.625,s.getValue(LINK[1])?1:.625,s.getValue(LINK[3])?1:.625);}
        @Nullable @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return NULL_AABB;}
    }
}
