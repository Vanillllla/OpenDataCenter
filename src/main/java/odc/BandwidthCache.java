package odc;
import java.util.*;
import java.util.function.Predicate;

/** Reuse graph and last immutable result until physical/availability/link revisions
 * or reservation inputs actually change. All callers are on the world server thread. */
final class BandwidthCache {
    private long graphRev=Long.MIN_VALUE,linkRev=Long.MIN_VALUE,liveRev=Long.MIN_VALUE;
    private BandwidthEngine engine;
    private List<BandwidthEngine.Request> last;
    private BandwidthEngine.Result result;
    BandwidthEngine.Result get(PhysicalTopology topology,LogicalLinks links,long availabilityRevision,Predicate<PhysicalTopology.Pos> loaded,Collection<BandwidthEngine.Request> requests){
        if(engine==null||graphRev!=topology.routingRevision()||linkRev!=links.revision()||liveRev!=availabilityRevision){
            engine=new BandwidthTopology(topology,links.all(),loaded).engine();graphRev=topology.routingRevision();linkRev=links.revision();liveRev=availabilityRevision;last=null;
        }
        List<BandwidthEngine.Request> ordered=new ArrayList<>(requests);ordered.sort(Comparator.comparing(r->r.id));
        if(!ordered.equals(last)){result=engine.solve(ordered);last=ordered;}
        return result;
    }
    BandwidthEngine engine(){return engine;}
    void clear(){engine=null;last=null;result=null;}
}
