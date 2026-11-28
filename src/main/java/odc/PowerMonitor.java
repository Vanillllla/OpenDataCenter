package odc;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Server-only read model; bounded histories, one sample per electrical component, not per GUI. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class PowerMonitor {
    private static final int MAX_HISTORY=512;
    private static final Map<World,LinkedHashMap<UUID,PowerStats.History>> worlds=new IdentityHashMap<>();
    private PowerMonitor(){}
    private static LinkedHashMap<UUID,PowerStats.History> histories(World w){return worlds.computeIfAbsent(w,k->new LinkedHashMap<>(16,.75f,true));}
    static UUID key(World w,PowerGrid.Domain d){return PowerStats.key(w.provider.getDimension(),PowerState.keys(d.positions));}
    static long[] categories(PowerGrid.Domain d){
        long[] c=new long[PowerStats.SIZE];
        if(d.virtualGroup!=null){
            System.arraycopy(d.virtualGroup.fixedCategories,0,c,0,c.length);
            long used=d.budget.nominalWatts();
            c[PowerStats.SERVER]+=Math.max(0,used-d.virtualGroup.fixed);
        }else for(TileEntity tile:d.consumers){
            if(tile instanceof Tiles.Rack){
                Tiles.Rack rack=(Tiles.Rack)tile;
                for(int b=0;b<rack.capacity();b++)if((rack.poweredMask&(1<<b))!=0&&rack.isServer(b))c[PowerStats.SERVER]+=rack.powerUsed[b];
                if(rack.switchOnline)c[PowerStats.SWITCH]+=Hardware.switchPower(rack.switchTier());
            }else if(tile instanceof Tiles.Powered&&((Tiles.Powered)tile).operational()){
                int cat=tile instanceof Tiles.Computer?PowerStats.DATAOS:tile instanceof Tiles.NetworkTerminal?PowerStats.TERMINAL:tile instanceof Tiles.Station?PowerStats.ANTENNA:PowerStats.OTHER;
                c[cat]+=((Tiles.Powered)tile).idlePower();
            }
        }
        return c;
    }
    static void record(World w){
        long tick=w.getTotalWorldTime();LinkedHashMap<UUID,PowerStats.History> histories=histories(w);
        for(PowerGrid.Domain d:PowerGrid.topology(w).domains){
            boolean known=d.complete||d.virtualGroup!=null;
            PowerStats.Sample sample=new PowerStats.Sample(tick,d.fundedWatts(),d.ratedWatts(),PowerEngine.scaleCategories(categories(d),d.budget.factor),d.state,known,!ODC.requireEnergy,d.funded());
            histories.computeIfAbsent(key(w,d),k->new PowerStats.History()).record(sample);
        }
        histories.entrySet().removeIf(e->e.getValue().latest!=null&&e.getValue().latest.tick<=tick-PowerStats.WINDOW);
        while(histories.size()>MAX_HISTORY)histories.remove(histories.keySet().iterator().next());
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload e){worlds.remove(e.getWorld());}

    /** Scope follows the existing owner LAN plus its verified optical links.
     * Totals describe the entire electrical domain, even when it feeds another LAN. */
    static List<PowerGrid.Domain> visible(Tiles.Computer root){
        World w=root.getWorld();Set<PowerGrid.Domain> found=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<BlockPos> terminals=new HashSet<>();
        add(w,root.getPos(),found);
        Network.Domain lan=Network.scan(w,root.getPos());
        if(lan!=null)for(Tiles.Owned t:lan.devices)if(Objects.equals(root.owner,t.owner)){
            add(w,t.getPos(),found);if(t instanceof Tiles.NetworkTerminal)terminals.add(t.getPos());
        }
        PhysicalTopology.View view=PhysicalNetwork.view(w,root.getPos());
        for(PhysicalTopology.Link link:view.links)if(link.verified&&link.type==PhysicalTopology.LinkType.FIBER){
            PhysicalTopology.State a=view.devices.get(link.from),b=view.devices.get(link.to);
            if(a==null||b==null||!a.loaded||!b.loaded)continue;
            BlockPos pa=PhysicalNetwork.block(a.device.pos),pb=PhysicalNetwork.block(b.device.pos);
            BlockPos station=terminals.contains(pa)?pb:terminals.contains(pb)?pa:null;
            if(station!=null&&w.isBlockLoaded(station)){
                TileEntity t=w.getTileEntity(station);
                if(t instanceof Tiles.Station&&Objects.equals(((Tiles.Station)t).owner,root.owner))add(w,station,found);
            }
        }
        List<PowerGrid.Domain> result=new ArrayList<>(found);
        result.sort(Comparator.comparing(d->key(w,d).toString()));return result;
    }
    private static void add(World w,BlockPos p,Set<PowerGrid.Domain> found){PowerGrid.Domain d=PowerGrid.domain(w,p);if(d!=null)found.add(d);}
    static boolean select(PanelMenu menu,UUID id,int direction){
        if(direction!=-1&&direction!=1)return false;
        Tiles.Computer root=(Tiles.Computer)menu.tile;World w=root.getWorld();List<PowerGrid.Domain> grids=visible(root);
        List<UUID> keys=new ArrayList<>();for(PowerGrid.Domain d:grids)keys.add(key(w,d));
        int index=keys.indexOf(id);if(index<0||!id.equals(menu.powerGrid))return false;
        menu.powerGrid=keys.get(PowerStats.step(index,direction,keys.size()));menu.scrollRow=0;return true;
    }
    static NBTTagCompound snapshot(PanelMenu menu,NBTTagCompound out){
        Tiles.Computer root=(Tiles.Computer)menu.tile;World w=root.getWorld();
        List<PowerGrid.Domain> grids=visible(root);List<UUID> keys=new ArrayList<>();for(PowerGrid.Domain d:grids)keys.add(key(w,d));
        out.setInteger("GridCount",keys.size());
        if(!keys.contains(menu.powerGrid)){PowerGrid.Domain own=PowerGrid.domain(w,root.getPos());if(own!=null)menu.powerGrid=key(w,own);menu.scrollRow=0;}
        int index=PowerStats.selected(keys,menu.powerGrid);
        if(index<0){out.setInteger("Count",0);return out;}
        menu.powerGrid=keys.get(index);out.setUniqueId("GridKey",menu.powerGrid);out.setInteger("GridIndex",index);
        PowerGrid.Domain d=grids.get(index);long now=w.getTotalWorldTime();
        BlockPos first=d.positions.stream().min(Comparator.comparingLong(BlockPos::toLong)).orElse(root.getPos());
        out.setString("GridPos",coords(first));out.setInteger("State",d.state);out.setBoolean("Bypass",!ODC.requireEnergy);
        out.setBoolean("Complete",d.complete||d.virtualGroup!=null);out.setLong("Rated",d.ratedWatts());
        PowerStats.History history=histories(w).get(menu.powerGrid);
        PowerStats.Sample last=history==null?null:history.latest;
        boolean fresh=last!=null&&now-last.tick>=0&&now-last.tick<40&&last.state==d.state&&last.known==(d.complete||d.virtualGroup!=null);
        PowerStatsWire.write(out,history,now-now%20,fresh);
        List<NBTTagCompound> sources=new ArrayList<>();
        for(PowerGrid.Source source:d.sources){
            NBTTagCompound row=new NBTTagCompound();row.setInteger("State",d.state);row.setLong("Rated",source.ratedWatts());
            if(source instanceof Tiles.Transformer){
                Tiles.Transformer t=(Tiles.Transformer)source;row.setInteger("Tier",t.tier());row.setString("Pos",coords(t.getPos()));row.setBoolean("External",true);
                row.setLong("Received",fresh&&d.state==GridProtection.ON?t.lastOutput:0);row.setLong("HE",fresh?t.lastHE:0);row.setLong("FE",fresh?t.lastFE:0);
            }else if(source instanceof TileEntity){row.setString("Pos",coords(((TileEntity)source).getPos()));row.setLong("Received",d.state==GridProtection.ON?source.outputWatts():0);}
            else if(source instanceof PowerGrid.SavedCreative){row.setString("Pos",coords(((PowerGrid.SavedCreative)source).pos));row.setBoolean("Virtual",true);row.setLong("Received",d.state==GridProtection.ON?source.outputWatts():0);}
            sources.add(row);
        }
        sources.sort(Comparator.comparing(r->r.getString("Pos")));
        int count=sources.size(),start=Math.min(Math.max(0,menu.scrollRow),Math.max(0,count-1));out.setInteger("Count",count);out.setInteger("Start",start);
        NBTTagList rows=new NBTTagList();for(int i=start;i<Math.min(count,start+DataOSView.WINDOW);i++)rows.appendTag(sources.get(i));out.setTag("Rows",rows);return out;
    }
    private static String coords(BlockPos p){return p.getX()+", "+p.getY()+", "+p.getZ();}
}
