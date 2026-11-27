package odc;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Dimension-local persistent uplinks. Physical discovery is not authorization to create one. */
public final class LogicalLinks {
 public enum Kind { FIBER, UPLINK, PATCH }
 public enum State { ACTIVE, OFFLINE, UNVERIFIED, SEPARATED }
 public static int speed(int tier){return tier==1?100:tier==2?1000:tier==3?100000:0;}
 public static UUID automaticId(Kind k,UUID a,UUID b){return UUID.nameUUIDFromBytes((k+":"+a+":"+b).getBytes(StandardCharsets.UTF_8));}
 public static final class Link {
  public final UUID id,from,to; public final Kind kind; public final int tier,capacityMbps,usedMbps; public final State state;
  public Link(UUID id,UUID from,UUID to,Kind kind,int tier,int capacity,State state,int used){this.id=Objects.requireNonNull(id);this.from=Objects.requireNonNull(from);this.to=Objects.requireNonNull(to);this.kind=Objects.requireNonNull(kind);this.tier=tier;capacityMbps=Math.max(0,capacity);this.state=Objects.requireNonNull(state);usedMbps=used;}
 }
 private final Map<UUID,Link> links=new LinkedHashMap<>();
 private long revision;
 public long revision(){return revision;}
 public Collection<Link> all(){return Collections.unmodifiableCollection(links.values());}
 public Link find(UUID terminal,UUID sw){for(Link l:links.values())if(l.from.equals(terminal)&&l.to.equals(sw))return l;return null;}
 public boolean restore(Link l){if(l.kind!=Kind.UPLINK||speed(l.tier)==0||l.from.equals(l.to)||links.containsKey(l.id)||find(l.from,l.to)!=null)return false;links.put(l.id,l);revision++;return true;}
 public Link add(UUID terminal,UUID sw,int tier){Link l=new Link(UUID.randomUUID(),terminal,sw,Kind.UPLINK,tier,speed(tier),State.UNVERIFIED,-1);return restore(l)?l:null;}
 public Link remove(UUID terminal,UUID sw){Link l=find(terminal,sw);if(l!=null){links.remove(l.id);revision++;}return l;}
 public boolean replace(UUID terminal,UUID sw,int tier){Link l=find(terminal,sw);if(l==null||speed(tier)<=speed(l.tier))return false;links.put(l.id,new Link(l.id,l.from,l.to,l.kind,tier,speed(tier),State.UNVERIFIED,-1));revision++;return true;}
 public List<Link> removeDevice(UUID id){List<Link> out=new ArrayList<>();Iterator<Link> it=links.values().iterator();while(it.hasNext()){Link l=it.next();if(l.from.equals(id)||l.to.equals(id)){out.add(l);it.remove();revision++;}}return out;}
 public void clear(){if(!links.isEmpty()){links.clear();revision++;}}
 public static State state(PhysicalTopology.View v,UUID a,UUID b){PhysicalTopology.State x=v.devices.get(a),y=v.devices.get(b);if(x==null||y==null)return State.SEPARATED;if(!x.loaded||!y.loaded||!v.verifiedLanPositions.contains(x.device.pos)||!v.verifiedLanPositions.contains(y.device.pos))return State.UNVERIFIED;return x.available&&y.available?State.ACTIVE:State.OFFLINE;}
}
