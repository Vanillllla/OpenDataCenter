package odc;
import java.util.*;
import static odc.PhysicalTopology.Type.*;
import static odc.LogicalLinks.Kind.*;

public final class BandwidthEngineTest {
    static int checks,sequence;
    static void yes(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    static void eq(double a,double b){checks++;if(Math.abs(a-b)>1e-4)throw new AssertionError("check "+checks+": "+a+" != "+b);}
    static UUID id(){return new UUID(0,++sequence);}
    static final class Graph {
        final List<BandwidthEngine.Node> n=new ArrayList<>();final List<BandwidthEngine.Edge> e=new ArrayList<>();
        UUID node(PhysicalTopology.Type t,int c){return node(t,c,true);}
        UUID node(PhysicalTopology.Type t,int c,boolean live){UUID u=id();n.add(new BandwidthEngine.Node(u,t,c,live));return u;}
        UUID edge(UUID a,UUID b,LogicalLinks.Kind k,int c){return edge(a,b,k,c,c,true);}
        UUID edge(UUID a,UUID b,LogicalLinks.Kind k,int c,int port,boolean active){UUID u=id();e.add(new BandwidthEngine.Edge(u,a,b,k,c,port,active));return u;}
        BandwidthEngine.Result run(BandwidthEngine.Request... r){return new BandwidthEngine(n,e).solve(Arrays.asList(r));}
    }
    static BandwidthEngine.Request wan(String name,UUID s,double demand){return new BandwidthEngine.Request(name,s,null,demand,demand);}
    static BandwidthEngine.Request lan(String name,UUID s,UUID t,double demand){return new BandwidthEngine.Request(name,s,t,demand,demand);}
    static double served(BandwidthEngine.Result r,String s){return r.requests.get(s).served;}
    static double use(BandwidthEngine.Result r,UUID id){return r.devices.get(id).served;}
    static UUID server(Graph g,UUID sw,int capacity,int cable,int port){UUID s=g.node(SERVER,capacity);g.edge(sw,s,PATCH,cable,port,true);return s;}
    static UUID terminal(Graph g,UUID antenna,int cap){UUID t=g.node(TERMINAL,cap);g.edge(antenna,t,FIBER,cap);return t;}
    static UUID sw(Graph g,UUID t,int cap){UUID s=g.node(SWITCH,cap);g.edge(t,s,UPLINK,cap);return s;}
    static void valid(BandwidthEngine.Result r){for(BandwidthEngine.Usage u:r.devices.values())checkUsage(u);for(BandwidthEngine.Usage u:r.links.values())checkUsage(u);for(BandwidthEngine.Allocation a:r.requests.values()){yes(a.served<=a.request.requested+1e-5);yes(a.reservedServed<=a.request.reserved+1e-5);eq(a.served+a.unserved,a.request.requested);double route=0;for(BandwidthEngine.Route p:a.routes)route+=p.mbps;eq(route,a.served);} }
    static void checkUsage(BandwidthEngine.Usage u){yes(u.served<=u.available+1e-5);yes(u.available<=u.installed);yes(u.served>=0);eq(u.served,u.wan+u.lan);eq(u.free+u.served,u.available);}
    static void basic(){Graph g=new Graph();UUID a=g.node(RADIO,10000),t=terminal(g,a,1000),s1=sw(g,t,1000),s2=sw(g,t,1000),x=server(g,s1,1000,1000,1000),y=server(g,s2,1000,1000,1000);BandwidthEngine.Result r=g.run(wan("x",x,1000),wan("y",y,1000));eq(served(r,"x"),500);eq(served(r,"y"),500);eq(use(r,t),1000);valid(r);
        r=g.run(new BandwidthEngine.Request("x",x,null,800,800),new BandwidthEngine.Request("y",y,null,800,0));eq(served(r,"x"),800);eq(served(r,"y"),200);eq(r.requests.get("y").reservedServed,0);valid(r);
    }
    static void equalSplit(){Graph g=new Graph();UUID a=g.node(RADIO,10000),b=g.node(SATELLITE,10000),t=terminal(g,a,200),u=terminal(g,b,2000),s=sw(g,t,2000);g.edge(u,s,UPLINK,2000);UUID x=server(g,s,2000,2000,2000);BandwidthEngine.Result r=g.run(wan("x",x,1000));eq(use(r,t),200);eq(use(r,u),800);valid(r);
        Graph h=new Graph();UUID[] ts=new UUID[3];UUID k=h.node(SWITCH,5000);for(int i=0;i<3;i++){ts[i]=terminal(h,h.node(RADIO,5000),5000);h.edge(ts[i],k,UPLINK,5000);}UUID z=server(h,k,5000,5000,5000);r=h.run(wan("z",z,900));for(UUID q:ts)eq(use(r,q),300);valid(r);}
    static void shared(){Graph g=new Graph();UUID a=g.node(SATELLITE,2000),t=terminal(g,a,5000),u=terminal(g,a,5000),s=sw(g,t,5000),v=sw(g,u,5000),x=server(g,s,5000,5000,5000),y=server(g,v,5000,5000,5000);BandwidthEngine.Result r=g.run(wan("x",x,2000),wan("y",y,2000));eq(use(r,a),2000);eq(served(r,"x"),1000);eq(served(r,"y"),1000);valid(r);
        // A flexible consumer must move off a saturated shared route for a constrained consumer.
        Graph h=new Graph();UUID shared=h.node(RADIO,1000),spare=h.node(SATELLITE,1000),ta=terminal(h,shared,1000),tb=terminal(h,spare,1000),sa=sw(h,ta,1000),sb=sw(h,ta,1000);h.edge(tb,sb,UPLINK,1000);UUID p=server(h,sa,1000,1000,1000),q=server(h,sb,1000,1000,1000);r=h.run(wan("constrained",p,1000),wan("flexible",q,1000));eq(served(r,"constrained"),1000);eq(served(r,"flexible"),1000);eq(use(r,tb),1000);valid(r);
        r=h.run(wan("constrained",p,2000),wan("flexible",q,500));eq(served(r,"constrained"),1000);eq(served(r,"flexible"),500);valid(r);
    }
    static void multipleSources(){Graph g=new Graph();UUID a=g.node(RADIO,500),b=g.node(SATELLITE,2000),t=terminal(g,a,10000);g.edge(b,t,FIBER,10000);UUID sw=sw(g,t,3000),x=server(g,sw,3000,3000,3000);BandwidthEngine.Result r=g.run(wan("x",x,2400));eq(use(r,a),500);eq(use(r,b),1900);valid(r);
        for(int tier=1;tier<=3;tier++){Graph h=new Graph();UUID src=h.node(TOWER,100000),tt=terminal(h,src,100000),s=sw(h,tt,100000),v=server(h,s,100000,LogicalLinks.speed(tier),100000);r=h.run(wan("x",v,100000));eq(served(r,"x"),LogicalLinks.speed(tier));valid(r);}
    }
    static void limitsAndLan(){Graph g=new Graph();UUID a=g.node(TOWER,10000),t=terminal(g,a,10000),sw=sw(g,t,2000),sw2=sw(g,t,2000),x=server(g,sw,10000,100000,100),y=server(g,sw,1000,1000,1000),z=server(g,sw2,1000,1000,1000);BandwidthEngine.Result r=g.run(wan("x",x,500),lan("local",y,z,700));eq(served(r,"x"),100);eq(served(r,"local"),700);eq(use(r,a),100);eq(r.lan,700);eq(use(r,t),800);valid(r);
        r=g.run(lan("local",x,y,300));eq(served(r,"local"),100);eq(use(r,a),0);eq(use(r,t),0);eq(use(r,sw),100);valid(r);
        Graph h=new Graph();UUID aa=h.node(RADIO,500),tt=h.node(TERMINAL,1000,false),ss=sw(h,tt,1000),xx=server(h,ss,1000,1000,1000);h.edge(aa,tt,FIBER,500);r=h.run(wan("x",xx,200));eq(served(r,"x"),0);yes(r.requests.get("x").reason.equals("BOTTLENECK"));valid(r);
        r=h.run(wan("x",id(),200));eq(served(r,"x"),0);yes(r.requests.get("x").reason.equals("NO_ROUTE"));valid(r);
    }
    static PhysicalTopology.Pos pos(int x,int y,int z){return new PhysicalTopology.Pos(x,y,z);}
    static PhysicalTopology.Device device(UUID id,PhysicalTopology.Type t,PhysicalTopology.Pos p,int c,UUID parent,UUID patch){return new PhysicalTopology.Device(id,t,t.name(),t==SWITCH?"odc:switch_pro":"test",p,0,c,-1,true,parent,0,0,patch,3);}
    static void cell(PhysicalTopology t,PhysicalTopology.Pos p,PhysicalTopology.Kind k,PhysicalTopology.Device... d){t.put(p,new PhysicalTopology.Cell(k,Arrays.asList(d)));}
    static void topology(){PhysicalTopology t=new PhysicalTopology();LogicalLinks ll=new LogicalLinks();UUID a=id(),ta=id(),tb=id(),sa=id(),sb=id(),x=id(),y=id(),px=id(),py=id();
        PhysicalTopology.Pos pa=pos(0,0,0),pfa=pos(1,0,0),pfb=pos(-1,0,0),pta=pos(2,0,0),ptb=pos(-2,0,0),pc=pos(3,0,0),pd=pos(-3,0,0),psa=pos(4,0,0),psb=pos(-4,0,0);
        cell(t,pa,PhysicalTopology.Kind.ANTENNA,device(a,SATELLITE,pa,1500,null,null));cell(t,pfa,PhysicalTopology.Kind.FIBER);cell(t,pfb,PhysicalTopology.Kind.FIBER);cell(t,pta,PhysicalTopology.Kind.TERMINAL,device(ta,TERMINAL,pta,10000,null,null));cell(t,ptb,PhysicalTopology.Kind.TERMINAL,device(tb,TERMINAL,ptb,10000,null,null));cell(t,pc,PhysicalTopology.Kind.DATA);cell(t,pd,PhysicalTopology.Kind.DATA);cell(t,psa,PhysicalTopology.Kind.RACK,device(sa,SWITCH,psa,2000,null,null),device(x,SERVER,psa,10000,sa,px));cell(t,psb,PhysicalTopology.Kind.RACK,device(sb,SWITCH,psb,2000,null,null),device(y,SERVER,psb,10000,sb,py));ll.add(ta,sa,3);ll.add(tb,sb,3);yes(t.lanDomain(pta)!=t.lanDomain(ptb));
        BandwidthCache cache=new BandwidthCache();List<BandwidthEngine.Request> req=Arrays.asList(wan("x",x,1000),wan("y",y,1000));BandwidthEngine.Result r=cache.get(t,ll,0,p->true,req);eq(served(r,"x"),750);eq(served(r,"y"),750);eq(use(r,a),1500);yes(r==cache.get(t,ll,0,p->true,req));valid(r);
        BandwidthEngine.Result smaller=cache.get(t,ll,0,p->true,Collections.singletonList(wan("x",x,500)));eq(smaller.wan,500);yes(smaller!=r);
        r=cache.get(t,ll,0,p->true,req);eq(r.wan,1500);
        r=cache.get(t,ll,1,p->!p.equals(pc),req);eq(served(r,"x"),0);eq(served(r,"y"),1000);valid(r);
        r=cache.get(t,ll,2,p->true,req);eq(served(r,"x"),750);ll.remove(ta,sa);r=cache.get(t,ll,2,p->true,req);eq(served(r,"x"),0);eq(served(r,"y"),1000);valid(r);
    }
    static void randomized(){Random rnd=new Random(5129);for(int iteration=0;iteration<160;iteration++){Graph g=new Graph();UUID[] a=new UUID[3],t=new UUID[3],sw=new UUID[4];for(int i=0;i<a.length;i++)a[i]=g.node(RADIO,100+rnd.nextInt(2000));for(int i=0;i<t.length;i++){t[i]=g.node(TERMINAL,100+rnd.nextInt(3000));for(UUID src:a)if(rnd.nextBoolean())g.edge(src,t[i],FIBER,10000);}for(int i=0;i<sw.length;i++){sw[i]=g.node(SWITCH,100+rnd.nextInt(2000));for(UUID term:t)if(rnd.nextBoolean())g.edge(term,sw[i],UPLINK,100+rnd.nextInt(1500));}List<BandwidthEngine.Request> req=new ArrayList<>();for(int i=0;i<12;i++){UUID x=server(g,sw[i%4],100+rnd.nextInt(2000),100+rnd.nextInt(2000),100+rnd.nextInt(1000));req.add(wan("r"+i,x,rnd.nextInt(1200)));}BandwidthEngine.Result r=new BandwidthEngine(g.n,g.e).solve(req);valid(r);Collections.shuffle(g.n,rnd);Collections.shuffle(g.e,rnd);Collections.shuffle(req,rnd);BandwidthEngine.Result other=new BandwidthEngine(g.n,g.e).solve(req);for(String k:r.requests.keySet())eq(served(r,k),served(other,k));for(UUID u:r.devices.keySet())eq(use(r,u),use(other,u));}}
    static void large(){Graph g=new Graph();UUID[] t=new UUID[12];for(int i=0;i<t.length;i++)t[i]=terminal(g,g.node(TOWER,10000),10000);List<BandwidthEngine.Request> requests=new ArrayList<>();for(int i=0;i<40;i++){UUID sw=g.node(SWITCH,2000);g.edge(t[i%12],sw,UPLINK,10000);g.edge(t[(i+1)%12],sw,UPLINK,10000);for(int j=0;j<8;j++){UUID s=server(g,sw,1000,1000,1000);requests.add(wan(i+":"+j,s,150));}}BandwidthEngine.Result r=new BandwidthEngine(g.n,g.e).solve(requests);eq(r.wan,48000);valid(r);
        List<BandwidthEngine.Request> congested=new ArrayList<>();for(BandwidthEngine.Request q:requests)congested.add(wan(q.id,q.server,400));r=new BandwidthEngine(g.n,g.e).solve(congested);eq(r.wan,80000);for(BandwidthEngine.Allocation q:r.requests.values())eq(q.served,250);valid(r);}

    public static void main(String[] args){basic();equalSplit();shared();multipleSources();limitsAndLan();topology();randomized();large();System.out.println("Bandwidth engine PASS: "+checks+" checks");}
}
