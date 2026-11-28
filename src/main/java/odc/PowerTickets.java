package odc;

import java.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Source chunks only. One ticket per distinct chunk prevents Forge's ticket-depth
 * eviction from silently dropping an unrelated transformer. Reconcile every cycle. */
@Mod.EventBusSubscriber(modid=ODC.ID)
public final class PowerTickets {
    private static final Map<World,Map<ChunkPos,ForgeChunkManager.Ticket>> active=new IdentityHashMap<>();
    private PowerTickets(){}
    static void init(){
        // Old tickets are hints, never authority: recreate from validated saved snapshots.
        ForgeChunkManager.setForcedChunkLoadingCallback(ODC.instance,(tickets,world)->{
            for(ForgeChunkManager.Ticket ticket:tickets)ForgeChunkManager.releaseTicket(ticket);
        });
    }
    static void sync(World w,OfflineState state,TaskData data){
        if(!(w instanceof WorldServer))return;
        Set<ChunkPos> wanted=new LinkedHashSet<>();
        if(ODC.requireEnergy&&ODC.persistentSourceChunks>0){
            List<OfflineState.Snapshot> order=new ArrayList<>(state.snapshots.values());
            order.sort(Comparator.comparing(s->s.anchor.toString()));
            // First retain transformer chunks, then adjacent known external connection chunks.
            for(OfflineState.Snapshot s:order)if(state.active(data,s))
                for(OfflineState.Group g:s.groups)if(PowerState.get(w).state(g.cells)==GridProtection.ON)
                    for(OfflineState.SourceSpec source:g.sources)if(source.external)wanted.add(new ChunkPos(source.pos));
            for(OfflineState.Snapshot s:order)if(state.active(data,s))
                for(OfflineState.Group g:s.groups)if(PowerState.get(w).state(g.cells)==GridProtection.ON)
                    for(OfflineState.SourceSpec source:g.sources)if(source.external)
                        for(BlockPos pos:source.inputs)wanted.add(new ChunkPos(pos));
        }
        Set<ChunkPos> limited=new LinkedHashSet<>();
        for(ChunkPos p:wanted){if(limited.size()>=ODC.persistentSourceChunks)break;limited.add(p);}
        Map<ChunkPos,ForgeChunkManager.Ticket> held=active.computeIfAbsent(w,k->new HashMap<>());
        Iterator<Map.Entry<ChunkPos,ForgeChunkManager.Ticket>> it=held.entrySet().iterator();
        while(it.hasNext()){Map.Entry<ChunkPos,ForgeChunkManager.Ticket> e=it.next();
            if(!limited.contains(e.getKey())){ForgeChunkManager.releaseTicket(e.getValue());it.remove();}
        }
        for(ChunkPos p:limited)if(!held.containsKey(p)){
            ForgeChunkManager.Ticket ticket=ForgeChunkManager.requestTicket(ODC.instance,w,ForgeChunkManager.Type.NORMAL);
            if(ticket==null)continue;
            ForgeChunkManager.forceChunk(ticket,p);
            if(!ticket.getChunkList().contains(p)){ForgeChunkManager.releaseTicket(ticket);continue;}
            held.put(p,ticket);
            // The coordinates were captured from existing loaded source/connection tiles.
            if(!w.isBlockLoaded(new BlockPos(p.x<<4,64,p.z<<4)))((WorldServer)w).getChunkProvider().provideChunk(p.x,p.z);
        }
        if(held.isEmpty())active.remove(w);
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload e){
        // Forge owns saving/removing world tickets; no references to the world survive.
        active.remove(e.getWorld());
    }
}
