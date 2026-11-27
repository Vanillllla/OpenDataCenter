package odc;

import java.util.*;

/** Pure, dimension-wide bandwidth engine. Decimal Mbps; a shared device or link is
 * charged once per traversing flow, regardless of direction. No compute allocation. */
public final class BandwidthEngine {
    private static final double EPS=1e-7;
    public enum Traffic { WAN, LAN }
    public static final class Node {
        public final UUID id; public final PhysicalTopology.Type type; public final int installed; public final boolean available;
        public Node(UUID id,PhysicalTopology.Type type,int installed,boolean available){this.id=Objects.requireNonNull(id);this.type=Objects.requireNonNull(type);if(installed<0)throw new IllegalArgumentException("capacity");this.installed=installed;this.available=available;}
    }
    public static final class Edge {
        public final UUID id,from,to; public final LogicalLinks.Kind kind; public final int installed,portLimit; public final boolean available;
        public Edge(UUID id,UUID from,UUID to,LogicalLinks.Kind kind,int installed,int portLimit,boolean available){this.id=Objects.requireNonNull(id);this.from=Objects.requireNonNull(from);this.to=Objects.requireNonNull(to);this.kind=Objects.requireNonNull(kind);if(installed<0||portLimit<0)throw new IllegalArgumentException("capacity");this.installed=installed;this.portLimit=portLimit;this.available=available;}
    }
    public static final class Request {
        public final String id; public final UUID server,peer; public final Traffic traffic; public final double requested,reserved;
        public Request(String id,UUID server,UUID peer,double requested,double reserved){this.id=Objects.requireNonNull(id);this.server=Objects.requireNonNull(server);this.peer=peer;traffic=peer==null?Traffic.WAN:Traffic.LAN;if(peer!=null&&peer.equals(server)||!Double.isFinite(requested)||!Double.isFinite(reserved)||reserved<0||requested<reserved||requested>Integer.MAX_VALUE)throw new IllegalArgumentException("request");this.requested=requested;this.reserved=reserved;}
        @Override public boolean equals(Object o){if(!(o instanceof Request))return false;Request r=(Request)o;return id.equals(r.id)&&server.equals(r.server)&&Objects.equals(peer,r.peer)&&requested==r.requested&&reserved==r.reserved;}
        @Override public int hashCode(){return Objects.hash(id,server,peer,requested,reserved);}
    }
    public static final class Usage {
        public final int installed; public final double available,served,free,wan,lan;
        Usage(int installed,double available,double wan,double lan){this.installed=installed;this.available=available;this.wan=wan;this.lan=lan;served=wan+lan;free=Math.max(0,available-served);}
    }
    public static final class Route {
        public final List<UUID> devices,links; public final double mbps;
        Route(Path p,double mbps){devices=p.nodes;links=p.edges;this.mbps=mbps;}
    }
    public static final class Allocation {
        public final Request request;public final double served,reservedServed,unserved;public final List<Route> routes;public final List<UUID> bottlenecks;public final String reason;
        Allocation(Request r,double served,double reservedServed,List<Route> routes,List<UUID> bottlenecks,String reason){request=r;this.served=served;this.reservedServed=reservedServed;unserved=Math.max(0,r.requested-served);this.routes=Collections.unmodifiableList(routes);this.bottlenecks=Collections.unmodifiableList(bottlenecks);this.reason=reason;}
    }
    public static final class Result {
        public final Map<UUID,Usage> devices,links; public final Map<String,Allocation> requests;public final double wan,lan;
        Result(Map<UUID,Usage> devices,Map<UUID,Usage> links,Map<String,Allocation> requests,double wan,double lan){this.devices=Collections.unmodifiableMap(devices);this.links=Collections.unmodifiableMap(links);this.requests=Collections.unmodifiableMap(requests);this.wan=wan;this.lan=lan;}
    }
    private static final class Path {
        final List<UUID> nodes,edges; final int[] resources; final UUID group;
        Path(List<UUID> nodes,List<UUID> edges,int[] resources,UUID group){this.nodes=Collections.unmodifiableList(new ArrayList<>(nodes));this.edges=Collections.unmodifiableList(new ArrayList<>(edges));this.resources=resources;this.group=group;}
    }
    private static final class Candidate {
        final Request request;final List<Path> paths;final double[] flow;double served,reservedServed;
        Candidate(Request request,List<Path> paths){this.request=request;this.paths=paths;flow=new double[paths.size()];}
    }
    private final Map<UUID,Node> nodes=new TreeMap<>();private final Map<UUID,Edge> edges=new TreeMap<>();
    private final Map<UUID,Integer> ni=new HashMap<>(),ei=new HashMap<>();private final Map<UUID,List<Edge>> incoming=new HashMap<>();
    private final Map<UUID,Edge> patch=new HashMap<>();private final Map<UUID,List<Path>> wanPaths=new HashMap<>();
    private final double[] cap;private final int[] nominal;
    public BandwidthEngine(Collection<Node> nodes,Collection<Edge> edges){
        for(Node n:nodes)if(this.nodes.put(n.id,n)!=null)throw new IllegalArgumentException("duplicate device");
        for(Edge e:edges){Node a=this.nodes.get(e.from),b=this.nodes.get(e.to);if(a==null||b==null)continue;
            boolean valid=e.kind==LogicalLinks.Kind.FIBER?antenna(a.type)&&b.type==PhysicalTopology.Type.TERMINAL:e.kind==LogicalLinks.Kind.UPLINK?a.type==PhysicalTopology.Type.TERMINAL&&b.type==PhysicalTopology.Type.SWITCH:a.type==PhysicalTopology.Type.SWITCH&&b.type==PhysicalTopology.Type.SERVER;
            if(!valid||this.edges.put(e.id,e)!=null)throw new IllegalArgumentException("invalid/duplicate link");
        }
        cap=new double[this.nodes.size()+this.edges.size()];nominal=new int[cap.length];int i=0;
        for(Node n:this.nodes.values()){ni.put(n.id,i);nominal[i]=n.installed;cap[i++]=n.available?n.installed:0;}
        for(Edge e:this.edges.values()){ei.put(e.id,i);nominal[i]=e.installed;cap[i++]=e.available&&this.nodes.get(e.from).available&&this.nodes.get(e.to).available?Math.min(e.installed,e.portLimit):0;incoming.computeIfAbsent(e.to,k->new ArrayList<>()).add(e);if(e.kind==LogicalLinks.Kind.PATCH&&patch.put(e.to,e)!=null)throw new IllegalArgumentException("multiple server ports");}
    }
    private static boolean antenna(PhysicalTopology.Type t){return t==PhysicalTopology.Type.RADIO||t==PhysicalTopology.Type.SATELLITE||t==PhysicalTopology.Type.TOWER;}
    private Path path(List<UUID> ns,List<Edge> es,UUID group){List<UUID> ls=new ArrayList<>();int[] r=new int[ns.size()+es.size()];int i=0;for(UUID n:ns)r[i++]=ni.get(n);for(Edge e:es){r[i++]=ei.get(e.id);ls.add(e.id);}return new Path(ns,ls,r,group);}
    private List<Path> wan(UUID server){return wanPaths.computeIfAbsent(server,s->{List<Path> out=new ArrayList<>();Edge p=patch.get(s);if(p!=null)for(Edge u:incoming.getOrDefault(p.from,Collections.emptyList()))for(Edge f:incoming.getOrDefault(u.from,Collections.emptyList()))out.add(path(Arrays.asList(f.from,u.from,p.from,s),Arrays.asList(f,u,p),u.from));return out;});}
    private List<Path> lan(UUID a,UUID b){List<Path> out=new ArrayList<>();Edge p=patch.get(a),q=patch.get(b);if(p==null||q==null)return out;if(p.from.equals(q.from)){out.add(path(Arrays.asList(a,p.from,b),Arrays.asList(p,q),p.from));return out;}
        Map<UUID,Edge> other=new TreeMap<>();for(Edge u:incoming.getOrDefault(q.from,Collections.emptyList()))other.put(u.from,u);for(Edge u:incoming.getOrDefault(p.from,Collections.emptyList())){Edge v=other.get(u.from);if(v!=null)out.add(path(Arrays.asList(a,p.from,u.from,q.from,b),Arrays.asList(p,u,v,q),u.from));}return out;}
    private boolean open(Path p,double[] room){for(int i:p.resources)if(room[i]<=EPS)return false;return true;}
    /** Simultaneous progressive filling: one increment per demand, equally divided
     * over still-open terminals, then over sources at each terminal. Saturated paths
     * leave the active set. Thus request or hash-map iteration order is not a priority. */
    private void fill(List<Candidate> cs,double[] target,double[] room){
        double[] slope=new double[cap.length];double[][] weights=new double[cs.size()][];
        while(true){Arrays.fill(slope,0);double delta=Double.POSITIVE_INFINITY;boolean any=false;
            for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);double left=target[j]-c.served;double[] w=weights[j];if(w==null)weights[j]=w=new double[c.paths.size()];else Arrays.fill(w,0);if(left<=EPS)continue;
                Map<UUID,Integer> groups=new TreeMap<>();for(Path p:c.paths)if(open(p,room))groups.merge(p.group,1,Integer::sum);if(groups.isEmpty())continue;
                any=true;delta=Math.min(delta,left);for(int k=0;k<c.paths.size();k++){Path p=c.paths.get(k);if(!open(p,room))continue;w[k]=1.0/groups.size()/groups.get(p.group);for(int i:p.resources)slope[i]+=w[k];}
            }
            if(!any)break;for(int i=0;i<room.length;i++)if(slope[i]>0)delta=Math.min(delta,room[i]/slope[i]);if(!Double.isFinite(delta)||delta<=0)break;
            for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);for(int k=0;k<weights[j].length;k++){double v=delta*weights[j][k];c.flow[k]+=v;c.served+=v;}}
            for(int i=0;i<room.length;i++)room[i]=Math.max(0,room[i]-delta*slope[i]);
        }
    }
    /** Max-flow fallback for WAN-only cohorts: can move an earlier path off a shared
     * bottleneck to serve a constrained request. Targets are max-min fair to 1e-5 Mbps.
     * The balanced progressive solution is retained when it already serves these targets. */
    private void phase(List<Candidate> cs,double[] limits,double[] room){
        double[] before=room.clone(),base=new double[cs.size()],target=new double[cs.size()];double[][] previous=new double[cs.size()][];
        boolean onlyWan=true;for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);base[j]=c.served;target[j]=base[j]+limits[j];previous[j]=c.flow.clone();if(limits[j]>EPS&&c.request.traffic!=Traffic.WAN)onlyWan=false;}
        fill(cs,target,room);boolean shortfall=false;for(int j=0;j<cs.size();j++)if(cs.get(j).served+EPS<target[j])shortfall=true;
        if(!onlyWan||!shortfall)return;
        double[] fair=fairWan(cs,limits,before);boolean same=true;for(int j=0;j<cs.size();j++)if(Math.abs(cs.get(j).served-base[j]-fair[j])>1e-5)same=false;if(same)return;
        for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);c.served=base[j];System.arraycopy(previous[j],0,c.flow,0,c.flow.length);target[j]=base[j]+fair[j];}System.arraycopy(before,0,room,0,room.length);
        fill(cs,target,room);boolean complete=true;for(int j=0;j<cs.size();j++)if(cs.get(j).served+1e-5<target[j])complete=false;if(complete)return;
        // The fair targets are feasible but the equal splitter picked a dead-end.
        Flow f=flow(cs,fair,before);if(f.total+1e-4<sum(fair))throw new IllegalStateException("infeasible fair WAN");
        for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);c.served=base[j];System.arraycopy(previous[j],0,c.flow,0,c.flow.length);}System.arraycopy(before,0,room,0,room.length);
        for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);double left=f.sinks[j].flow();for(int k=0;k<c.paths.size()&&left>EPS;k++){Path p=c.paths.get(k);double take=left;for(int r:p.resources)take=Math.min(take,f.resources[r].flow());if(take<=EPS)continue;for(int r:p.resources){f.resources[r].remaining+=take;room[r]=Math.max(0,room[r]-take);}c.flow[k]+=take;c.served+=take;left-=take;}if(left>1e-4)throw new IllegalStateException("WAN decomposition");}
    }
    private static double sum(double[] a){double s=0;for(double v:a)s+=v;return s;}
    private double[] fairWan(List<Candidate> cs,double[] limits,double[] room){
        double[] a=new double[limits.length];boolean[] active=new boolean[limits.length];for(int i=0;i<a.length;i++)active[i]=limits[i]>EPS;
        for(int round=0;round<=a.length;round++){
            double hi=Double.POSITIVE_INFINITY;for(int j=0;j<a.length;j++)if(active[j])hi=Math.min(hi,limits[j]-a[j]);if(!Double.isFinite(hi))break;
            double lo=0;double[] t=a.clone();for(int j=0;j<a.length;j++)if(active[j])t[j]+=hi;Flow upper=flow(cs,t,room);
            if(upper.total>=sum(t)-EPS){lo=hi;}else for(int k=0;k<45&&hi-lo>1e-8;k++){double mid=(lo+hi)/2;for(int j=0;j<a.length;j++)t[j]=a[j]+(active[j]?mid:0);Flow f=flow(cs,t,room);if(f.total>=sum(t)-EPS)lo=mid;else hi=mid;}
            for(int j=0;j<a.length;j++)if(active[j])a[j]+=lo;
            Flow f=flow(cs,a,room);boolean[] reachable=f.graph.reachable(f.source,1e-5);boolean froze=false;
            for(int j=0;j<a.length;j++)if(active[j]&&(limits[j]-a[j]<=1e-6||!reachable[f.endpoints[j]])){active[j]=false;froze=true;}
            if(!froze){ // numerical boundary: explicitly test individual extra feasibility
                for(int j=0;j<a.length;j++)if(active[j]){t=a.clone();t[j]+=Math.min(1e-5,limits[j]-a[j]);if(flow(cs,t,room).total<sum(t)-EPS){active[j]=false;froze=true;}}
            }
            if(!froze)break;
        }
        return a;
    }
    private static final class Arc {final int to,rev;final double initial;double remaining;Arc(int to,int rev,double cap){this.to=to;this.rev=rev;initial=remaining=cap;}double flow(){return Math.max(0,initial-remaining);}}
    private static final class Dinic {
        final List<List<Arc>> g=new ArrayList<>();int[] level,next;
        Dinic(int n){for(int i=0;i<n;i++)g.add(new ArrayList<>());level=new int[n];next=new int[n];}
        Arc add(int a,int b,double cap){Arc x=new Arc(b,g.get(b).size(),cap),y=new Arc(a,g.get(a).size(),0);g.get(a).add(x);g.get(b).add(y);return x;}
        boolean[] reachable(int s,double eps){boolean[] seen=new boolean[g.size()];ArrayDeque<Integer> q=new ArrayDeque<>();seen[s]=true;q.add(s);while(!q.isEmpty()){int a=q.remove();for(Arc e:g.get(a))if(e.remaining>eps&&!seen[e.to]){seen[e.to]=true;q.add(e.to);}}return seen;}
        boolean levels(int s,int t){Arrays.fill(level,-1);ArrayDeque<Integer> q=new ArrayDeque<>();q.add(s);level[s]=0;while(!q.isEmpty()){int a=q.remove();for(Arc e:g.get(a))if(e.remaining>EPS&&level[e.to]<0){level[e.to]=level[a]+1;q.add(e.to);}}return level[t]>=0;}
        double send(int a,int t,double lim){if(a==t)return lim;List<Arc> list=g.get(a);for(;next[a]<list.size();next[a]++){Arc e=list.get(next[a]);if(e.remaining>EPS&&level[e.to]==level[a]+1){double x=send(e.to,t,Math.min(lim,e.remaining));if(x>EPS){e.remaining-=x;g.get(e.to).get(e.rev).remaining+=x;return x;}}}return 0;}
        double solve(int s,int t){double total=0;while(levels(s,t)){Arrays.fill(next,0);double x;while((x=send(s,t,Double.MAX_VALUE))>EPS)total+=x;}return total;}
    }
    private static final class Flow {Dinic graph;int source;Arc[] resources,sinks;int[] endpoints;double total;}
    private Flow flow(List<Candidate> cs,double[] targets,double[] room){Flow f=new Flow();int n=nodes.size(),source=2*n,sink=source+1;Dinic g=f.graph=new Dinic(sink+1);f.source=source;f.resources=new Arc[cap.length];f.sinks=new Arc[cs.size()];f.endpoints=new int[cs.size()];
        for(Node node:nodes.values()){int i=ni.get(node.id);f.resources[i]=g.add(i*2,i*2+1,room[i]);if(antenna(node.type))g.add(source,i*2,room[i]);}
        for(Edge e:edges.values()){int i=ei.get(e.id);f.resources[i]=g.add(ni.get(e.from)*2+1,ni.get(e.to)*2,room[i]);}
        for(int j=0;j<cs.size();j++){UUID id=cs.get(j).request.server;Integer i=nodes.containsKey(id)&&nodes.get(id).type==PhysicalTopology.Type.SERVER?ni.get(id):null;int from=i==null?source:i*2+1;f.endpoints[j]=from;f.sinks[j]=g.add(from,sink,i==null?0:targets[j]);}
        f.total=g.solve(source,sink);return f;}
    /** Additional end-to-end WAN with existing routes held fixed; no double counting across selected hosts. */
    double additional(Result base,Collection<UUID> servers){
        double[] room=cap.clone();for(Map.Entry<UUID,Usage> e:base.devices.entrySet()){Integer i=ni.get(e.getKey());if(i!=null)room[i]=Math.max(0,room[i]-e.getValue().served);}for(Map.Entry<UUID,Usage> e:base.links.entrySet()){Integer i=ei.get(e.getKey());if(i!=null)room[i]=Math.max(0,room[i]-e.getValue().served);}
        List<Candidate> cs=new ArrayList<>();for(UUID id:new TreeSet<>(servers))if(ni.containsKey(id)&&nodes.get(id).type==PhysicalTopology.Type.SERVER)cs.add(new Candidate(new Request("probe:"+id,id,null,Integer.MAX_VALUE,0),Collections.emptyList()));double[] targets=new double[cs.size()];Arrays.fill(targets,Integer.MAX_VALUE);return cs.isEmpty()?0:flow(cs,targets,room).total;
    }
    boolean hasWanRoute(UUID id){for(Path p:wan(id))if(open(p,cap))return true;return false;}
    String constraint(UUID id){Node n=nodes.get(id);if(n!=null)return n.type.name();Edge e=edges.get(id);return e==null?"NO_ROUTE":e.kind==LogicalLinks.Kind.PATCH&&e.portLimit<e.installed?"SWITCH_PORT":e.kind==LogicalLinks.Kind.FIBER?"FIBER":"LAN T"+(e.installed==100?1:e.installed==1000?2:3);}
    public Result solve(Collection<Request> demands){List<Request> ordered=new ArrayList<>(demands);ordered.sort(Comparator.comparing(r->r.id));Set<String> ids=new HashSet<>();List<Candidate> cs=new ArrayList<>();for(Request r:ordered){if(!ids.add(r.id))throw new IllegalArgumentException("duplicate request");cs.add(new Candidate(r,r.traffic==Traffic.WAN?wan(r.server):lan(r.server,r.peer)));}
        double[] room=cap.clone(),limits=new double[cs.size()];for(int j=0;j<cs.size();j++)limits[j]=cs.get(j).request.reserved;phase(cs,limits,room);
        for(int j=0;j<cs.size();j++){Candidate c=cs.get(j);c.reservedServed=c.served;limits[j]=Math.max(0,c.request.requested-c.request.reserved);}phase(cs,limits,room);
        double[] w=new double[cap.length],l=new double[cap.length];Map<String,Allocation> allocations=new LinkedHashMap<>();double wan=0,lan=0;
        for(Candidate c:cs){List<Route> routes=new ArrayList<>();Set<UUID> bottlenecks=new TreeSet<>();boolean shortfall=c.request.requested-c.served>1e-5;for(int k=0;k<c.paths.size();k++){Path p=c.paths.get(k);double v=c.flow[k];if(v>EPS){routes.add(new Route(p,v));for(int i:p.resources)(c.request.traffic==Traffic.WAN?w:l)[i]+=v;}if(shortfall){for(UUID id:p.nodes)if(room[ni.get(id)]<=EPS)bottlenecks.add(id);for(UUID id:p.edges)if(room[ei.get(id)]<=EPS)bottlenecks.add(id);}}
            if(c.request.traffic==Traffic.WAN)wan+=c.served;else lan+=c.served;allocations.put(c.request.id,new Allocation(c.request,c.served,c.reservedServed,routes,new ArrayList<>(bottlenecks),!shortfall?"OK":c.paths.isEmpty()?"NO_ROUTE":bottlenecks.isEmpty()?"INSUFFICIENT_CAPACITY":"BOTTLENECK"));}
        Map<UUID,Usage> du=new LinkedHashMap<>(),eu=new LinkedHashMap<>();for(UUID id:nodes.keySet()){int i=ni.get(id);du.put(id,new Usage(nominal[i],cap[i],w[i],l[i]));}for(UUID id:edges.keySet()){int i=ei.get(id);eu.put(id,new Usage(nominal[i],cap[i],w[i],l[i]));}return new Result(du,eu,allocations,wan,lan);
    }
}
