package odc;
import static odc.EventJournal.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Virtual execution only when all recorded consumer endpoints are unloaded.
 * Source chunks may remain loaded; both live and virtual demand use PowerGrid. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class OfflineState extends WorldSavedData {
    static final String KEY="odc_virtual_domains_v053";
    static final class SourceSpec {
        BlockPos pos;boolean external;long watts;
        Set<BlockPos> inputs=new HashSet<>();
    }
    static final class Group {
        Set<BlockPos> cells=new HashSet<>();
        List<SourceSpec> sources=new ArrayList<>();
        long fixed;
        long[] fixedCategories=new long[PowerStats.SIZE];
        boolean controller;
    }
    static final class Snapshot {
        UUID anchor,owner;
        Set<BlockPos> positions=new HashSet<>(),consumers=new HashSet<>();
        List<TaskEngine.Node> nodes=new ArrayList<>();
        int[] caps,budget,gridByRack;
        List<Group> groups=new ArrayList<>();
        Set<PhysicalTopology.Pos> routing=new HashSet<>();
        PersistentThermal thermal=new PersistentThermal();
    }
    final Map<UUID,Snapshot> snapshots=new HashMap<>();final Map<UUID,Integer> faults=new HashMap<>();
    final Map<UUID,PersistentThermal.Server> pendingThermal=new HashMap<>();
    public OfflineState(){super(KEY);}public OfflineState(String n){super(n);}
    static OfflineState get(World w){net.minecraft.world.storage.MapStorage s=w.getPerWorldStorage();OfflineState d=(OfflineState)s.getOrLoadData(OfflineState.class,KEY);if(d==null){d=new OfflineState();s.setData(KEY,d);}return d;}
    static PersistentPower.Mode mode(World w,Snapshot s){
        int loaded=0;for(BlockPos p:s.consumers)if(w.isBlockLoaded(p))loaded++;
        if(s.consumers.isEmpty())return PersistentPower.Mode.PARTIAL;
        PersistentPower.Mode mode=PersistentPower.mode(loaded,s.consumers.size());
        return mode==PersistentPower.Mode.LIVE&&!allLoaded(w,s)?PersistentPower.Mode.PARTIAL:mode;
    }
    boolean active(TaskData d,Snapshot s){for(TaskData.Job j:d.jobs)if(j.enabled&&s.anchor.equals(j.anchor)&&s.owner.equals(j.owner)&&!j.terms.hasKey("End"))return true;return false;}
    boolean validVirtual(World w,Snapshot s){return snapshots.get(s.anchor)==s&&mode(w,s)==PersistentPower.Mode.VIRTUAL&&active(TaskData.get(w),s);}
    boolean hasVirtual(World w){for(Snapshot s:snapshots.values())if(validVirtual(w,s))return true;return false;}
    Set<BlockPos> paused(World w,TaskData d){
        Set<BlockPos> out=new HashSet<>();
        for(Snapshot s:snapshots.values())if(active(d,s)&&mode(w,s)==PersistentPower.Mode.PARTIAL)out.addAll(s.consumers);
        return out;
    }
    static boolean loaded(World w,Snapshot s){return mode(w,s)!=PersistentPower.Mode.VIRTUAL;}
    static boolean allLoaded(World w,Snapshot s){for(BlockPos p:s.positions)if(!w.isBlockLoaded(p))return false;return true;}
    private static boolean touches(Set<BlockPos> cells,BlockPos p){
        if(cells.contains(p))return true;for(net.minecraft.util.EnumFacing f:net.minecraft.util.EnumFacing.VALUES)if(cells.contains(p.offset(f)))return true;return false;
    }
    static void invalidateAt(World w,BlockPos p){
        if(w==null||w.isRemote)return;
        OfflineState o=get(w);
        for(Snapshot s:new ArrayList<>(o.snapshots.values()))if(touches(s.positions,p)){
            boolean virtual=o.active(TaskData.get(w),s)&&mode(w,s)!=PersistentPower.Mode.LIVE;
            o.snapshots.remove(s.anchor);o.markDirty();
            if(virtual)for(int g=0;g<s.groups.size();g++)if(touches(s.groups.get(g).cells,p)){
                Group group=s.groups.get(g);
                PowerGrid.cancel(w,group.cells);
                o.trip(w,TaskData.get(w),s,g,"Verified electrical topology changed");
            }
        }
    }
    @SubscribeEvent public static void placed(BlockEvent.PlaceEvent e){Network.invalidate(e.getWorld());invalidateAt(e.getWorld(),e.getPos());}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){Network.invalidate(e.getWorld());invalidateAt(e.getWorld(),e.getPos());}
    Set<PhysicalTopology.Pos> virtualCells(World w){
        Set<PhysicalTopology.Pos> out=new HashSet<>();
        for(Snapshot s:snapshots.values())if(validVirtual(w,s)){
            if(!ODC.requireEnergy){out.addAll(s.routing);continue;}
            Set<PhysicalTopology.Pos> route=new HashSet<>(s.routing);
            for(Group g:s.groups){
                PowerGrid.Domain domain=PowerGrid.virtualDomain(w,g);
                if(domain==null||domain.state!=GridProtection.ON||(PowerGrid.settling(w)&&!domain.funded()))
                    for(BlockPos p:g.cells)route.remove(PhysicalNetwork.pos(p));
            }
            out.addAll(route);
        }
        return out;
    }
    void capture(World w,UUID anchor,UUID owner,Set<BlockPos> positions,List<Tiles.Owned> devices,List<Tiles.Rack> racks,List<TaskEngine.Node> nodes,List<ClusterScheduler.Ref> refs,Set<Integer> excluded,int[] caps,int wan){if(anchor==null)return;
        Snapshot previous=snapshots.get(anchor);if(previous!=null&&!allLoaded(w,previous))return; // never overwrite a known larger domain with a fragment
        Snapshot s=new Snapshot();s.anchor=anchor;s.owner=owner;s.positions.addAll(positions);s.caps=caps.clone();s.budget=new int[racks.size()];s.gridByRack=new int[racks.size()];Arrays.fill(s.gridByRack,-1);
        Set<TileEntity> own=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());own.addAll(devices);
        Tiles.Computer root=null;for(Tiles.Owned t:devices)if(t instanceof Tiles.Computer){root=(Tiles.Computer)t;break;}
        if(root!=null){Set<PhysicalTopology.Pos> footprint=PhysicalNetwork.exclusiveFootprint(w,root.getPos());boolean certified=!footprint.isEmpty();for(PhysicalTopology.Pos p:footprint){BlockPos q=PhysicalNetwork.block(p);TileEntity tile=w.getTileEntity(q);if(tile instanceof Tiles.Station){if(!Objects.equals(((Tiles.Station)tile).owner,owner)){certified=false;break;}own.add(tile);}else if(tile instanceof Tiles.Owned&&Network.isLanDevice(tile)&&!own.contains(tile)){if(tile instanceof Tiles.Rack&&((Tiles.Rack)tile).role==-1)continue;certified=false;break;}}
            if(certified){s.routing.addAll(footprint);for(PhysicalTopology.Pos p:footprint)s.positions.add(PhysicalNetwork.block(p));}else{own.clear();own.addAll(devices);}}
        List<LiquidNetwork.Contour> certified=PersistentThermal.exclusive(w,racks);
        for(LiquidNetwork.Contour c:certified){own.addAll(c.chillers);s.positions.addAll(c.cells);}
        for(TileEntity t:own){s.consumers.add(t.getPos());s.positions.add(t.getPos());}
        Set<TileEntity> powered=Collections.newSetFromMap(new IdentityHashMap<TileEntity,Boolean>());List<PowerGrid.Domain> verified=new ArrayList<>();
        if(ODC.requireEnergy){
            Set<PowerGrid.Domain> grids=Collections.newSetFromMap(new IdentityHashMap<PowerGrid.Domain,Boolean>());
            for(TileEntity tile:own){
                PowerGrid.Domain grid=PowerGrid.domain(w,tile.getPos());
                if(grid!=null&&grid.state==GridProtection.ON&&grid.budget.capacityWatts>0)grids.add(grid);
            }
            for(PowerGrid.Domain grid:grids){
                // Never certify a shared grid with another live owner/LAN or an unknown fragment.
                for(TileEntity consumer:grid.consumers)if(!own.contains(consumer)){snapshots.remove(anchor);markDirty();return;}
                if(!grid.complete||!grid.funded()){snapshots.remove(anchor);markDirty();return;}
                Group g=new Group();g.cells.addAll(grid.positions);
                for(PowerGrid.Source source:grid.sources){
                    if(!(source instanceof Tiles.Battery)&&!(source instanceof Tiles.Transformer)){snapshots.remove(anchor);markDirty();return;}
                    SourceSpec spec=new SourceSpec();spec.pos=((TileEntity)source).getPos();spec.external=source instanceof Tiles.Transformer;spec.watts=source.ratedWatts();
                    if(spec.external)for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.VALUES){
                        BlockPos q=spec.pos.offset(side);if(!w.isBlockLoaded(q))continue;
                        TileEntity t=w.getTileEntity(q);if(t!=null&&!Network.isPowerDevice(t)&&!(t instanceof PowerGrid.Source))spec.inputs.add(q);
                    }
                    g.sources.add(spec);
                }
                if(g.sources.isEmpty()){snapshots.remove(anchor);markDirty();return;}
                verified.add(grid);s.groups.add(g);s.positions.addAll(grid.positions);powered.addAll(grid.consumers);
            }
            for(TileEntity tile:own)if(tile instanceof Tiles.Powered&&((Tiles.Powered)tile).online&&!powered.contains(tile)){snapshots.remove(anchor);markDirty();return;}
        }
        for(int i=0;i<nodes.size();i++)if(!excluded.contains(i)){ClusterScheduler.Ref f=refs.get(i);if(ODC.requireEnergy&&!powered.contains(f.rack))continue;s.nodes.add(nodes.get(i));s.budget[nodes.get(i).rack]+=f.rack.powerUsed[f.bay];}
        for(int g=0;g<verified.size();g++){
            PowerGrid.Domain grid=verified.get(g);long planned=0;
            for(int ri=0;ri<racks.size();ri++)if(racks.get(ri).energy.domain()==grid){s.gridByRack[ri]=g;planned+=s.budget[ri];}
            Group group=s.groups.get(g);
            group.fixed=Math.max(0,grid.budget.nominalWatts()-planned);
            group.fixedCategories=PowerMonitor.categories(grid);group.fixedCategories[PowerStats.SERVER]=Math.max(0,group.fixedCategories[PowerStats.SERVER]-planned);
            long categorized=PowerStats.total(group.fixedCategories);if(categorized<=group.fixed)group.fixedCategories[PowerStats.OTHER]+=group.fixed-categorized;else{Arrays.fill(group.fixedCategories,0);group.fixedCategories[PowerStats.OTHER]=group.fixed;}
            for(TileEntity consumer:grid.consumers)if(consumer instanceof Tiles.Computer&&((Tiles.Computer)consumer).operational())group.controller=true;
        }
        s.thermal.capture(s,previous,racks,refs,nodes,excluded,verified,certified);
        if(!ODC.requireEnergy)Arrays.fill(s.budget,Integer.MAX_VALUE);
        if(s.nodes.isEmpty()&&s.thermal.servers.isEmpty()){snapshots.remove(anchor);markDirty();return;}
        // Two saved snapshots must not own overlapping physical consumers or electrical cells.
        for(Snapshot old:new ArrayList<>(snapshots.values()))if(!old.anchor.equals(anchor)&&!Collections.disjoint(old.positions,s.positions)){
            if(!allLoaded(w,old))return;
            snapshots.remove(old.anchor);
        }
        snapshots.put(anchor,s);markDirty();
    }
    void checkInterruptedWindows(World w,Map<Set<BlockPos>,ExternalWindow> previous){
        if(!ODC.requireEnergy)return;
        TaskData data=TaskData.get(w);
        for(Snapshot s:snapshots.values())if(active(data,s)&&mode(w,s)==PersistentPower.Mode.PARTIAL){
            for(int g=0;g<s.groups.size();g++){
                ExternalWindow window=previous.get(s.groups.get(g).cells);
                if(window!=null&&!window.sufficient())trip(w,data,s,g,"External input insufficient during chunk transition");
            }
        }
    }
    void trip(World w,TaskData d,Snapshot s,int group,String reason){
        Group g=s.groups.get(group);int before=PowerState.get(w).state(g.cells);
        if(before==GridProtection.ON)PowerState.get(w).set(g.cells,GridProtection.TRIPPED);
        PowerGrid.cancel(w,g.cells);
        for(PersistentThermal.Server server:s.thermal.servers)if(s.gridByRack[server.rack]==group&&(server.node>=0||server.idleWatts>0)){
            UUID id=server.id;
            if(!faults.containsKey(id)){
                faults.put(id,ComputeEngine.NO_POWER);d.serverStopped(id,false);
                d.record(s.owner,s.anchor,id,Level.CRITICAL,Category.POWER,Target.SERVER,"grid_overload",reason,-1,0);
            }
        }
        markDirty();
    }
    void plan(World w,TaskData d,List<Runnable> settlement){
        BandwidthEngine.Result flow=PhysicalNetwork.bandwidth(w);
        Set<UUID> seen=new HashSet<>();
        List<Snapshot> order=new ArrayList<>(snapshots.values());order.sort(Comparator.comparing(x->x.anchor.toString()));
        for(Snapshot s:order){
            if(!validVirtual(w,s)||!seen.add(s.anchor))continue;
            List<TaskData.Job> jobs=new ArrayList<>();
            for(TaskData.Job j:d.jobs)if(s.anchor.equals(j.anchor)&&s.owner.equals(j.owner))jobs.add(j);
            Set<Integer> excluded=new HashSet<>(),blocked=new HashSet<>();
            int[] caps=s.caps.clone();
            for(int i=0;i<s.nodes.size();i++)if(faults.containsKey(s.nodes.get(i).id))excluded.add(i);
            for(TaskData.Job j:jobs)if(j.enabled){
                boolean ok=NetworkAdmission.amounts(j.bindings).isEmpty()||!s.routing.isEmpty()&&NetworkAdmission.served(flow,j.terms.getUniqueId("Key"),j.bindings);
                if(!ok)blocked.add(j.id);d.routeState(j,ok);
            }
            PowerGrid.Domain[] domains=new PowerGrid.Domain[s.groups.size()];
            if(ODC.requireEnergy)for(int g=0;g<domains.length;g++){
                domains[g]=PowerGrid.virtualDomain(w,s.groups.get(g));
                if(domains[g]==null||domains[g].state!=GridProtection.ON)
                    trip(w,d,s,g,"Virtual electrical grid unavailable");
            }
            TaskEngine.Plan p;
            while(true){
                boolean controller=!ODC.requireEnergy;
                if(ODC.requireEnergy)for(int g=0;g<domains.length;g++)
                    if(domains[g]!=null&&domains[g].state==GridProtection.ON&&s.groups.get(g).controller)controller=true;
                for(int ri=0;ri<caps.length;ri++)if(ODC.requireEnergy){
                    int g=s.gridByRack[ri];if(g<0||g>=domains.length||domains[g]==null||domains[g].state!=GridProtection.ON)caps[ri]=0;
                }
                for(int i=0;i<s.nodes.size();i++)if(faults.containsKey(s.nodes.get(i).id))excluded.add(i);
                Set<Integer> unavailable=new HashSet<>(blocked);
                if(!controller)for(TaskData.Job j:jobs)unavailable.add(j.id);
                p=d.plan(s.anchor,s.nodes,jobs,caps,controller?Integer.MAX_VALUE:0,excluded,unavailable);
                boolean retry=false;
                for(int i:p.overload)if(excluded.add(i)){
                    UUID id=s.nodes.get(i).id;faults.put(id,ComputeEngine.PSU_OVERLOAD);d.serverStopped(id,false);
                    d.record(s.owner,s.anchor,id,Level.CRITICAL,Category.POWER,Target.SERVER,"psu_overload","PSU overload (virtual simulation)",-1,0);retry=true;
                }
                if(retry)continue;
                if(ODC.requireEnergy){
                    long[] demand=new long[domains.length];for(int g=0;g<demand.length;g++)demand[g]=s.groups.get(g).fixed;
                    s.thermal.addIdleDemand(s,this,demand);
                    for(int i=0;i<s.nodes.size();i++)if(!excluded.contains(i)){
                        int g=s.gridByRack[s.nodes.get(i).rack];
                        if(g>=0)demand[g]=PersistentPower.demand(demand[g],p.power[i]);
                    }
                    for(int g=0;g<domains.length;g++){
                        PowerGrid.Domain domain=domains[g];
                        if(domain==null||domain.state!=GridProtection.ON)continue;
                        if(!domain.budget.canUse(demand[g])){
                            domain.shutdown(GridProtection.TRIPPED);
                            trip(w,d,s,g,"Insufficient power (virtual simulation)");retry=true;
                        }
                    }
                    if(retry)continue;
                    for(int g=0;g<domains.length;g++)if(domains[g]!=null&&domains[g].state==GridProtection.ON)
                        if(!domains[g].budget.use(demand[g]))throw new IllegalStateException("Virtual power reservation changed");
                }
                break;
            }
            final TaskEngine.Plan planned=p;
            settlement.add(()->{
                if(snapshots.get(s.anchor)!=s||mode(w,s)!=PersistentPower.Mode.VIRTUAL)return;
                s.thermal.step(w,d,this,s,planned,excluded,domains,false,false);
                if(!validVirtual(w,s))return;
                boolean controller=!ODC.requireEnergy;
                if(ODC.requireEnergy)for(int g=0;g<domains.length;g++)if(domains[g]!=null&&domains[g].funded()&&s.groups.get(g).controller)controller=true;
                BandwidthEngine.Result current=PhysicalNetwork.bandwidth(w);
                for(TaskData.Job j:jobs){
                    TaskEngine.Result result=planned.tasks.get(j.id);if(result==null||j.served)continue;
                    boolean funded=controller&&j.enabled&&NetworkAdmission.served(current,j.terms.getUniqueId("Key"),j.bindings);
                    for(TaskEngine.Slice slice:result.slices){
                        int g=s.gridByRack[s.nodes.get(slice.node).rack];
                        if(excluded.contains(slice.node)||faults.containsKey(s.nodes.get(slice.node).id)||ODC.requireEnergy&&(g<0||domains[g]==null||!domains[g].funded()))funded=false;
                    }
                    if(j.enabled)d.resourceState(j,!funded||result.work<=0||!result.full);
                    if(!funded)continue;
                    j.work=result.work;j.usedCPU=result.cpu;j.usedGPU=result.gpus;j.usedRAM=result.ram;j.usedDisk=result.disk;j.usedNet=result.net;j.full=result.full;
                    d.account(j,result);
                }
                if(w.getTotalWorldTime()%400==0)markDirty();
            });
        }
    }
    void passiveThermal(World w,TaskData d){
        for(Snapshot s:snapshots.values())if(mode(w,s)!=PersistentPower.Mode.LIVE)
            s.thermal.step(w,d,this,s,null,Collections.emptySet(),new PowerGrid.Domain[s.groups.size()],true,true);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){
        NBTTagList ss=new NBTTagList();
        for(Snapshot s:snapshots.values()){
            NBTTagCompound a=new NBTTagCompound();a.setUniqueId("Anchor",s.anchor);a.setUniqueId("Owner",s.owner);
            a.setIntArray("Caps",s.caps);a.setIntArray("Budget",s.budget);a.setIntArray("GridByRack",s.gridByRack);
            NBTTagList groups=new NBTTagList();
            for(Group g:s.groups){
                NBTTagCompound z=new NBTTagCompound();z.setBoolean("Controller",g.controller);z.setLong("Fixed",g.fixed);for(int c=0;c<PowerStats.SIZE;c++)z.setLong("FixedC"+c,g.fixedCategories[c]);z.setTag("Cells",writePositions(g.cells));
                NBTTagList sources=new NBTTagList();for(SourceSpec spec:g.sources){
                    NBTTagCompound q=new NBTTagCompound();q.setLong("Pos",spec.pos.toLong());q.setBoolean("External",spec.external);q.setLong("Watts",spec.watts);q.setTag("Inputs",writePositions(spec.inputs));sources.appendTag(q);
                }
                z.setTag("Sources",sources);groups.appendTag(z);
            }
            a.setTag("PowerGroups",groups);a.setTag("Positions",writePositions(s.positions));a.setTag("Consumers",writePositions(s.consumers));
            Set<BlockPos> route=new HashSet<>();for(PhysicalTopology.Pos pos:s.routing)route.add(PhysicalNetwork.block(pos));a.setTag("Routing",writePositions(route));
            NBTTagList nn=new NBTTagList();for(TaskEngine.Node node:s.nodes){
                NBTTagCompound q=new NBTTagCompound();q.setUniqueId("Id",node.id);if(node.pool!=null)q.setUniqueId("Pool",node.pool);q.setInteger("Rack",node.rack);q.setInteger("Net",node.net);q.setIntArray("Parts",node.parts);if(node.chassis!=null)q.setString("Chassis",node.chassis.id);nn.appendTag(q);
            }
            a.setTag("Nodes",nn);a.setTag("Thermal",s.thermal.write());ss.appendTag(a);
        }
        n.setInteger("PowerSchema",6);n.setTag("Snapshots",ss);
        NBTTagList fs=new NBTTagList();for(Map.Entry<UUID,Integer> f:faults.entrySet()){
            NBTTagCompound a=new NBTTagCompound();a.setUniqueId("Id",f.getKey());a.setInteger("Reason",f.getValue());fs.appendTag(a);
        }
        n.setTag("Faults",fs);NBTTagList pending=new NBTTagList();for(PersistentThermal.Server server:pendingThermal.values())pending.appendTag(server.write());n.setTag("PendingThermal",pending);return n;
    }
    static NBTTagList writePositions(Collection<BlockPos> positions){
        NBTTagList list=new NBTTagList();for(BlockPos p:positions){NBTTagCompound a=new NBTTagCompound();a.setLong("P",p.toLong());list.appendTag(a);}return list;
    }
    static Set<BlockPos> readPositions(NBTTagList list){
        Set<BlockPos> result=new HashSet<>();for(int i=0;i<list.tagCount();i++)result.add(BlockPos.fromLong(list.getCompoundTagAt(i).getLong("P")));return result;
    }
    @Override public void readFromNBT(NBTTagCompound n){
        snapshots.clear();NBTTagList ss=n.getInteger("PowerSchema")==6?n.getTagList("Snapshots",10):new NBTTagList();
        for(int i=0;i<ss.tagCount();i++){
            NBTTagCompound a=ss.getCompoundTagAt(i);if(!a.hasUniqueId("Anchor")||!a.hasUniqueId("Owner"))continue;
            Snapshot s=new Snapshot();s.anchor=a.getUniqueId("Anchor");s.owner=a.getUniqueId("Owner");
            s.caps=a.getIntArray("Caps");s.budget=a.getIntArray("Budget");s.gridByRack=a.getIntArray("GridByRack");
            if(s.caps.length!=s.budget.length||s.caps.length!=s.gridByRack.length)continue;
            s.positions=readPositions(a.getTagList("Positions",10));s.consumers=readPositions(a.getTagList("Consumers",10));
            if(s.consumers.isEmpty()||!s.positions.containsAll(s.consumers))continue;
            boolean valid=true;Set<BlockPos> claimed=new HashSet<>();
            NBTTagList groups=a.getTagList("PowerGroups",10);
            for(int j=0;j<groups.tagCount();j++){
                NBTTagCompound z=groups.getCompoundTagAt(j);Group g=new Group();g.controller=z.getBoolean("Controller");g.fixed=z.getLong("Fixed");for(int c=0;c<PowerStats.SIZE;c++)g.fixedCategories[c]=Math.max(0,z.getLong("FixedC"+c));if(PowerStats.total(g.fixedCategories)!=g.fixed){Arrays.fill(g.fixedCategories,0);g.fixedCategories[PowerStats.OTHER]=Math.max(0,g.fixed);}g.cells=readPositions(z.getTagList("Cells",10));
                if(g.fixed<0||g.cells.isEmpty()||!s.positions.containsAll(g.cells)||!Collections.disjoint(claimed,g.cells)){valid=false;break;}claimed.addAll(g.cells);
                NBTTagList sources=z.getTagList("Sources",10);Set<BlockPos> unique=new HashSet<>();
                for(int k=0;k<sources.tagCount();k++){
                    NBTTagCompound q=sources.getCompoundTagAt(k);SourceSpec spec=new SourceSpec();spec.pos=BlockPos.fromLong(q.getLong("Pos"));spec.external=q.getBoolean("External");spec.watts=q.getLong("Watts");spec.inputs=readPositions(q.getTagList("Inputs",10));
                    if(!g.cells.contains(spec.pos)||!unique.add(spec.pos)||spec.watts<=0||spec.watts>TransformerCore.MAX_WATTS){valid=false;break;}
                    spec.inputs.removeIf(p->p.distanceSq(spec.pos)>1);g.sources.add(spec);
                }
                if(!valid||g.sources.isEmpty()){valid=false;break;}s.groups.add(g);
            }
            if(!valid)continue;
            for(BlockPos p:readPositions(a.getTagList("Routing",10)))if(s.positions.contains(p))s.routing.add(PhysicalNetwork.pos(p));
            NBTTagList nn=a.getTagList("Nodes",10);Set<UUID> ids=new HashSet<>();
            for(int j=0;j<nn.tagCount();j++){
                NBTTagCompound q=nn.getCompoundTagAt(j);int rack=q.getInteger("Rack");int[] parts=q.getIntArray("Parts");ChassisSpec chassis=ChassisSpec.byId(q.getString("Chassis"));
                if(parts.length!=ServerSpec.COUNT||chassis==null||ServerSpec.state(chassis,parts)!=ServerSpec.READY||!q.hasUniqueId("Id")||rack<0||rack>=s.caps.length||!ids.add(q.getUniqueId("Id"))){valid=false;break;}
                if(ODC.requireEnergy&&(s.gridByRack[rack]<0||s.gridByRack[rack]>=s.groups.size())){valid=false;break;}
                s.nodes.add(new TaskEngine.Node(q.getUniqueId("Id"),rack,q.getInteger("Net"),parts,q.hasUniqueId("Pool")?q.getUniqueId("Pool"):PoolResources.id(s.owner,PoolResources.DEFAULT),chassis));
            }
            if(valid){s.thermal=PersistentThermal.read(a.getCompoundTag("Thermal"),s);if(s.thermal!=null&&!s.thermal.servers.isEmpty()&&snapshots.values().stream().noneMatch(old->!Collections.disjoint(old.positions,s.positions)))snapshots.put(s.anchor,s);}
        }
        pendingThermal.clear();NBTTagList pt=n.getTagList("PendingThermal",10);for(int i=0;i<pt.tagCount();i++){PersistentThermal.Server server=PersistentThermal.Server.read(pt.getCompoundTagAt(i));if(server!=null)pendingThermal.put(server.id,server);}
        faults.clear();NBTTagList fs=n.getTagList("Faults",10);for(int i=0;i<fs.tagCount();i++){NBTTagCompound a=fs.getCompoundTagAt(i);if(a.hasUniqueId("Id"))faults.put(a.getUniqueId("Id"),a.getInteger("Reason"));}
    }
}
