package api.hbm.energy;
import com.hbm.lib.ForgeDirection;
public interface IEnergyConnector extends ILoadedTile {long transferPower(long power);long getPower();long getMaxPower();default long getTransferWeight(){return 0;}default boolean canConnect(ForgeDirection dir){return false;}}
