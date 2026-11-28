package odc;

import api.hbm.energy.IEnergyConnector;
import api.hbm.energy.IEnergyConductor;
import api.hbm.energy.IPowerNet;
import com.hbm.lib.ForgeDirection;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Loaded only with HBM installed. API signatures verified against Extended 3.0.3.
 * No private fields or setPower calls: HBM transfers energy to its subscribers. */
final class HbmBridge {
    static void subscribe(Tiles.Transformer t){
        IEnergyConnector receiver=(IEnergyConnector)t;
        for(EnumFacing side:EnumFacing.VALUES){
            BlockPos p=t.getPos().offset(side);
            if(!t.getWorld().isBlockLoaded(p))continue;
            TileEntity tile=t.getWorld().getTileEntity(p);
            if(tile instanceof IEnergyConductor){
                IEnergyConductor conductor=(IEnergyConductor)tile;
                IPowerNet net=conductor.getPowerNet();
                if(net!=null&&net.isValid()&&conductor.canConnect(ForgeDirection.getOrientation(side.getOpposite()))&&!net.isSubscribed(receiver))net.subscribe(receiver);
            }
        }
    }
}
