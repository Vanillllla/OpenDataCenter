package odc;
import java.util.*;
import java.util.function.Predicate;

/** Adapter from the persisted physical index to the globally unique routing graph.
 * A partially unloaded cable run is not considered a verified path. */
final class BandwidthTopology {
    final List<BandwidthEngine.Node> nodes=new ArrayList<>();
    final List<BandwidthEngine.Edge> edges=new ArrayList<>();
    private final Map<UUID,PhysicalTopology.Device> devices=new TreeMap<>();
    private final Map<PhysicalTopology.Pos,PhysicalTopology.Pos> verifiedLan=new HashMap<>();
    private final Set<UUID> ambiguous=new HashSet<>();
    BandwidthTopology(PhysicalTopology topology,Collection<LogicalLinks.Link> uplinks,Predicate<PhysicalTopology.Pos> loaded){
        // Snapshot availability once. Do not consult or load chunks during routing.
        Set<PhysicalTopology.Pos> live=new HashSet<>();for(PhysicalTopology.Pos p:topology.cells().keySet())if(loaded.test(p))live.add(p);
        List<PhysicalTopology.Pos> ordered=new ArrayList<>(topology.cells().keySet());Collections.sort(ordered);
        for(PhysicalTopology.Pos p:ordered){PhysicalTopology.Cell cell=topology.cell(p);for(PhysicalTopology.Device d:cell.devices){if(devices.putIfAbsent(d.id,d)!=null)ambiguous.add(d.id);}
            if(!cell.kind.lan()||!live.contains(p)||verifiedLan.containsKey(p))continue;
            Set<PhysicalTopology.Pos> part=TopologySearch.component(p,PhysicalTopology.Pos::adjacent,q->live.contains(q)&&topology.cell(q).kind.lan(),Math.max(1,topology.cells().size()));
            if(part!=null)for(PhysicalTopology.Pos q:part)verifiedLan.put(q,p);
        }
        ambiguous.forEach(devices::remove);
        for(PhysicalTopology.Device d:devices.values())nodes.add(new BandwidthEngine.Node(d.id,d.type,d.capacityMbps,live.contains(d.pos)&&d.powered));
        Set<UUID> linkIds=new HashSet<>();
        Map<String,Integer> portOwners=new HashMap<>();for(PhysicalTopology.Device d:devices.values())if(d.type==PhysicalTopology.Type.SERVER&&d.parent!=null)portOwners.merge(d.parent+":"+d.port,1,Integer::sum);
        for(PhysicalTopology.Device d:devices.values()){
            if(d.type==PhysicalTopology.Type.TERMINAL){for(PhysicalTopology.Peer p:topology.opticalPeers(d.pos,live::contains)){PhysicalTopology.Device a=devices.get(p.device.id);if(a==null)continue;UUID id=LogicalLinks.automaticId(LogicalLinks.Kind.FIBER,a.id,d.id);int c=Math.min(a.capacityMbps,d.capacityMbps);if(linkIds.add(id))edges.add(new BandwidthEngine.Edge(id,a.id,d.id,LogicalLinks.Kind.FIBER,c,c,p.verified));}}
            if(d.type==PhysicalTopology.Type.SERVER&&d.parent!=null){PhysicalTopology.Device sw=devices.get(d.parent);if(sw==null||sw.type!=PhysicalTopology.Type.SWITCH||!sw.pos.equals(d.pos))continue;
                int tier=Math.max(1,d.patchTier),c=LogicalLinks.speed(tier);UUID id=d.patchId==null?LogicalLinks.automaticId(LogicalLinks.Kind.PATCH,sw.id,d.id):d.patchId;
                // The registered model determines the physical port speed and count.
                // A cable upgrade does not upgrade the port.
                int switchTier=SwitchLimits.tier(sw.model);int port=SwitchLimits.portMbps(switchTier);if(d.port<0||d.port>=SwitchLimits.ports(switchTier)||portOwners.getOrDefault(d.parent+":"+d.port,0)!=1)port=0;if(linkIds.add(id))edges.add(new BandwidthEngine.Edge(id,sw.id,d.id,LogicalLinks.Kind.PATCH,c,port,live.contains(d.pos)));}
        }
        for(LogicalLinks.Link l:uplinks){PhysicalTopology.Device a=devices.get(l.from),b=devices.get(l.to);if(a==null||b==null||a.type!=PhysicalTopology.Type.TERMINAL||b.type!=PhysicalTopology.Type.SWITCH)continue;
            PhysicalTopology.Pos ap=verifiedLan.get(a.pos),bp=verifiedLan.get(b.pos);if(linkIds.add(l.id))edges.add(new BandwidthEngine.Edge(l.id,a.id,b.id,LogicalLinks.Kind.UPLINK,l.capacityMbps,l.capacityMbps,ap!=null&&ap.equals(bp)));}
    }
    BandwidthEngine engine(){return new BandwidthEngine(nodes,edges);}
}
