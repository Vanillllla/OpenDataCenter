package api.hbm.energy;
public interface IPowerNet {boolean isValid();boolean isSubscribed(IEnergyConnector c);void subscribe(IEnergyConnector c);}
