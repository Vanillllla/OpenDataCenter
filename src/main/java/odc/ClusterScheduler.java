package odc;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Single authoritative scheduling pass per loaded server world, per 20 game ticks.
 * A physical server executes at most one workload. Multiple servers contribute to each task.
 * Each local domain shares its owner-specific uplink and switch capacity across all controllers.
 */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class ClusterScheduler {
    private ClusterScheduler(){}
    private static final class Request {
        final Tiles.Rack rack;final Tiles.Computer controller;final int bay,task,rackIndex;final ComputeEngine.Profile profile;
        Request(Tiles.Rack r,Tiles.Computer c,int b,int t,ComputeEngine.Profile p,int index){rack=r;controller=c;bay=b;task=t;profile=p;rackIndex=index;}
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.world.isRemote||e.world.getTotalWorldTime()%ComputeEngine.TICKS!=0)return;
        World w=e.world;List<TileEntity> loaded=new ArrayList<>();
        for(TileEntity t:new ArrayList<>(w.loadedTileEntityList))if(Network.isDataDevice(t)&&Network.live(w,t))loaded.add(t);
        loaded.sort(Network.ORDER);
        List<Tiles.Rack> racks=new ArrayList<>();
        for(TileEntity t:loaded){if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;r.resetCycle();racks.add(r);}else if(t instanceof Tiles.Computer)((Tiles.Computer)t).resetCycle();}
        List<Network.Domain> domains=Network.domains(w,loaded);
        if(domains!=null){int rotation=(int)(w.getTotalWorldTime()/ComputeEngine.TICKS);
            for(Network.Domain d:domains){Map<UUID,List<Tiles.Owned>> byOwner=new LinkedHashMap<>();
                for(Tiles.Owned t:d.devices)if(t.owner!=null)byOwner.computeIfAbsent(t.owner,k->new ArrayList<>()).add(t);
                for(Map.Entry<UUID,List<Tiles.Owned>> group:byOwner.entrySet())runGroup(w,group.getKey(),group.getValue(),rotation);
            }
        }
        for(Tiles.Rack r:racks)r.finishCycle();
    }
    private static int add(int a,int b){return (int)Math.min(Integer.MAX_VALUE,(long)a+b);}
    private static void runGroup(World w,UUID owner,List<Tiles.Owned> devices,int rotation){
        int uplink=0,switching=0,clusterPower=0;
        List<Tiles.Rack> racks=new ArrayList<>();Map<UUID,Tiles.Computer> controllers=new LinkedHashMap<>();
        for(Tiles.Owned t:devices){
            if(t instanceof Tiles.Station&&((Tiles.Station)t).online){uplink=add(uplink,ODC.stationBandwidth);clusterPower=add(clusterPower,12);}
            else if(t instanceof Tiles.Switch&&((Tiles.Switch)t).online){switching=add(switching,ODC.switchBandwidth);clusterPower=add(clusterPower,8);}
            else if(t instanceof Tiles.Computer){Tiles.Computer c=(Tiles.Computer)t;controllers.putIfAbsent(c.id,c);if(c.online)clusterPower=add(clusterPower,5);}
            else if(t instanceof Tiles.Rack)racks.add((Tiles.Rack)t);
        }
        int capacity=ComputeEngine.networkCapacity(uplink,switching),serverCount=0;
        for(Tiles.Rack r:racks)serverCount+=r.serverCount();
        List<Request> requests=new ArrayList<>();
        int[] budgets=new int[racks.size()];
        for(int rackIndex=0;rackIndex<racks.size();rackIndex++){
            Tiles.Rack r=racks.get(rackIndex);budgets[rackIndex]=ODC.requireEnergy?r.energy.getEnergyStored():Integer.MAX_VALUE;
            Tiles.Computer c=controllers.get(r.controller);
            if(c==null)continue;
            r.controllerPosition=c.getPos();
            for(int bay=0;bay<4;bay++){
                int task=r.assignments[bay];if(task==0||r.startAt(bay)!=bay)continue;
                int[] stats=c.jobStats[task-1];stats[0]++;
                ComputeEngine.Profile p=Hardware.profile(r.slots.get(bay),task);r.neededMbps[bay]=p.mbps;stats[5]=add(stats[5],p.mbps);
                if(!c.online){r.status[bay]=ComputeEngine.CONTROLLER_OFFLINE;continue;}
                if(!c.enabled(task)){r.status[bay]=ComputeEngine.TASK_DISABLED;continue;}
                if(p.work==0){r.status[bay]=ComputeEngine.INCOMPATIBLE;continue;}
                if(capacity==0){r.status[bay]=ComputeEngine.NO_NETWORK;continue;}
                requests.add(new Request(r,c,bay,task,p,rackIndex));
            }
        }
        ComputeEngine.Profile[] profiles=new ComputeEngine.Profile[requests.size()];int[] rackOf=new int[requests.size()];
        for(int i=0;i<requests.size();i++){profiles[i]=requests.get(i).profile;rackOf[i]=requests.get(i).rackIndex;}
        ComputeEngine.Plan plan=ComputeEngine.plan(profiles,rackOf,budgets,capacity,rotation);int used=0;
        Market.Ledger ledger=null;
        for(int i=0;i<requests.size();i++){
            Request q=requests.get(i);Tiles.Rack r=q.rack;int b=q.bay,grant=plan.bandwidth[i];
            if(grant==0){r.status[b]=plan.energyLimitedBandwidth[i]==0?ComputeEngine.NO_POWER:ComputeEngine.NO_NETWORK;continue;}
            int cost=plan.energyCost[i];
            if(!r.energy.use(cost)){r.status[b]=ComputeEngine.NO_POWER;continue;}
            long micro=q.profile.reward(grant)+r.remainder[b];int reward=(int)Math.min(Integer.MAX_VALUE,micro/1000);r.remainder[b]=(int)(micro%1000);
            if(reward>0){if(ledger==null)ledger=Market.Ledger.get(w);ledger.credit(owner,reward);}
            r.markDirty();r.nextMask|=1<<b;r.status[b]=grant<q.profile.mbps?ComputeEngine.THROTTLED:ComputeEngine.RUNNING;
            r.usedMbps[b]=grant;r.work[b]=q.profile.effectiveWork(grant);r.totalMbps=add(r.totalMbps,grant);r.totalReward=add(r.totalReward,reward);
            r.lastPower=add(r.lastPower,(cost+ComputeEngine.TICKS-1)/ComputeEngine.TICKS);used=add(used,grant);
            int[] s=q.controller.jobStats[q.task-1];s[1]++;s[2]=add(s[2],r.work[b]);s[3]=add(s[3],grant);s[4]=add(s[4],reward);
        }
        for(Tiles.Rack r:racks)clusterPower=add(clusterPower,r.lastPower);
        for(Tiles.Computer c:controllers.values()){c.racks=racks.size();c.servers=serverCount;c.uplink=uplink;c.switching=switching;c.networkUsed=used;c.clusterPower=clusterPower;}
    }
}
