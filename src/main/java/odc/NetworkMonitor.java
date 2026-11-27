package odc;

import java.util.*;

/** Read-only, domain-scoped projection. Global usage of shared sources is preserved. */
final class NetworkMonitor {
    static int layer(PhysicalTopology.Type t){return t==PhysicalTopology.Type.TERMINAL?1:t==PhysicalTopology.Type.SWITCH?2:t==PhysicalTopology.Type.SERVER?3:0;}
    static final class Node {
        final PhysicalTopology.State state; final BandwidthEngine.Usage usage; final boolean verified; final Set<String> reasons=new TreeSet<>(); final Set<UUID> bottlenecks=new TreeSet<>();
        Node(PhysicalTopology.State s,BandwidthEngine.Usage u,boolean verified){state=s;usage=u;this.verified=verified;}
    }
    final Map<UUID,Node> nodes=new LinkedHashMap<>(); final List<LogicalLinks.Link> links=new ArrayList<>();
    final double wan,lan,ingressFree; final int active,unavailable,boundary; final boolean complete;
    NetworkMonitor(PhysicalTopology.View view,List<LogicalLinks.Link> logical,BandwidthEngine.Result flow){
        boolean verifiedAll=view.complete;double w=0,l=0;int a=0,b=0,external=0;
        for(PhysicalTopology.State s:view.devices.values()){
            boolean verified=layer(s.device.type)==0?s.loaded:view.verifiedLanPositions.contains(s.device.pos)&&s.loaded;
            Node n=new Node(s,flow.devices.get(s.device.id),verified);verifiedAll&=verified&&n.usage!=null;nodes.put(s.device.id,n);
            if(s.device.type==PhysicalTopology.Type.TERMINAL&&n.verified&&n.usage!=null)w+=n.usage.wan;
        }
        for(LogicalLinks.Link e:logical){if(!nodes.containsKey(e.from)||!nodes.containsKey(e.to)){external++;continue;}links.add(e);verifiedAll&=e.state!=LogicalLinks.State.UNVERIFIED;if(e.state==LogicalLinks.State.ACTIVE)a++;else b++;}
        for(BandwidthEngine.Allocation x:flow.requests.values()){
            Node n=nodes.get(x.request.server);if(n==null)continue;
            if(x.request.traffic==BandwidthEngine.Traffic.LAN&&n.verified&&nodes.containsKey(x.request.peer)&&nodes.get(x.request.peer).verified)l+=x.served;
            if(x.unserved<=1e-5)continue;
            n.reasons.add(x.reason);for(UUID id:x.bottlenecks){if(nodes.containsKey(id)){n.bottlenecks.add(id);nodes.get(id).reasons.add("BOTTLENECK");}else for(LogicalLinks.Link e:links)if(e.id.equals(id)){n.bottlenecks.add(id);nodes.get(e.from).reasons.add("BOTTLENECK");nodes.get(e.to).reasons.add("BOTTLENECK");break;}}
        }
        complete=verifiedAll;wan=w;lan=l;active=a;boundary=external;unavailable=b+external;ingressFree=residualIngress(nodes,links,flow);
    }
    /** Additional WAN at local terminal inputs, holding current routes fixed.
     * Max flow on residual source--antenna--terminal--sink graph; no downstream capacity claim. */
    private static double residualIngress(Map<UUID,Node> nodes,List<LogicalLinks.Link> links,BandwidthEngine.Result flow){
        Map<UUID,Integer> indices=new HashMap<>();int count=2;for(UUID id:nodes.keySet())indices.put(id,count++);
        List<List<Arc>> g=new ArrayList<>();for(int i=0;i<count;i++)g.add(new ArrayList<>());
        for(Map.Entry<UUID,Node> e:nodes.entrySet()){Node n=e.getValue();if(!n.verified||!n.state.available||n.usage==null)continue;int level=layer(n.state.device.type);if(level==0)add(g,0,indices.get(e.getKey()),n.usage.free);if(level==1)add(g,indices.get(e.getKey()),1,n.usage.free);}
        for(LogicalLinks.Link e:links)if(e.kind==LogicalLinks.Kind.FIBER&&e.state==LogicalLinks.State.ACTIVE){BandwidthEngine.Usage u=flow.links.get(e.id);if(u!=null)add(g,indices.get(e.from),indices.get(e.to),u.free);}
        double total=0;while(true){Arc[] prev=new Arc[count];boolean[] seen=new boolean[count];ArrayDeque<Integer> q=new ArrayDeque<>();q.add(0);seen[0]=true;while(!q.isEmpty()&&!seen[1]){int i=q.remove();for(Arc e:g.get(i))if(e.cap>1e-7&&!seen[e.to]){seen[e.to]=true;prev[e.to]=e;q.add(e.to);}}
            if(!seen[1])return total;double v=Double.POSITIVE_INFINITY;for(int i=1;i!=0;i=prev[i].from)v=Math.min(v,prev[i].cap);for(int i=1;i!=0;i=prev[i].from){Arc e=prev[i];e.cap-=v;e.reverse.cap+=v;}total+=v;
        }
    }
    private static final class Arc {final int from,to;double cap;Arc reverse;Arc(int f,int t,double c){from=f;to=t;cap=c;}}
    private static void add(List<List<Arc>> g,int a,int b,double cap){if(cap<=1e-7)return;Arc x=new Arc(a,b,cap),y=new Arc(b,a,0);x.reverse=y;y.reverse=x;g.get(a).add(x);g.get(b).add(y);}
}
