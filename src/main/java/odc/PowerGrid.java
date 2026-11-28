package odc;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Physical electrical domains, independent of LAN, ownership, and WAN.
 * All consumers share one budget per loaded component and scheduler cycle. */
final class PowerGrid {
    /** Rated capacity is informational; output is the available funded capacity.
     * External source settlement must be integrated before returning nonzero output. */
    interface Source {
        long outputWatts();
        default void gridState(int state){}
        default long ratedWatts(){return outputWatts();}
    }
    static final class Domain {
        final Set<BlockPos> positions;
        final List<TileEntity> consumers=new ArrayList<>();
        final List<Source> sources=new ArrayList<>();
        PowerEngine.Budget budget;
        final World world;
        Set<BlockPos> footprint;
        int state;
        boolean complete;
        long creativeWatts,paidWatts;
        boolean external,confirmed;
        private boolean noiseSeedReady;
        private long noiseSeed;
        int factor(long cycle){
            if(!noiseSeedReady){long anchor=positions.stream().mapToLong(BlockPos::toLong).min().orElse(0);noiseSeed=world.getSeed()^anchor^(0x632be59bd9b4e019L*world.provider.getDimension());noiseSeedReady=true;}
            return PowerEngine.factor(noiseSeed,cycle,ODC.powerFluctuationPercent);
        }
        long displayedWatts(long nominal){return PowerEngine.scale(nominal,budget.factor);}
        OfflineState.Snapshot virtualSnapshot;
        OfflineState.Group virtualGroup;
        Domain(World world,Set<BlockPos> p){this.world=world;positions=p;footprint=p;}
        long ratedWatts(){
            long total=0;
            for(Source source:sources)total=PowerEngine.sum(total,Math.max(0,source.ratedWatts()));
            return total;
        }
        void reset(){
            PowerState saved=PowerState.get(world);
            footprint=virtualGroup!=null?positions:saved.footprint(world,positions);complete=virtualGroup==null&&footprint.equals(positions);
            state=saved.state(footprint);
            // Migrate local switches from 0.6.2.2 once; subsequently the saved grid is authoritative.
            for(Source source:sources)if(source instanceof Tiles.Transformer&&((Tiles.Transformer)source).legacyOff())state=Math.max(state,GridProtection.OFF);
            saved.set(footprint,state);
            if(complete)saved.remember(positions);
            for(Source source:sources)source.gridState(state);
            long capacity=0;creativeWatts=0;external=false;paidWatts=0;confirmed=false;
            if(state==GridProtection.ON)for(Source source:sources){
                if(source instanceof Tiles.Transformer){external=true;capacity=PowerEngine.sum(capacity,source.ratedWatts());}
                else{long watts=Math.max(0,source.outputWatts());creativeWatts=PowerEngine.sum(creativeWatts,watts);capacity=PowerEngine.sum(capacity,watts);}
            }
            budget=new PowerEngine.Budget(capacity,PowerEngine.TICKS_PER_SECOND,factor(world.getTotalWorldTime()/20));
            confirmed=!external||!ODC.requireEnergy;
        }
        long fundedWatts(){return state==GridProtection.ON?PowerEngine.sum(creativeWatts,paidWatts):0;}
        boolean funded(){return state==GridProtection.ON&&confirmed;}
        void prepare(ExternalWindow old){
            for(Source source:sources)if(source instanceof Tiles.Transformer)((Tiles.Transformer)source).closeWindow(old);
            if(old!=null){
                paidWatts=old.paidWatts();
                if(state==GridProtection.ON&&ODC.requireEnergy&&!old.sufficient())shutdown(GridProtection.TRIPPED);
            }
        }
        ExternalWindow finish(){
            if(state!=GridProtection.ON||!external||!ODC.requireEnergy)return null;
            long demand=budget.usedUnits()/PowerEngine.TICKS_PER_SECOND;
            // Request next cycle's factor for the current nominal load. A steady load
            // must not lose funding just because the next random multiplier is larger.
            long nextDemand=Math.min(budget.capacityWatts,PowerEngine.scale(budget.nominalWatts(),factor(world.getTotalWorldTime()/20+1)));
            long needed=Math.max(0,nextDemand-creativeWatts);
            confirmed=PersistentPower.funded(demand,creativeWatts,paidWatts,true);
            // No previous receipt can be reused; the next window is a fresh demand.
            ExternalWindow next=new ExternalWindow(needed);
            for(Source source:sources)if(source instanceof Tiles.Transformer){Tiles.Transformer t=(Tiles.Transformer)source;next.add(t,t.ratedWatts());t.openWindow(next);}
            if(!confirmed){
                // Provisional planning is not usable power. Do not disable server intent;
                // the first receipt window must complete before any workload is credited.
                for(TileEntity tile:consumers){
                    if(tile instanceof Tiles.Powered)((Tiles.Powered)tile).setPowered(false);
                    if(tile instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)tile;r.poweredMask=r.runningMask=0;r.switchOnline=false;r.lastPower=0;Arrays.fill(r.powerUsed,0);Arrays.fill(r.cpuUsed,0);Arrays.fill(r.gpuUsed,0);Arrays.fill(r.ramUsed,0);Arrays.fill(r.diskUsed,0);Arrays.fill(r.netUsed,0);}
                }
            }
            return next;
        }
        boolean use(int watts){
            if(watts<0)return false;
            if(!ODC.requireEnergy&&state==GridProtection.ON)return true;
            if(state!=GridProtection.ON)return false;
            if(budget.use(watts))return true;
            shutdown(GridProtection.TRIPPED);return false;
        }
        void shutdown(int reason){
            if(state!=GridProtection.ON)return;
            PowerState.get(world).set(footprint,reason);
            // Include any loaded fragments of this previously verified physical grid.
            for(Domain d:topology(world).domains)if(!Collections.disjoint(footprint,d.footprint))d.applyOff(reason);
        }
        private void applyOff(int reason){
            boolean transition=state==GridProtection.ON;
            state=Math.max(state,reason);confirmed=false;paidWatts=0;cancel(world,positions);PowerState.get(world).set(footprint,state);
            for(Source s:sources)s.gridState(state);
            budget=new PowerEngine.Budget(0,PowerEngine.TICKS_PER_SECOND);
            publishIndicators();
            TaskData data=TaskData.get(world);
            for(TileEntity tile:consumers){
                OfflineState.invalidateAt(world,tile.getPos());
                if(tile instanceof Tiles.Powered)((Tiles.Powered)tile).setPowered(false);
                if(tile instanceof Tiles.Rack){
                    Tiles.Rack r=(Tiles.Rack)tile;
                    for(int b=0;b<r.capacity();b++)if(r.isServer(b)&&(r.enabledMask&(1<<b))!=0)ClusterScheduler.trip(r,b,ComputeEngine.NO_POWER,data,false);
                    boolean dirty=r.switchOnline||r.lastPower!=0||r.poweredMask!=0||r.runningMask!=0;
                    r.switchOnline=false;r.lastPower=0;r.poweredMask=r.runningMask=0;
                    Arrays.fill(r.powerUsed,0);Arrays.fill(r.work,0);Arrays.fill(r.cpuUsed,0);Arrays.fill(r.gpuUsed,0);Arrays.fill(r.ramUsed,0);Arrays.fill(r.diskUsed,0);Arrays.fill(r.netUsed,0);r.activeJobs.clear();if(dirty)r.changed();
                }
                if(transition&&tile instanceof Tiles.Owned){
                    Tiles.Owned owned=(Tiles.Owned)tile;UUID scope=EventSystem.scope(owned);
                    if(scope!=null&&owned.owner!=null)data.record(owned.owner,scope,scope,
                        reason==GridProtection.TRIPPED?EventJournal.Level.CRITICAL:EventJournal.Level.INFO,
                        EventJournal.Category.POWER,EventJournal.Target.NONE,
                        reason==GridProtection.TRIPPED?"grid_overload":"grid_switched_off",
                        reason==GridProtection.TRIPPED?"Electrical grid tripped: insufficient power":"Electrical grid switched off",-1,0);
                }
            }
        }
        void publishIndicators(){
            boolean powered=funded()&&fundedWatts()>0;
            int lamp=GridProtection.lamp(state,powered);
            for(TileEntity tile:consumers)if(tile instanceof Tiles.Rack)((Tiles.Rack)tile).setPowerLamp(lamp);
            for(Source source:sources)if(source instanceof Tiles.Transformer){
                Tiles.Transformer t=(Tiles.Transformer)source;
                // A closed switch/nameplate alone does not prove external supply.
                t.setPowerLamp(GridProtection.lamp(state,powered&&(t.lastHE>0||t.lastFE>0)));
            }
        }
        boolean toggle(){
            if(state==GridProtection.ON){shutdown(GridProtection.OFF);return true;}
            if(!complete)return false; // do not clear an unknown portion of a saved grid
            applyOff(state); // disable even newly loaded servers before clearing the latch
            PowerState.get(world).set(positions,GridProtection.ON);
            for(Source s:sources)s.gridState(GridProtection.ON);
            for(BlockPos p:positions)OfflineState.invalidateAt(world,p);
            reset();publishIndicators();return true;
        }
    }
    static final class Topology {
        final Map<BlockPos,Domain> byPos=new HashMap<>();
        final Set<BlockPos> failed=new HashSet<>();
        final List<Domain> domains=new ArrayList<>();
        final Map<OfflineState.Group,Domain> virtual=new IdentityHashMap<>();
        final Set<OfflineState.Snapshot> virtualOwners=Collections.newSetFromMap(new IdentityHashMap<OfflineState.Snapshot,Boolean>());
        boolean settling;
    }
    static final class Port {
        final TileEntity tile;
        Port(TileEntity t){tile=t;}
        Domain domain(){World w=tile.getWorld();return w==null||w.isRemote?null:PowerGrid.domain(w,tile.getPos());}
        PowerEngine.Budget budget(){Domain d=domain();return d==null?null:d.budget;}
        boolean fed(){Domain d=domain();return d!=null&&d.state==GridProtection.ON&&(!ODC.requireEnergy||d.budget.capacityWatts>0);}
        boolean useWatts(int watts){Domain d=domain();return d!=null&&d.use(watts);}
        boolean funded(){Domain d=domain();return d!=null&&d.funded();}
        boolean closed(){Domain d=domain();return d!=null&&d.state==GridProtection.ON;}
        void tripGrid(){Domain d=domain();if(d!=null)d.shutdown(GridProtection.TRIPPED);}
        int availableWatts(){PowerEngine.Budget b=budget();return !closed()?0:!ODC.requireEnergy?Integer.MAX_VALUE:b==null?0:(int)Math.min(Integer.MAX_VALUE,domain().fundedWatts());}
    }
    private PowerGrid(){}
    /** Display a LAN subset without summing nominal loads from different grids before scaling. */
    static void addDisplayLoad(Map<Domain,Long> loads,TileEntity tile,long nominal){
        if(nominal<=0)return;
        Domain d=domain(tile.getWorld(),tile.getPos());if(d!=null)loads.merge(d,nominal,PowerEngine::sum);
    }
    static long displayTotal(Map<Domain,Long> loads){long total=0;for(Map.Entry<Domain,Long> e:loads.entrySet())total=PowerEngine.sum(total,e.getKey().displayedWatts(e.getValue()));return total;}
    static boolean settling(World w){return topology(w).settling;}
    static Domain virtualDomain(World w,OfflineState.Group group){return topology(w).virtual.get(group);}
    static void worldUnload(World w){Map<Set<BlockPos>,ExternalWindow> active=windows.remove(w);if(active!=null)for(ExternalWindow window:active.values())window.close();}
    private static final Map<World,Map<Set<BlockPos>,ExternalWindow>> windows=new WeakHashMap<>();
    static void cancel(World w,Set<BlockPos> cells){
        Map<Set<BlockPos>,ExternalWindow> active=windows.get(w);if(active==null)return;
        active.entrySet().removeIf(e->{if(Collections.disjoint(e.getKey(),cells))return false;e.getValue().close();return true;});
    }
    static boolean active(World w,BlockPos p,ExternalWindow window){Map<Set<BlockPos>,ExternalWindow> map=windows.get(w);Domain d=domain(w,p);return map!=null&&d!=null&&d.state==GridProtection.ON&&map.get(d.positions)==window&&(d.virtualSnapshot==null||OfflineState.get(w).validVirtual(w,d.virtualSnapshot));}
    static void begin(World w){
        Map<Set<BlockPos>,ExternalWindow> previous=windows.remove(w);
        if(previous==null)previous=Collections.emptyMap();
        for(ExternalWindow window:previous.values())window.close();
        OfflineState.get(w).checkInterruptedWindows(w,previous);
        Network.Cache cache=Network.cache(w);
        if(cache.power!=null){OfflineState offline=OfflineState.get(w);Set<OfflineState.Snapshot> active=Collections.newSetFromMap(new IdentityHashMap<OfflineState.Snapshot,Boolean>());if(ODC.requireEnergy)for(OfflineState.Snapshot s:offline.snapshots.values())if(offline.validVirtual(w,s))active.add(s);if(!active.equals(cache.power.virtualOwners))cache.power=null;}
        Topology t=topology(w);t.settling=false;for(Domain d:t.domains)d.reset();
        for(Domain d:t.domains){
            int saved=PowerState.get(w).state(d.footprint);if(saved!=GridProtection.ON)d.applyOff(saved);
            ExternalWindow old=previous.get(d.positions);
            // Only the same live sources and exact topology may spend this receipt.
            if(old!=null){int count=0;boolean same=true;for(Source source:d.sources)if(source instanceof Tiles.Transformer){count++;if(!old.receipts.containsKey(source))same=false;}if(!same||count!=old.receipts.size())old=null;}
            d.prepare(old);
        }
    }
    static void finish(World w){
        Map<Set<BlockPos>,ExternalWindow> next=new HashMap<>();
        for(Domain d:topology(w).domains){ExternalWindow window=d.finish();if(window!=null)next.put(new HashSet<>(d.positions),window);}
        windows.put(w,next);Topology current=topology(w);current.settling=true;
        for(Domain d:current.domains)d.publishIndicators();
        // A failed/oversized topology has no verified power. Clear stale render state.
        for(TileEntity tile:w.loadedTileEntityList)if((tile instanceof Tiles.Rack||tile instanceof Tiles.Transformer)&&!current.byPos.containsKey(tile.getPos()))
            ((Tiles.Owned)tile).setPowerLamp(GridProtection.LAMP_OFF);
    }
    static Domain domain(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return null;
        return topology(w).byPos.get(canonical(w,p));
    }
    static Topology topology(World w){
        Network.Cache c=Network.cache(w);
        if(c.power==null)c.power=build(w);
        return c.power;
    }
    private static boolean wire(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return false;
        Block b=w.getBlockState(p).getBlock();
        return b==Hardware.POWER_CABLE||b==Hardware.RELAY&&!w.isBlockPowered(p);
    }
    private static BlockPos canonical(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return p;
        BlockPos q=Blocks.Terminal.base(w,p);
        if(!w.isBlockLoaded(q))return p;
        TileEntity t=w.getTileEntity(q);
        if(t instanceof Tiles.Rack){Tiles.Rack r=((Tiles.Rack)t).master();if(r!=null&&Network.live(w,r))return r.getPos();}
        return q;
    }
    private static TileEntity endpoint(World w,BlockPos p){
        if(!w.isBlockLoaded(p))return null;
        TileEntity t=w.getTileEntity(p);
        if(!Network.live(w,t))return null;
        if(t instanceof Tiles.Rack&&((Tiles.Rack)t).role==-1)return null;
        return t instanceof Source||Network.isPowerDevice(t)?t:null;
    }
    private static boolean allowed(World w,BlockPos p){return wire(w,p)||endpoint(w,p)!=null;}
    /** Consumers may share a bus through their own input contacts, but adjacent consumers
     * alone do not form a power cable. Upper rack/terminal cells are aliases, not loads. */
    private static Iterable<BlockPos> neighbors(World w,BlockPos p){
        List<BlockPos> result=new ArrayList<>();
        TileEntity self=endpoint(w,p);
        boolean conductive=wire(w,p)||self instanceof Source;
        List<BlockPos> contacts=new ArrayList<>();contacts.add(p);
        if(self instanceof Tiles.Rack&&((Tiles.Rack)self).role==1||self instanceof Tiles.Computer){
            BlockPos upper=p.up();if(w.isBlockLoaded(upper)&&canonical(w,upper).equals(p))contacts.add(upper);
        }
        for(BlockPos contact:contacts)for(EnumFacing side:EnumFacing.VALUES){
            BlockPos raw=contact.offset(side);
            if(!w.isBlockLoaded(raw))continue;
            BlockPos q=canonical(w,raw);
            if(q.equals(p))continue;
            TileEntity other=endpoint(w,q);
            if((conductive||wire(w,q)||other instanceof Source)&&allowed(w,q))result.add(q);
        }
        return result;
    }
    private static Topology build(World w){
        Topology t=new Topology();
        List<TileEntity> loaded=new ArrayList<>(w.loadedTileEntityList);loaded.sort(Network.ORDER);
        for(TileEntity tile:loaded){
            BlockPos p=tile.getPos();
            if(endpoint(w,p)!=tile||t.byPos.containsKey(p)||t.failed.contains(p))continue;
            Set<BlockPos> cells=TopologySearch.component(p,q->neighbors(w,q),q->allowed(w,q),Network.LIMIT);
            if(cells==null){t.failed.add(p);continue;} // fail closed; never count a partial domain
            Domain d=new Domain(w,new HashSet<>(cells));
            for(BlockPos q:cells){
                TileEntity device=endpoint(w,q);
                if(device instanceof Source)d.sources.add((Source)device);
                else if(device!=null){
                    d.consumers.add(device);
                    if(device instanceof Tiles.Computer||device instanceof Tiles.Rack&&((Tiles.Rack)device).role==1){
                        BlockPos upper=q.up();if(w.isBlockLoaded(upper)&&canonical(w,upper).equals(q))d.positions.add(upper);
                    }
                }
            }
            d.consumers.sort(Network.ORDER);t.domains.add(d);
            for(BlockPos q:d.positions)t.byPos.put(q,d);
        }
        if(ODC.requireEnergy)attachVirtual(w,t);
        for(Domain d:t.domains)d.reset();
        return t;
    }
    /** A fully virtual consumer group owns the exact previously certified footprint.
     * Loaded source-only fragments are replaced, never added as a second budget. */
    private static void attachVirtual(World w,Topology t){
        OfflineState offline=OfflineState.get(w);
        List<OfflineState.Snapshot> ordered=new ArrayList<>(offline.snapshots.values());
        ordered.sort(Comparator.comparing(s->s.anchor.toString()));
        for(OfflineState.Snapshot snapshot:ordered){
            if(!offline.validVirtual(w,snapshot))continue;
            t.virtualOwners.add(snapshot);
            for(OfflineState.Group group:snapshot.groups){
                if(group.cells.isEmpty())continue;
                Set<Domain> overlaps=Collections.newSetFromMap(new IdentityHashMap<Domain,Boolean>());
                boolean valid=true;
                for(BlockPos p:group.cells){
                    Domain existing=t.byPos.get(p);if(existing!=null)overlaps.add(existing);
                    if(w.isBlockLoaded(p)&&!allowed(w,canonical(w,p))){valid=false;break;}
                }
                for(Domain d:overlaps)if(d.virtualGroup!=null||!d.consumers.isEmpty()||!group.cells.containsAll(d.positions))valid=false;
                if(!valid)continue;
                Domain d=new Domain(w,new HashSet<>(group.cells));
                d.virtualGroup=group;d.virtualSnapshot=snapshot;
                Set<BlockPos> sourcePositions=new HashSet<>();
                for(OfflineState.SourceSpec spec:group.sources){
                    if(!sourcePositions.add(spec.pos)){valid=false;break;}
                    if(!w.isBlockLoaded(spec.pos)){
                        if(spec.external){valid=false;break;}
                        d.sources.add(new SavedCreative(spec.pos,spec.watts));continue;
                    }
                    TileEntity tile=endpoint(w,spec.pos);
                    if(spec.external?!(tile instanceof Tiles.Transformer):!(tile instanceof Tiles.Battery)){valid=false;break;}
                    d.sources.add((Source)tile);
                }
                for(Domain existing:overlaps)for(Source source:existing.sources)
                    if(!(source instanceof TileEntity)||!sourcePositions.contains(((TileEntity)source).getPos()))valid=false;
                if(!valid)continue;
                for(Domain existing:overlaps){t.domains.remove(existing);for(BlockPos p:existing.positions)t.byPos.remove(p);}
                t.domains.add(d);t.virtual.put(group,d);
                for(BlockPos p:group.cells)t.byPos.put(p,d);
            }
        }
    }
    static final class SavedCreative implements Source {
        final BlockPos pos;final long watts;SavedCreative(BlockPos pos,long watts){this.pos=pos;this.watts=watts;}
        @Override public long outputWatts(){return watts;}
    }
}
