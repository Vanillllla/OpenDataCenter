package odc;
import static odc.EventJournal.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** One authoritative simulation per connected owner network, each second. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class ClusterScheduler {
    static UUID serverId(ItemStack s){Hardware.ensureId(s);return s.getTagCompound().getUniqueId("ServerId");}
    static UUID key(List<Tiles.Computer> cs){return cs.stream().map(c->c.id).min(Comparator.comparing(UUID::toString)).orElse(null);}
    static UUID key(Tiles.Computer c){Network.Domain d=Network.scan(c.getWorld(),c.getPos());List<Tiles.Computer> cs=new ArrayList<>();if(d!=null)for(Tiles.Owned t:d.devices)if(t instanceof Tiles.Computer&&Objects.equals(c.owner,t.owner))cs.add((Tiles.Computer)t);return cs.isEmpty()?c.id:key(cs);}
    static final class Ref {final Tiles.Rack rack;final int bay;Ref(Tiles.Rack r,int b){rack=r;bay=b;}}
    static int add(int a,int b){return (int)Math.min(Integer.MAX_VALUE,(long)a+b);}
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent e){World w=e.world;if(e.phase!=TickEvent.Phase.END||w.isRemote||w.getTotalWorldTime()%20!=0)return;
        PowerGrid.begin(w);TaskData d=TaskData.get(w);OfflineState offline=OfflineState.get(w);d.clock();for(TaskData.Job j:d.jobs){j.work=j.usedCPU=j.usedGPU=j.usedRAM=j.usedDisk=j.usedNet=0;j.full=false;}
        List<TileEntity> powerOrder=new ArrayList<>(w.loadedTileEntityList);powerOrder.sort(Network.ORDER);for(TileEntity t:powerOrder)if(t instanceof Tiles.Powered&&Network.live(w,t))((Tiles.Powered)t).refreshPower();
        List<TileEntity> loaded=new ArrayList<>();List<Tiles.Rack> racks=new ArrayList<>();Map<Tiles.Rack,Integer> old=new IdentityHashMap<>();
        for(TileEntity t:powerOrder)if(Network.isDataDevice(t)&&Network.live(w,t)){
            loaded.add(t);if(t instanceof Tiles.Computer)((Tiles.Computer)t).resetCycle();
            if(t instanceof Tiles.Rack&&((Tiles.Rack)t).role!=-1){Tiles.Rack r=(Tiles.Rack)t;racks.add(r);old.put(r,r.poweredMask|(r.runningMask<<9)|(r.switchOnline?1<<18:0));r.poweredMask=r.runningMask=r.lastPower=0;r.switchOnline=false;Arrays.fill(r.work,0);Arrays.fill(r.status,0);Arrays.fill(r.cpuUsed,0);Arrays.fill(r.diskUsed,0);r.activeJobs.clear();Arrays.fill(r.gpuUsed,0);Arrays.fill(r.ramUsed,0);Arrays.fill(r.netUsed,0);Arrays.fill(r.powerUsed,0);int st=r.switchTier();if(st>0&&r.energy.useWatts(Hardware.switchPower(st))){r.switchOnline=true;r.lastPower=Hardware.switchPower(st);}for(int b=0;b<r.capacity();b++)if(r.isServer(b)){Integer f=offline.faults.remove(serverId(r.slots.get(b)));if(f!=null){trip(r,b,f,d,false);offline.markDirty();}else if((r.enabledMask&(1<<b))!=0&&!r.energy.fed()){r.energy.tripGrid();if((r.enabledMask&(1<<b))!=0)trip(r,b,6,d);}}}}
        PhysicalNetwork.refreshRouting(w);BandwidthEngine.Result network=PhysicalNetwork.bandwidth(w);
        List<Runnable> settlement=new ArrayList<>();List<EventSystem.Topology> observed=new ArrayList<>();loaded.sort(Network.ORDER);for(Network.Domain domain:Network.domains(w,loaded)){Map<UUID,List<Tiles.Owned>> owners=new LinkedHashMap<>();for(Tiles.Owned t:domain.devices)if(t.owner!=null)owners.computeIfAbsent(t.owner,k->new ArrayList<>()).add(t);for(Map.Entry<UUID,List<Tiles.Owned>> o:owners.entrySet()){Set<UUID> ids=new HashSet<>();for(Tiles.Owned t:o.getValue()){UUID scope=EventSystem.scope(t);if(scope!=null)ids.add(scope);}if(!ids.isEmpty())observed.add(new EventSystem.Topology(o.getKey(),ids,domain.positions));run(w,d,offline,domain.positions,o.getKey(),o.getValue(),network,settlement);}}EventSystem.observe(w,d,observed);
        // Disconnected racks still consume idle energy; no job can run without controller.
        for(Tiles.Rack r:racks){for(int b=0;b<r.capacity();b++)if(r.isServer(b)&&(r.enabledMask&(1<<b))!=0&&(r.poweredMask&(1<<b))==0&&r.status[b]==0){powerIdle(r,b,d,r.networkConnected(b)?ComputeEngine.NO_CONTROLLER:ComputeEngine.NO_NETWORK);}}PowerGrid.finish(w);PhysicalNetwork.refreshRouting(w);for(Runnable commit:settlement)commit.run();for(Tiles.Rack r:racks){int now=r.poweredMask|(r.runningMask<<9)|(r.switchOnline?1<<18:0);if(now!=old.get(r))r.changed();}offline.tick(w,d);d.finishCycle();
    }
    private static void powerIdle(Tiles.Rack r,int b,TaskData d,int status){
        ItemStack stack=r.slots.get(b);
        if(Hardware.state(stack)!=ServerSpec.READY){r.status[b]=ComputeEngine.INCOMPLETE;return;}
        int idle=Hardware.idle(stack);
        if(idle<0){trip(r,b,ComputeEngine.PSU_OVERLOAD,d);return;}
        if(!r.energy.useWatts(idle)){trip(r,b,ComputeEngine.NO_POWER,d);return;}
        r.poweredMask|=1<<b;r.lastPower+=idle;r.powerUsed[b]=idle;r.status[b]=status;
    }
    static void trip(Tiles.Rack r,int b,int reason,TaskData d){trip(r,b,reason,d,true);}
    static void trip(Tiles.Rack r,int b,int reason,TaskData d,boolean journal){d.serverStopped(serverId(r.slots.get(b)),false);r.enabledMask&=~(1<<b);r.poweredMask&=~(1<<b);r.runningMask&=~(1<<b);r.lastPower=Math.max(0,r.lastPower-r.powerUsed[b]);r.powerUsed[b]=0;r.status[b]=reason;r.fault[b]=reason;OfflineState.invalidateAt(r.getWorld(),r.getPos());r.changed();if(journal)EventSystem.rack(r,b,Level.CRITICAL,Category.POWER,reason==11?"psu_overload":"power_loss",reason==11?"PSU output capacity exceeded":"External supply unavailable or insufficient");}
    static void run(World w,TaskData d,OfflineState offline,Set<net.minecraft.util.math.BlockPos> positions,UUID owner,List<Tiles.Owned> devices,BandwidthEngine.Result network,List<Runnable> settlement){List<Tiles.Rack> racks=new ArrayList<>();List<Tiles.Computer> cs=new ArrayList<>();int wan=0,lan=0,servers=0,power=0;boolean online=false;
        for(Tiles.Owned t:devices){if(t instanceof Tiles.NetworkTerminal){Tiles.NetworkTerminal s=(Tiles.NetworkTerminal)t;if(s.online&&s.energy.fed())power=add(power,s.idlePower());}else if(t instanceof Tiles.Computer){Tiles.Computer c=(Tiles.Computer)t;cs.add(c);if(c.online&&c.energy.fed()){online=true;power=add(power,c.idlePower());}}else if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;racks.add(r);servers+=r.serverCount();if(r.switchOnline)lan=add(lan,Hardware.switchMbps(r.switchTier()));}}
        UUID anchor=key(cs);List<TaskData.Job> jobs=new ArrayList<>();if(anchor!=null){d.market.supply(owner,d.now());Set<UUID> ids=new HashSet<>();for(Tiles.Computer c:cs)ids.add(c.id);for(TaskData.Job j:d.jobs)if(owner.equals(j.owner)&&ids.contains(j.anchor)){j.anchor=anchor;jobs.add(j);}for(UUID id:ids)if(!id.equals(anchor)){long[] a=d.wallet(id);for(int k=0;k<2;k++){long amount=Math.min(a[k],EconomyEngine.LIMIT-d.wallet(anchor)[k]);if(amount>0)d.transfer(id,anchor,k,amount);}}}
        if(!cs.isEmpty())d.observeNetwork(cs.get(0),anchor);
        Set<Integer> networkBlocked=new HashSet<>();for(TaskData.Job j:jobs)if(j.enabled){boolean ok=NetworkAdmission.served(network,j.terms.getUniqueId("Key"),j.bindings);if(!ok)networkBlocked.add(j.id);d.routeState(j,ok);}
        List<TaskEngine.Node> nodes=new ArrayList<>();List<Ref> refs=new ArrayList<>();Set<UUID> seen=new HashSet<>();int[] caps=new int[racks.size()];
        for(int ri=0;ri<racks.size();ri++){Tiles.Rack r=racks.get(ri);int st=r.switchTier();caps[ri]=r.switchOnline?Hardware.switchMbps(st):0;
            for(int b=0;b<r.capacity();b++)if(r.isServer(b)){if((r.enabledMask&(1<<b))==0){r.status[b]=r.fault[b];continue;}ItemStack s=r.slots.get(b);if(Hardware.state(s)!=ServerSpec.READY){r.status[b]=ComputeEngine.INCOMPLETE;continue;}UUID id=serverId(s);if(!seen.add(id)){r.status[b]=ComputeEngine.INCOMPATIBLE;continue;}if(!r.networkConnected(b)){d.networkState(r,b,false);continue;}int[] p=Hardware.spec(s);int net=online&&r.switchOnline?r.patchLimit(b):0;
                d.networkState(r,b,net>0&&PhysicalNetwork.hasWanRoute(w,id));nodes.add(new TaskEngine.Node(id,ri,net,p,d.poolId(owner,id)));refs.add(new Ref(r,b));}}
        Set<Integer> excluded=new HashSet<>();TaskEngine.Plan plan;
        while(true){plan=d.plan(anchor,nodes,jobs,caps,online?Integer.MAX_VALUE:0,excluded,networkBlocked);boolean repeat=false;for(int i:plan.overload)if(excluded.add(i)){Ref f=refs.get(i);trip(f.rack,f.bay,11,d);repeat=true;}if(repeat)continue;
            PowerEngine.Preview budget=new PowerEngine.Preview();
            for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){
                Ref f=refs.get(i);if((f.rack.enabledMask&(1<<f.bay))==0||!f.rack.energy.closed()){excluded.add(i);repeat=true;continue;}
                if(ODC.requireEnergy&&!budget.use(f.rack.energy.budget(),plan.power[i])){
                    f.rack.energy.tripGrid();if((f.rack.enabledMask&(1<<f.bay))!=0)trip(f.rack,f.bay,6,d);
                    excluded.add(i);repeat=true;
                }
            }
            if(repeat){online=cs.stream().anyMatch(Tiles.Computer::operational);for(int ri=0;ri<racks.size();ri++)caps[ri]=racks.get(ri).switchOnline?Hardware.switchMbps(racks.get(ri).switchTier()):0;}
            if(!repeat)break;}
        // All allocations committed only after power reservation was validated.
        boolean paid=true;for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){Ref f=refs.get(i);Tiles.Rack r=f.rack;int b=f.bay;if((r.enabledMask&(1<<b))==0||!r.energy.useWatts(plan.power[i])){if((r.enabledMask&(1<<b))!=0)trip(r,b,6,d);paid=false;continue;}r.poweredMask|=1<<b;r.lastPower+=plan.power[i];r.cpuUsed[b]=plan.cpu[i];r.diskUsed[b]=plan.disk[i];r.gpuUsed[b]=plan.gpu[i];r.ramUsed[b]=plan.ram[i];BandwidthEngine.Usage nu=network.devices.get(nodes.get(i).id);r.netUsed[b]=nu==null?0:NetworkAdmission.floor(nu.served);r.powerUsed[b]=plan.power[i];r.status[b]=!online?ComputeEngine.NO_CONTROLLER:!r.switchOnline||r.portOf(b)<0||!PhysicalNetwork.hasWanRoute(w,nodes.get(i).id)?ComputeEngine.NO_NETWORK:ComputeEngine.IDLE;}
        // A disconnected server may remain powered, but must never enter a compute pool.
        for(Tiles.Rack r:racks)for(int b=0;b<r.capacity();b++)if(r.isServer(b)&&!r.networkConnected(b)&&(r.enabledMask&(1<<b))!=0&&(r.poweredMask&(1<<b))==0&&r.status[b]==0){
            powerIdle(r,b,d,ComputeEngine.NO_NETWORK);
        }
        final TaskEngine.Plan finalPlan=plan;final boolean initialPaid=paid;
        final UUID finalAnchor=anchor;final int serverCount=servers;
        settlement.add(()->{
            boolean controller=cs.stream().anyMatch(Tiles.Computer::operational);
            boolean complete=initialPaid;
            for(int i=0;i<refs.size();i++){Ref f=refs.get(i);if(!excluded.contains(i)&&(f.rack.poweredMask&(1<<f.bay))==0){excluded.add(i);complete=false;}}
            int switching=0,actualPower=0;
            for(int ri=0;ri<racks.size();ri++){Tiles.Rack rack=racks.get(ri);caps[ri]=rack.switchOnline?Hardware.switchMbps(rack.switchTier()):0;switching=add(switching,caps[ri]);actualPower=add(actualPower,rack.lastPower);}
            for(Tiles.Owned tile:devices)if(tile instanceof Tiles.Powered&&((Tiles.Powered)tile).operational())actualPower=add(actualPower,((Tiles.Powered)tile).idlePower());
            if(finalAnchor!=null){
                TaskConsole.Domain live=new TaskConsole.Domain(nodes,caps,controller?Integer.MAX_VALUE:0,excluded,w.getTotalWorldTime(),w);
                if(complete&&controller)live.plan=finalPlan;
                d.domains.put(finalAnchor,live);
            }
            int active=0,hash=0,used=0;
            BandwidthEngine.Result current=PhysicalNetwork.bandwidth(w);
            for(TaskData.Job j:jobs){
                TaskEngine.Result result=finalPlan.tasks.get(j.id);if(result==null)continue;
                boolean funded=controller&&initialPaid&&j.enabled&&NetworkAdmission.served(current,j.terms.getUniqueId("Key"),j.bindings);
                for(TaskEngine.Slice slice:result.slices)if(excluded.contains(slice.node)||!refs.get(slice.node).rack.switchOnline)funded=false;
                if(j.enabled)d.resourceState(j,!funded||result.work<=0||!result.full);
                if(!funded)continue;
                j.work=result.work;j.usedCPU=result.cpu;j.usedGPU=result.gpus;j.usedRAM=result.ram;j.usedDisk=result.disk;j.usedNet=result.net;j.full=result.full;
                if(result.work<=0)continue;
                d.account(j,result);if(j.type<9)hash=add(hash,result.work);used=add(used,(result.net+9)/10);
                for(TaskEngine.Slice slice:result.slices){Ref f=refs.get(slice.node);f.rack.activeJobs.computeIfAbsent(f.bay,k->new ArrayList<>()).add(j.id);f.rack.runningMask|=1<<f.bay;f.rack.work[f.bay]+=slice.work;f.rack.status[f.bay]=result.full?ComputeEngine.RUNNING:ComputeEngine.THROTTLED;active++;}
            }
            for(Tiles.Computer c:cs){NetworkMonitor mon=PhysicalNetwork.monitor(w,c.getPos());c.racks=racks.size();c.servers=serverCount;c.uplink=NetworkAdmission.floor(mon.wan+mon.ingressFree);c.switching=switching;c.networkUsed=NetworkAdmission.floor(mon.wan);c.clusterPower=actualPower;c.miningStats=new int[]{jobs.size(),active,hash,used,0,0};}
            d.markDirty();if(controller&&complete&&!jobs.isEmpty())offline.capture(w,finalAnchor,owner,positions,devices,racks,nodes,refs,excluded,caps,0);
        });
    }
}
