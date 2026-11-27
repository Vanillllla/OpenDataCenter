package odc;
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
        Network.revokeDisconnected(w);TaskData d=TaskData.get(w);OfflineState offline=OfflineState.get(w);d.clock();for(TaskData.Job j:d.jobs){j.work=j.usedCPU=j.usedGPU=j.usedRAM=j.usedDisk=j.usedNet=0;j.full=false;}
        List<TileEntity> loaded=new ArrayList<>();List<Tiles.Rack> racks=new ArrayList<>();Map<Tiles.Rack,Integer> old=new IdentityHashMap<>();
        for(TileEntity t:new ArrayList<>(w.loadedTileEntityList))if(Network.isDataDevice(t)&&Network.live(w,t)){
            loaded.add(t);if(t instanceof Tiles.Computer)((Tiles.Computer)t).resetCycle();
            if(t instanceof Tiles.Rack&&((Tiles.Rack)t).role!=-1){Tiles.Rack r=(Tiles.Rack)t;racks.add(r);old.put(r,r.poweredMask|(r.runningMask<<9)|(r.switchOnline?1<<18:0));r.poweredMask=r.runningMask=r.lastPower=0;r.switchOnline=false;Arrays.fill(r.work,0);Arrays.fill(r.status,0);Arrays.fill(r.cpuUsed,0);Arrays.fill(r.diskUsed,0);r.activeJobs.clear();Arrays.fill(r.gpuUsed,0);Arrays.fill(r.ramUsed,0);Arrays.fill(r.netUsed,0);Arrays.fill(r.powerUsed,0);int st=r.switchTier();if(st>0&&r.energy.use(20*Hardware.switchPower(st))){r.switchOnline=true;r.lastPower=Hardware.switchPower(st);}for(int b=0;b<r.capacity();b++)if(r.isServer(b)){Integer f=offline.faults.remove(serverId(r.slots.get(b)));if(f!=null){trip(r,b,f,d);offline.markDirty();}else if((r.enabledMask&(1<<b))!=0&&!r.energy.fed())trip(r,b,6,d);}}}
        loaded.sort(Network.ORDER);for(Network.Domain domain:Network.domains(w,loaded)){Map<UUID,List<Tiles.Owned>> owners=new LinkedHashMap<>();for(Tiles.Owned t:domain.devices)if(t.owner!=null)owners.computeIfAbsent(t.owner,k->new ArrayList<>()).add(t);for(Map.Entry<UUID,List<Tiles.Owned>> o:owners.entrySet())run(w,d,offline,domain.positions,o.getKey(),o.getValue());}
        // Disconnected racks still consume idle energy; no job can run without controller.
        for(Tiles.Rack r:racks){for(int b=0;b<r.capacity();b++)if(r.isServer(b)&&(r.enabledMask&(1<<b))!=0&&(r.poweredMask&(1<<b))==0&&r.status[b]==0){int idle=Hardware.idle(r.slots.get(b));if(idle>0&&r.energy.use(idle*20)){r.poweredMask|=1<<b;r.lastPower+=idle;r.powerUsed[b]=idle;r.status[b]=ComputeEngine.NO_CONTROLLER;}else trip(r,b,6,d);}int now=r.poweredMask|(r.runningMask<<9)|(r.switchOnline?1<<18:0);if(now!=old.get(r))r.changed();}offline.tick(w,d);d.finishCycle();
    }
    static void trip(Tiles.Rack r,int b,int reason,TaskData d){d.serverStopped(serverId(r.slots.get(b)),false);r.enabledMask&=~(1<<b);r.poweredMask&=~(1<<b);r.runningMask&=~(1<<b);r.status[b]=reason;r.fault[b]=reason;OfflineState.invalidateAt(r.getWorld(),r.getPos());r.changed();d.event(r.owner,"Server "+serverId(r.slots.get(b)).toString().substring(0,8)+" stopped: "+(reason==11?"PSU overload":"power loss"));}
    static void run(World w,TaskData d,OfflineState offline,Set<net.minecraft.util.math.BlockPos> positions,UUID owner,List<Tiles.Owned> devices){List<Tiles.Rack> racks=new ArrayList<>();List<Tiles.Computer> cs=new ArrayList<>();int wan=0,lan=0,servers=0,power=0;boolean online=false;
        for(Tiles.Owned t:devices){if(t instanceof Tiles.Station){Tiles.Station s=(Tiles.Station)t;if(s.online&&s.energy.fed()){wan=add(wan,s.bandwidth());power=add(power,s.idlePower());}}else if(t instanceof Tiles.Computer){Tiles.Computer c=(Tiles.Computer)t;cs.add(c);if(c.online&&c.energy.fed()){online=true;power=add(power,5);}}else if(t instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)t;racks.add(r);servers+=r.serverCount();if(r.switchOnline)lan=add(lan,Hardware.switchMbps(r.switchTier()));}}
        UUID anchor=key(cs);List<TaskData.Job> jobs=new ArrayList<>();if(anchor!=null){d.market.supply(owner,d.now());Set<UUID> ids=new HashSet<>();for(Tiles.Computer c:cs)ids.add(c.id);for(TaskData.Job j:d.jobs)if(owner.equals(j.owner)&&ids.contains(j.anchor)){j.anchor=anchor;jobs.add(j);}for(UUID id:ids)if(!id.equals(anchor)){long[] a=d.wallet(id);for(int k=0;k<2;k++){long amount=Math.min(a[k],EconomyEngine.LIMIT-d.wallet(anchor)[k]);if(amount>0)d.transfer(id,anchor,k,amount);}}}
        List<TaskEngine.Node> nodes=new ArrayList<>();List<Ref> refs=new ArrayList<>();Set<UUID> seen=new HashSet<>();int[] caps=new int[racks.size()];
        for(int ri=0;ri<racks.size();ri++){Tiles.Rack r=racks.get(ri);int st=r.switchTier();caps[ri]=r.switchOnline?Hardware.switchMbps(st):0;
            for(int b=0;b<r.capacity();b++)if(r.isServer(b)){if((r.enabledMask&(1<<b))==0){r.status[b]=r.fault[b];continue;}ItemStack s=r.slots.get(b);if(Hardware.state(s)!=ServerSpec.READY){r.status[b]=ComputeEngine.INCOMPLETE;continue;}UUID id=serverId(s);if(!seen.add(id)){r.status[b]=ComputeEngine.INCOMPATIBLE;continue;}int[] p=Hardware.spec(s);int net=online&&r.switchOnline&&r.portOf(b)>=0?Math.min(Hardware.nic(s),Hardware.portMbps(st)):0;
                nodes.add(new TaskEngine.Node(id,ri,net,p,d.poolId(owner,id)));refs.add(new Ref(r,b));}}
        Set<Integer> excluded=new HashSet<>();TaskEngine.Plan plan;
        while(true){plan=d.plan(anchor,nodes,jobs,caps,online?wan:0,excluded);boolean repeat=false;for(int i:plan.overload)if(excluded.add(i)){Ref f=refs.get(i);trip(f.rack,f.bay,11,d);repeat=true;}if(repeat)continue;
            int[] budget=new int[racks.size()];for(int ri=0;ri<budget.length;ri++)budget[ri]=ODC.requireEnergy?racks.get(ri).energy.getEnergyStored()/20:Integer.MAX_VALUE;
            for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){int ri=nodes.get(i).rack;if(plan.power[i]>budget[ri]){excluded.add(i);Ref f=refs.get(i);trip(f.rack,f.bay,6,d);repeat=true;}else budget[ri]-=plan.power[i];}if(!repeat)break;}
        // All allocations committed only after power reservation was validated.
        boolean paid=true;for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){Ref f=refs.get(i);Tiles.Rack r=f.rack;int b=f.bay;if(!r.energy.use(plan.power[i]*20)){trip(r,b,6,d);paid=false;continue;}r.poweredMask|=1<<b;r.lastPower+=plan.power[i];r.cpuUsed[b]=plan.cpu[i];r.diskUsed[b]=plan.disk[i];r.gpuUsed[b]=plan.gpu[i];r.ramUsed[b]=plan.ram[i];r.netUsed[b]=(plan.net[i]+9)/10;r.powerUsed[b]=plan.power[i];r.status[b]=!online?ComputeEngine.NO_CONTROLLER:!r.switchOnline||r.portOf(b)<0||wan==0?ComputeEngine.NO_NETWORK:ComputeEngine.IDLE;}
        if(anchor!=null){TaskConsole.Domain live=new TaskConsole.Domain(nodes,caps,online?wan:0,excluded,w.getTotalWorldTime());live.plan=plan;d.domains.put(anchor,live);}
        int active=0,hash=0,used=0; if(paid)for(TaskData.Job j:jobs){TaskEngine.Result result=plan.tasks.get(j.id);if(result==null)continue;j.work=result.work;j.usedCPU=result.cpu;j.usedGPU=result.gpus;j.usedRAM=result.ram;j.usedDisk=result.disk;j.usedNet=result.net;j.full=result.full;if(result.work<=0)continue;d.account(j,result);if(j.type<9)hash=add(hash,result.work);used=add(used,(result.net+9)/10);for(TaskEngine.Slice s:result.slices){Ref f=refs.get(s.node);f.rack.activeJobs.computeIfAbsent(f.bay,k->new ArrayList<>()).add(j.id);f.rack.runningMask|=1<<f.bay;f.rack.work[f.bay]+=s.work;f.rack.status[f.bay]=result.full?ComputeEngine.RUNNING:ComputeEngine.THROTTLED;active++;}}
        for(Tiles.Rack r:racks)power=add(power,r.lastPower);for(Tiles.Computer c:cs){c.racks=racks.size();c.servers=servers;c.uplink=wan;c.switching=lan;c.networkUsed=used;c.clusterPower=power;c.miningStats=new int[]{jobs.size(),active,hash,used,0,0};}d.markDirty();if(online&&paid&&!jobs.isEmpty())offline.capture(w,anchor,owner,positions,devices,racks,nodes,refs,excluded,caps,wan);
    }
}
