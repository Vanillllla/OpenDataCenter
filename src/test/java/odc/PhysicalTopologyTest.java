package odc;

import java.util.*;
import static odc.PhysicalTopology.Kind;
import static odc.PhysicalTopology.Type;

public final class PhysicalTopologyTest {
    static int checks;
    static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    static PhysicalTopology.Pos p(int x,int y,int z){return new PhysicalTopology.Pos(x,y,z);}
    static PhysicalTopology.Device d(int id,Type type,PhysicalTopology.Pos p,int cap,UUID parent){return new PhysicalTopology.Device(new UUID(0,id),type,type.name()+id,"Model",p,0,cap,-1,true,parent,0,0);}
    static void put(PhysicalTopology t,PhysicalTopology.Pos p,Kind k,PhysicalTopology.Device... d){t.put(p,new PhysicalTopology.Cell(k,Arrays.asList(d)));}
    public static void main(String[] args){
        PhysicalTopology t=new PhysicalTopology();PhysicalTopology.Pos ga=p(0,0,0),gb=p(6,0,0),a=p(3,0,2),r=p(0,0,-2),r2=p(6,0,-2);
        PhysicalTopology.Device g1=d(1,Type.TERMINAL,ga,1000,null),g2=d(2,Type.TERMINAL,gb,1000,null),ant=d(3,Type.RADIO,a,500,null);
        put(t,ga,Kind.TERMINAL,g1);put(t,gb,Kind.TERMINAL,g2);put(t,a,Kind.ANTENNA,ant);
        put(t,p(0,0,-1),Kind.DATA);put(t,p(6,0,-1),Kind.DATA);put(t,r,Kind.RACK);put(t,r2,Kind.RACK);
        for(int x=0;x<=6;x++)put(t,p(x,0,1),Kind.FIBER);
        PhysicalTopology.View v=t.view(ga,x->true);check(v.devices.size()==2,"empty rack invisible");check(v.links.size()==1&&v.links.get(0).type==PhysicalTopology.LinkType.FIBER,"automatic fiber");check(!v.devices.containsKey(g2.id),"shared fiber doesn't join LAN");check(t.view(gb,x->true).devices.containsKey(ant.id),"one antenna id shared");check(t.lanDomain(ga)!=t.lanDomain(gb),"separate LANs");
        PhysicalTopology.Device sw=d(4,Type.SWITCH,r,2000,null),srv=d(5,Type.SERVER,r,2500,sw.id);PhysicalTopology.Domain before=t.lanDomain(ga);long revision=t.revision();
        put(t,r,Kind.RACK,sw);v=t.view(ga,x->true);check(v.devices.size()==3&&v.links.size()==1,"switch detected without auto uplink");check(before==t.lanDomain(ga)&&t.revision()==revision,"metadata doesn't invalidate graph");
        put(t,r,Kind.RACK,sw,srv);v=t.view(ga,x->true);check(v.devices.size()==4&&v.links.size()==2,"patch discovered");check(v.links.stream().anyMatch(l->l.type==PhysicalTopology.LinkType.PATCH&&l.from.equals(sw.id)&&l.to.equals(srv.id)),"actual switch parent");
        v=t.view(ga,x->!x.equals(p(2,0,1)));check(v.links.stream().anyMatch(l->l.type==PhysicalTopology.LinkType.FIBER&&!l.verified),"unloaded fiber known but unverified");check(v.devices.get(ant.id).device.capacityMbps==500,"capacity retained");
        v=t.view(ga,x->!x.equals(r));check(!v.complete&&!v.devices.get(srv.id).available&&!v.devices.get(srv.id).loaded,"unloaded rack retained");check(!v.verifiedLanPositions.contains(r),"unverified rack not reachable");check(v.devices.get(srv.id).usedMbps==-1,"no stale utilization");check(v.links.stream().anyMatch(l->l.type==PhysicalTopology.LinkType.PATCH&&!l.verified),"unloaded patch retained");
        v=t.view(ga,x->true);check(v.devices.get(srv.id).available,"reload same UUID");
        for(int x=1;x<6;x++)put(t,p(x,0,0),Kind.DATA);v=t.view(ga,x->true);check(v.devices.size()==5&&v.links.size()==3,"LAN merge deduplicates antenna but keeps both fiber links");check(t.lanDomain(ga)==t.lanDomain(gb),"shared graph object");check(v.devices.get(ant.id).device.capacityMbps==500,"no multiplication of antenna capacity");
        t.put(p(3,0,0),null);check(t.lanDomain(ga)!=t.lanDomain(gb),"LAN split");check(t.view(ga,x->true).devices.containsKey(ant.id),"antenna survives split");
        t.put(p(2,0,1),null);check(!t.view(ga,x->true).devices.containsKey(ant.id),"fiber cut removes link");check(t.view(gb,x->true).devices.containsKey(ant.id),"other LAN unchanged");
        put(t,p(2,0,1),Kind.FIBER);check(t.view(ga,x->true).devices.containsKey(ant.id),"fiber restored same UUID");
        put(t,r,Kind.RACK,sw);check(!t.view(ga,x->true).devices.containsKey(srv.id),"unpatched server invisible");
        put(t,r,Kind.RACK,sw,srv);PhysicalTopology.Pos duplicate=p(0,0,-3);PhysicalTopology.Device copy=d(5,Type.SERVER,duplicate,2500,sw.id);put(t,duplicate,Kind.RACK,copy);v=t.view(ga,x->true);check(v.ambiguousIds.contains(srv.id)&&!v.devices.containsKey(srv.id),"duplicate UUID cannot become two devices");t.put(duplicate,null);
        // Fiber endpoints cannot be used as bridges between fiber runs.
        PhysicalTopology u=new PhysicalTopology();PhysicalTopology.Pos g=p(0,0,0),gNext=p(2,0,0),antenna=p(4,0,0);put(u,g,Kind.TERMINAL,d(10,Type.TERMINAL,g,1000,null));put(u,p(1,0,0),Kind.FIBER);put(u,gNext,Kind.TERMINAL,d(11,Type.TERMINAL,gNext,1000,null));put(u,p(3,0,0),Kind.FIBER);put(u,antenna,Kind.ANTENNA,d(12,Type.TOWER,antenna,10000,null));
        check(u.view(g,x->true).devices.size()==1,"gateway not fiber transit");check(u.view(gNext,x->true).devices.size()==2,"tower supported by schema");
        // Reverse optical discovery must include both terminals, but must not merge their LANs.
        List<PhysicalTopology.Peer> peers=t.opticalPeers(a,x->true);
        check(peers.size()==2&&peers.stream().allMatch(q->q.verified),"reverse peers on separate LANs");
        check(t.opticalPeers(a,x->!x.equals(p(2,0,1))).stream().filter(q->q.verified).count()==1,"reverse peer verification per branch");
        check(t.opticalPeers(a,x->false).stream().noneMatch(q->q.verified||q.loaded),"unloaded optical peers retained only");
        check(u.opticalPeers(antenna,x->true).size()==1,"tower only direct optical peers");
        check(t.opticalPeers(r,x->true).isEmpty(),"rack cannot act as optical endpoint");
        for(NetworkEquipment e:NetworkEquipment.values()){check(NetworkEquipment.byId(e.id)==e&&NetworkEquipment.byId("odc:"+e.id)==e,"catalog id lookup");check(e.watts>0&&e.mbps>0,"positive equipment ratings");}
        check(NetworkEquipment.RADIO.mbps==500&&NetworkEquipment.SATELLITE.mbps==2000&&NetworkEquipment.TOWER.mbps==10000,"source ratings");
        check(NetworkEquipment.TERMINAL_1.mbps==1000&&NetworkEquipment.TERMINAL_2.mbps==10000&&NetworkEquipment.TERMINAL_3.mbps==100000,"terminal ratings");
        check(NetworkEquipment.byId("invalid")==null,"unknown model");
        // Restore persisted cells without live chunks: identity and membership retained, no live capacity claim.
        PhysicalTopology restored=new PhysicalTopology();for(Map.Entry<PhysicalTopology.Pos,PhysicalTopology.Cell> e:t.cells().entrySet())restored.put(e.getKey(),e.getValue());
        check(restored.view(ga,x->false).devices.keySet().equals(t.view(ga,x->true).devices.keySet()),"saved index membership");check(restored.view(ga,x->false).devices.values().stream().noneMatch(s->s.available),"no live availability after restore");
        // Random line cut/reconnect compares graph to independent expected connectivity.
        Random rng=new Random(6131);PhysicalTopology line=new PhysicalTopology();int n=512;boolean[] present=new boolean[n];for(int i=0;i<n;i++){present[i]=true;put(line,p(i,5,0),Kind.DATA);}
        for(int k=0;k<2000;k++){int i=rng.nextInt(n);present[i]=!present[i];if(present[i])put(line,p(i,5,0),Kind.DATA);else line.put(p(i,5,0),null);int start=rng.nextInt(n);PhysicalTopology.Domain actual=line.lanDomain(p(start,5,0));if(!present[start]){check(actual==null,"absent cell");continue;}int low=start,high=start;while(low>0&&present[low-1])low--;while(high+1<n&&present[high+1])high++;check(actual!=null&&actual.positions.size()==high-low+1,"exact line component");for(int j=low;j<=high;j++)check(actual.positions.contains(p(j,5,0)),"component member");}
        System.out.println("PhysicalTopologyTest PASS: "+checks+" checks");
    }
}
