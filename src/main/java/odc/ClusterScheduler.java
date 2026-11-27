package odc;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Temporary single-workload engine for 0.3. No resource reservation, no offline earnings. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class ClusterScheduler {
    private ClusterScheduler(){}
    static final class Request { final Tiles.Rack rack;final int bay;final ComputeEngine.Profile profile;Request(Tiles.Rack r,int b,ComputeEngine.Profile p){rack=r;bay=b;profile=p;} }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent e){
        World w=e.world;if(e.phase!=TickEvent.Phase.END||w.isRemote||w.getTotalWorldTime()%20!=0)return;
        Network.revokeDisconnected(w);
        List<TileEntity> loaded=new ArrayList<>();List<Tiles.Rack> racks=new ArrayList<>();Map<Tiles.Rack,Integer> old=new IdentityHashMap<>();
        for(TileEntity t:new ArrayList<>(w.loadedTileEntityList))if(Network.isDataDevice(t)&&Network.live(w,t)){
            loaded.add(t);if(t instanceof Tiles.Computer)((Tiles.Computer)t).resetCycle();
            if(t instanceof Tiles.Rack&&((Tiles.Rack)t).role!=-1){Tiles.Rack r=(Tiles.Rack)t;racks.add(r);old.put(r,r.poweredMask|(r.runningMask<<9)|(r.switchOnline?1<<18:0));r.poweredMask=r.runningMask=r.lastPower=0;r.switchOnline=false;Arrays.fill(r.work,0);Arrays.fill(r.status,0);}}
        loaded.sort(Network.ORDER);int rotation=(int)(w.getTotalWorldTime()/20);
        // Idle loads are charged even without an active terminal or WAN connection.
        for(Tiles.Rack r:racks){int st=r.switchTier();if(st>0&&r.energy.use(20*Hardware.switchPower(st))){r.switchOnline=true;r.lastPower+=Hardware.switchPower(st);}
            for(int b=0;b<r.capacity();b++)if(r.isServer(b)){if(Hardware.state(r.slots.get(b))!=ServerSpec.READY){r.status[b]=ComputeEngine.INCOMPLETE;continue;}if((r.enabledMask&(1<<b))==0)continue;int idle=Hardware.idle(r.slots.get(b));if(r.energy.use(idle*20)){r.poweredMask|=1<<b;r.lastPower+=idle;}else r.status[b]=ComputeEngine.NO_POWER;}}
        for(Network.Domain d:Network.domains(w,loaded)){Map<UUID,List<Tiles.Owned>> owners=new LinkedHashMap<>();for(Tiles.Owned t:d.devices)if(t.owner!=null)owners.computeIfAbsent(t.owner,k->new ArrayList<>()).add(t);for(Map.Entry<UUID,List<Tiles.Owned>> o:owners.entrySet())run(w,o.getKey(),o.getValue(),rotation);}
        for(Tiles.Rack r:racks){int now=r.poweredMask|(r.runningMask<<9)|(r.switchOnline?1<<18:0);if(now!=old.get(r))r.changed();}
    }
    private static int add(int a,int b){return (int)Math.min(Integer.MAX_VALUE,(long)a+b);}
    private static void run(World w,UUID owner,List<Tiles.Owned> devices,int rotation){
        List<Tiles.Rack> racks=new ArrayList<>();List<Tiles.Computer> cs=new ArrayList<>();int wan=0,lan=0,servers=0,power=0;boolean online=false;
        for(Tiles.Owned t:devices){if(t instanceof Tiles.Station){Tiles.Station s=(Tiles.Station)t;if(s.online&&s.energy.fed()){wan=add(wan,s.bandwidth());power=add(power,s.idlePower());}}else if(t instanceof Tiles.Computer){Tiles.Computer c=(Tiles.Computer)t;cs.add(c);if(c.online&&c.energy.fed()){online=true;power=add(power,5);}}else if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;racks.add(r);servers=add(servers,r.serverCount());if(r.switchOnline)lan=add(lan,Hardware.switchMbps(r.switchTier()));}}
        List<Request> requests=new ArrayList<>();List<Integer> portCaps=new ArrayList<>(),rackOf=new ArrayList<>();int assigned=0,requested=0;
        int[] switchCaps=new int[racks.size()],budgets=new int[racks.size()];
        for(int ri=0;ri<racks.size();ri++){Tiles.Rack r=racks.get(ri);int st=r.switchTier();switchCaps[ri]=r.switchOnline?Hardware.switchMbps(st):0;budgets[ri]=ODC.requireEnergy?r.energy.getEnergyStored():Integer.MAX_VALUE;
            for(int b=0;b<r.capacity();b++){if(!r.isServer(b)||(r.miningMask&(1<<b))==0)continue;assigned++;if((r.poweredMask&(1<<b))==0)continue;if(!online){r.status[b]=ComputeEngine.NO_CONTROLLER;continue;}
                ComputeEngine.Profile profile=Hardware.profile(r.slots.get(b));requested=add(requested,profile.mbps);if(!r.switchOnline||r.portOf(b)<0||wan==0||profile.work<=0){r.status[b]=ComputeEngine.NO_NETWORK;continue;}
                requests.add(new Request(r,b,profile));portCaps.add(Math.min(Hardware.nic(r.slots.get(b)),Hardware.portMbps(st)));rackOf.add(ri);}}
        int n=requests.size();ComputeEngine.Profile[] profiles=new ComputeEngine.Profile[n];int[] owners=new int[n],limits=new int[n];for(int i=0;i<n;i++){profiles[i]=requests.get(i).profile;owners[i]=rackOf.get(i);limits[i]=portCaps.get(i);}
        ComputeEngine.Plan plan=ComputeEngine.plan(profiles,owners,limits,switchCaps,budgets,wan,rotation);int[] grant=plan.bandwidth;
        int active=0,hash=0,used=0,reward=0;Market.Ledger ledger=null;
        for(int i=0;i<grant.length;i++){Request q=requests.get(i);Tiles.Rack r=q.rack;int b=q.bay,g=grant[i];if(g<=0){r.status[b]=plan.local[i]>0&&plan.energyLimited[i]==0?ComputeEngine.NO_POWER:ComputeEngine.NO_NETWORK;continue;}int cost=plan.energyCost[i];if(!r.energy.use(cost)){r.status[b]=ComputeEngine.NO_POWER;continue;}
            r.runningMask|=1<<b;r.status[b]=g<q.profile.mbps?ComputeEngine.THROTTLED:ComputeEngine.RUNNING;r.work[b]=q.profile.effectiveWork(g);r.lastPower=add(r.lastPower,(cost+19)/20);long micro=q.profile.reward(g)+r.remainder[b];int amount=(int)Math.min(Integer.MAX_VALUE,micro/1000);r.remainder[b]=(int)(micro%1000);r.markDirty();if(amount>0){if(ledger==null)ledger=Market.Ledger.get(w);ledger.credit(owner,amount);}active++;hash=add(hash,r.work[b]);used=add(used,g);reward=add(reward,amount);}
        for(Tiles.Rack r:racks)power=add(power,r.lastPower);
        for(Tiles.Computer c:cs){c.racks=racks.size();c.servers=servers;c.uplink=wan;c.switching=lan;c.networkUsed=used;c.clusterPower=power;c.miningStats=new int[]{assigned,active,hash,used,reward,requested};}
    }
}
