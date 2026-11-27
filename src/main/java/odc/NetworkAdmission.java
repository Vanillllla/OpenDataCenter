package odc;
import java.util.*;
/** Fixed-route residual quotes and authoritative global reservation admission. */
final class NetworkAdmission {
 static final double EPS=1e-5;
 static String key(UUID job,UUID host){return job+":"+host;}
 static Map<UUID,Integer> amounts(Collection<PoolResources.Binding> bindings){Map<UUID,Integer> m=new TreeMap<>();for(PoolResources.Binding b:bindings)if(b.kind==PoolResources.Kind.NETWORK)m.merge(b.server,b.amount,(a,c)->(int)Math.min(Integer.MAX_VALUE,(long)a+c));return m;}
 static List<BandwidthEngine.Request> requests(UUID job,Collection<PoolResources.Binding> bindings,boolean reserved){List<BandwidthEngine.Request> r=new ArrayList<>();for(Map.Entry<UUID,Integer> e:amounts(bindings).entrySet())r.add(new BandwidthEngine.Request(key(job,e.getKey()),e.getKey(),null,e.getValue(),reserved?e.getValue():0));return r;}
 static boolean served(BandwidthEngine.Result result,UUID job,Collection<PoolResources.Binding> bindings){for(Map.Entry<UUID,Integer> e:amounts(bindings).entrySet()){BandwidthEngine.Allocation a=result.requests.get(key(job,e.getKey()));if(a==null||a.served+EPS<e.getValue())return false;}return true;}
 static final class Quote {
  final BandwidthEngine engine; final List<BandwidthEngine.Request> existing;final BandwidthEngine.Result baseline;final Map<UUID,Integer> held=new HashMap<>();
  private Map<UUID,Integer> previous;private BandwidthEngine.Result projected;private final Map<UUID,Integer> free=new HashMap<>();
  Quote(BandwidthEngine engine,List<BandwidthEngine.Request> existing,BandwidthEngine.Result baseline){this.engine=engine;this.existing=existing;this.baseline=baseline;for(BandwidthEngine.Request r:existing)held.merge(r.server,(int)r.requested,(a,b)->(int)Math.min(Integer.MAX_VALUE,(long)a+b));}
  BandwidthEngine.Result project(Map<UUID,Integer> draft){if(draft.isEmpty())return baseline;if(!draft.equals(previous)){List<BandwidthEngine.Request> all=new ArrayList<>(existing);for(Map.Entry<UUID,Integer> e:draft.entrySet())if(e.getValue()>0)all.add(new BandwidthEngine.Request("draft:"+e.getKey(),e.getKey(),null,e.getValue(),0));projected=engine.solve(all);previous=new TreeMap<>(draft);free.clear();}return projected;}
  int additional(UUID server,Map<UUID,Integer> draft){boolean changed=!draft.equals(previous);BandwidthEngine.Result r=project(draft);if(draft.isEmpty()&&changed){previous=new TreeMap<>();projected=baseline;free.clear();}return free.computeIfAbsent(server,id->floor(engine.additional(r,Collections.singleton(id))));}
  String failure(Map<UUID,Integer> draft){BandwidthEngine.Result r=project(draft);for(Map.Entry<UUID,Integer> e:draft.entrySet()){if(e.getValue()<=0)continue;BandwidthEngine.Allocation a=r.requests.get("draft:"+e.getKey());if(a==null||a.served+EPS<e.getValue()){String limit=a==null||a.bottlenecks.isEmpty()?"NO_ROUTE":engine.constraint(a.bottlenecks.get(0));return e.getKey().toString().substring(0,8)+": "+floor(a==null?0:a.served)+" / "+e.getValue()+" Mb/s | "+limit;}}return "";}
 }
 static int floor(double x){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.floor(x+EPS)));}
}
