package odc;

import java.util.*;
import java.util.function.Predicate;

/** Server-side physical topology, independent of chunks and of the traffic/reservation engine.
 * Positions are dimension-local. Only explicit block changes remove persisted cells. */
public final class PhysicalTopology {
    public enum Kind { DATA, FIBER, RACK, CONTROLLER, TERMINAL, ANTENNA;
        boolean lan(){return this==DATA||this==RACK||this==CONTROLLER||this==TERMINAL;}
    }
    public enum Type { RADIO, SATELLITE, TOWER, TERMINAL, SWITCH, SERVER }
    public enum LinkType { FIBER, PATCH }
    public static final class Pos implements Comparable<Pos> {
        public final int x,y,z;
        public Pos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        List<Pos> adjacent(){return Arrays.asList(new Pos(x-1,y,z),new Pos(x+1,y,z),new Pos(x,y-1,z),new Pos(x,y+1,z),new Pos(x,y,z-1),new Pos(x,y,z+1));}
        @Override public boolean equals(Object o){if(!(o instanceof Pos))return false;Pos p=(Pos)o;return x==p.x&&y==p.y&&z==p.z;}
        @Override public int hashCode(){return (x*31+y)*31+z;}
        @Override public int compareTo(Pos p){int c=Integer.compare(x,p.x);if(c==0)c=Integer.compare(y,p.y);return c==0?Integer.compare(z,p.z):c;}
        @Override public String toString(){return x+","+y+","+z;}
    }
    public static final class Device {
        public final UUID id,parent,patchId; // server parent = physical switch; null otherwise
        public final Type type; public final String name,model; public final Pos pos;
        public final int dimension,capacityMbps,usedMbps,slot,port,patchTier; // -1 use = unavailable / not measured yet
        public final boolean powered;
        public Device(UUID id,Type type,String name,String model,Pos pos,int dimension,int capacityMbps,int usedMbps,boolean powered,UUID parent,int slot,int port){
            this(id,type,name,model,pos,dimension,capacityMbps,usedMbps,powered,parent,slot,port,null,0);
        }
        public Device(UUID id,Type type,String name,String model,Pos pos,int dimension,int capacityMbps,int usedMbps,boolean powered,UUID parent,int slot,int port,UUID patchId,int patchTier){
            this.patchId=patchId;this.patchTier=patchTier;this.id=Objects.requireNonNull(id);this.type=Objects.requireNonNull(type);this.name=Objects.requireNonNull(name);this.model=Objects.requireNonNull(model);this.pos=Objects.requireNonNull(pos);this.dimension=dimension;this.capacityMbps=Math.max(0,capacityMbps);this.usedMbps=Math.max(-1,usedMbps);this.powered=powered;this.parent=parent;this.slot=slot;this.port=port;
        }
        @Override public boolean equals(Object obj){if(!(obj instanceof Device))return false;Device d=(Device)obj;return id.equals(d.id)&&type==d.type&&name.equals(d.name)&&model.equals(d.model)&&pos.equals(d.pos)&&dimension==d.dimension&&capacityMbps==d.capacityMbps&&usedMbps==d.usedMbps&&powered==d.powered&&Objects.equals(parent,d.parent)&&slot==d.slot&&port==d.port&&Objects.equals(patchId,d.patchId)&&patchTier==d.patchTier;}
        @Override public int hashCode(){return id.hashCode();}
    }
    public static final class Cell {
        public final Kind kind; public final List<Device> devices;
        public Cell(Kind kind,List<Device> devices){this.kind=Objects.requireNonNull(kind);this.devices=Collections.unmodifiableList(new ArrayList<>(devices));}
        @Override public boolean equals(Object o){return o instanceof Cell&&kind==((Cell)o).kind&&devices.equals(((Cell)o).devices);}
        @Override public int hashCode(){return 31*kind.hashCode()+devices.hashCode();}
    }
    public static final class Domain {
        public final Pos anchor; public final Set<Pos> positions;
        Domain(Set<Pos> positions){this.positions=Collections.unmodifiableSet(positions);this.anchor=Collections.min(positions);}
    }
    public static final class Link {
        public final UUID from,to; public final LinkType type; public final boolean verified;
        Link(UUID from,UUID to,LinkType type,boolean verified){this.from=from;this.to=to;this.type=type;this.verified=verified;}
    }
    public static final class State {
        public final Device device; public final boolean loaded,available; public final int usedMbps;
        State(Device d,boolean loaded){device=d;this.loaded=loaded;available=loaded&&d.powered;usedMbps=available?d.usedMbps:loaded?0:-1;}
    }
    public static final class View {
        public final Domain domain; public final boolean complete; public final Map<UUID,State> devices; public final List<Link> links;
        public final Set<UUID> ambiguousIds;
        public final Set<Pos> verifiedLanPositions; // reachable from origin over loaded LAN cells only
        View(Domain domain,boolean complete,Map<UUID,State> devices,List<Link> links,Set<UUID> ambiguousIds,Set<Pos> verifiedLanPositions){this.verifiedLanPositions=Collections.unmodifiableSet(verifiedLanPositions);this.domain=domain;this.complete=complete;this.devices=Collections.unmodifiableMap(devices);this.links=Collections.unmodifiableList(links);this.ambiguousIds=Collections.unmodifiableSet(ambiguousIds);}
    }
    private final Map<Pos,Cell> cells=new HashMap<>();
    private final Map<Pos,Domain> lanCache=new HashMap<>(),fiberCache=new HashMap<>();
    private long topologyRevision, routingRevision;
    public long revision(){return topologyRevision;}
    public long routingRevision(){return routingRevision;}
    public Map<Pos,Cell> cells(){return Collections.unmodifiableMap(cells);}
    public Cell cell(Pos p){return cells.get(p);}
    /** Metadata/occupancy changes do not invalidate the cable graph. */
    public boolean put(Pos p,Cell c){Cell old=cells.get(p);if(Objects.equals(old,c))return false;if(!sameRouting(old,c))routingRevision++;if(c==null)cells.remove(p);else cells.put(p,c);
        if(old==null||c==null||old.kind!=c.kind){lanCache.clear();fiberCache.clear();topologyRevision++;}return true;}
    // Ignore labels and legacy measured counters; they do not affect paths or capacities.
    private static boolean sameRouting(Cell a,Cell b){if(a==null||b==null||a.kind!=b.kind||a.devices.size()!=b.devices.size())return false;
        for(int i=0;i<a.devices.size();i++){Device x=a.devices.get(i),y=b.devices.get(i);if(!x.id.equals(y.id)||x.type!=y.type||!x.model.equals(y.model)||!x.pos.equals(y.pos)||x.dimension!=y.dimension||x.capacityMbps!=y.capacityMbps||x.powered!=y.powered||!Objects.equals(x.parent,y.parent)||x.slot!=y.slot||x.port!=y.port||!Objects.equals(x.patchId,y.patchId)||x.patchTier!=y.patchTier)return false;}return true;}
    public void clear(){cells.clear();lanCache.clear();fiberCache.clear();topologyRevision++;routingRevision++;}
    private boolean lan(Pos p){Cell c=cells.get(p);return c!=null&&c.kind.lan();}
    private boolean fiber(Pos p){Cell c=cells.get(p);return c!=null&&c.kind==Kind.FIBER;}
    private Domain component(Pos p,Map<Pos,Domain> cache,Predicate<Pos> allowed){
        if(!allowed.test(p))return null;Domain d=cache.get(p);if(d!=null)return d;
        // The finite persisted index is the bound. No chunk loading and no truncated component.
        Set<Pos> visited=TopologySearch.component(p,Pos::adjacent,allowed,Math.max(1,cells.size()));
        if(visited==null||visited.isEmpty())return null;d=new Domain(visited);for(Pos x:visited)cache.put(x,d);return d;
    }
    public Domain lanDomain(Pos p){return component(p,lanCache,this::lan);}
    private Domain fiberDomain(Pos p){return component(p,fiberCache,this::fiber);}
    /** Fiber endpoints are not transit nodes. Only a continuous fiber cable run links endpoints. */
    private Set<Pos> antennas(Pos terminal){return opticalPeers(terminal,Kind.ANTENNA);}
    private Set<Pos> opticalPeers(Pos terminal,Kind target){Set<Pos> out=new TreeSet<>();Set<Domain> done=new HashSet<>();for(Pos n:terminal.adjacent()){
        Domain d=fiberDomain(n);if(d==null||!done.add(d))continue;
        for(Pos c:d.positions)for(Pos end:c.adjacent()){Cell e=cells.get(end);if(e!=null&&e.kind==target)out.add(end);}
    }return out;}
    private Set<Pos> verifiedAntennas(Pos terminal,Predicate<Pos> loaded){return verifiedOpticalPeers(terminal,Kind.ANTENNA,loaded);}
    private Set<Pos> verifiedOpticalPeers(Pos terminal,Kind target,Predicate<Pos> loaded){Set<Pos> out=new HashSet<>(),seen=new HashSet<>();if(!loaded.test(terminal))return out;ArrayDeque<Pos> q=new ArrayDeque<>();for(Pos n:terminal.adjacent())if(fiber(n)&&loaded.test(n)&&seen.add(n))q.add(n);
        while(!q.isEmpty())for(Pos n:q.remove().adjacent())if(loaded.test(n)){Cell c=cells.get(n);if(c==null)continue;if(c.kind==target)out.add(n);else if(c.kind==Kind.FIBER&&seen.add(n))q.add(n);}return out;}
    public static final class Peer {
        public final Device device; public final boolean verified,loaded;
        Peer(Device device,boolean verified,boolean loaded){this.device=device;this.verified=verified;this.loaded=loaded;}
    }
    /** Direct optical endpoints only; a shared source retains one identity across all domains. */
    public List<Peer> opticalPeers(Pos origin,Predicate<Pos> loaded){Cell c=cells.get(origin);if(c==null||c.kind!=Kind.ANTENNA&&c.kind!=Kind.TERMINAL)return Collections.emptyList();Kind target=c.kind==Kind.ANTENNA?Kind.TERMINAL:Kind.ANTENNA;Set<Pos> live=verifiedOpticalPeers(origin,target,loaded);List<Peer> result=new ArrayList<>();for(Pos p:opticalPeers(origin,target))for(Device d:cells.get(p).devices)result.add(new Peer(d,live.contains(p),loaded.test(p)));return result;}
    private void addDevice(Device d,Predicate<Pos> loaded,Map<UUID,State> out,Set<UUID> ambiguous){if(ambiguous.contains(d.id))return;State prev=out.get(d.id);if(prev!=null){if(!prev.device.equals(d)){out.remove(d.id);ambiguous.add(d.id);}return;}out.put(d.id,new State(d,loaded.test(d.pos)));}
    public View view(Pos origin,Predicate<Pos> loaded){Domain domain=lanDomain(origin);Map<UUID,State> out=new LinkedHashMap<>();List<Link> links=new ArrayList<>();Set<UUID> ambiguous=new HashSet<>();if(domain==null)return new View(null,false,out,links,ambiguous,Collections.emptySet());
        boolean complete=true;for(Pos p:new TreeSet<>(domain.positions)){complete&=loaded.test(p);for(Device d:cells.get(p).devices)addDevice(d,loaded,out,ambiguous);}
        List<Device> terminals=new ArrayList<>();for(State s:out.values())if(s.device.type==Type.TERMINAL)terminals.add(s.device);
        for(Device t:terminals){Set<Pos> verified=verifiedAntennas(t.pos,loaded);for(Pos a:antennas(t.pos))for(Device d:cells.get(a).devices){addDevice(d,loaded,out,ambiguous);links.add(new Link(d.id,t.id,LinkType.FIBER,verified.contains(a)));}}
        for(State s:out.values())if(s.device.type==Type.SERVER&&s.device.parent!=null){State sw=out.get(s.device.parent);if(sw!=null&&sw.device.type==Type.SWITCH&&sw.device.pos.equals(s.device.pos))links.add(new Link(sw.device.id,s.device.id,LinkType.PATCH,sw.loaded&&s.loaded));}
        links.removeIf(l->!out.containsKey(l.from)||!out.containsKey(l.to));
        Set<Pos> reachable=complete?domain.positions:TopologySearch.component(origin,Pos::adjacent,p->lan(p)&&loaded.test(p),Math.max(1,cells.size()));
        return new View(domain,complete,out,links,ambiguous,reachable==null?Collections.emptySet():reachable);
    }
}
