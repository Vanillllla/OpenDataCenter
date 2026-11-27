package odc;
import java.util.*;
import static odc.PhysicalTopology.Type.*;
import static odc.LogicalLinks.Kind.*;
/** Catalogue, port count and actual adapter/solver regression for all ten models. */
public final class SwitchLimitsTest {
    static int checks,seq;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("switch check "+checks);}
    static UUID id(){return new UUID(1,++seq);}
    static PhysicalTopology.Pos pos(int x){return new PhysicalTopology.Pos(x,0,0);}
    static PhysicalTopology.Device dev(UUID id,PhysicalTopology.Type type,String model,PhysicalTopology.Pos pos,int cap,UUID parent,int port,int tier){return new PhysicalTopology.Device(id,type,model,model,pos,0,cap,-1,true,parent,port,port,parent==null?null:id(),tier);}
    static void put(PhysicalTopology t,int x,PhysicalTopology.Kind k,PhysicalTopology.Device... d){t.put(pos(x),new PhysicalTopology.Cell(k,Arrays.asList(d)));}
    public static void main(String[] args){
        int[][] expected={{4,200,100},{8,1000,1000},{2,100,100},{2,500,1000},{2,1000,1000},{2,10000,10000},{4,1000,1000},{4,10000,10000},{8,10000,10000},{8,50000,10000}};
        check(SwitchLimits.count()==expected.length);Set<String> ids=new HashSet<>();
        for(int tier=1;tier<=expected.length;tier++){
            SwitchLimits.Model m=SwitchLimits.model(tier);int n=expected[tier-1][0],total=expected[tier-1][1],port=expected[tier-1][2];
            check(ids.add(m.id));check(SwitchLimits.tier("odc:"+m.id)==tier);check(SwitchLimits.ports(tier)==n);check(SwitchLimits.totalMbps(tier)==total);check(SwitchLimits.portMbps(tier)==port);check(SwitchLimits.power(tier)>0);check(total<=n*port);
            int[] ports=new int[8],cables=new int[8];for(int i=0;i<n;i++){check(LanPorts.canConnect(ports,n,i,i,3));ports[i]=i+1;cables[i]=3;check(!LanPorts.canConnect(ports,n,i,i,3));}check(!LanPorts.canConnect(ports,n,n,8,3));for(int i=0;i<n;i++){check(LanPorts.remove(ports,cables,i)==3);check(ports[i]==0);}
            for(int cable=1;cable<=3;cable++){
                PhysicalTopology t=new PhysicalTopology();LogicalLinks links=new LogicalLinks();UUID a=id(),term=id(),sw=id();
                put(t,0,PhysicalTopology.Kind.ANTENNA,dev(a,TOWER,"tower",pos(0),100000,null,-1,0));put(t,1,PhysicalTopology.Kind.FIBER);put(t,2,PhysicalTopology.Kind.TERMINAL,dev(term,TERMINAL,"terminal",pos(2),100000,null,-1,0));put(t,3,PhysicalTopology.Kind.DATA);
                List<PhysicalTopology.Device> ds=new ArrayList<>();ds.add(dev(sw,SWITCH,"odc:"+m.id,pos(4),total,null,-1,0));List<BandwidthEngine.Request> req=new ArrayList<>();
                for(int i=0;i<n;i++){UUID server=id();ds.add(dev(server,SERVER,"server",pos(4),100000,sw,i,cable));req.add(new BandwidthEngine.Request("s"+i,server,null,100000,100000));}
                UUID invalid=id();ds.add(dev(invalid,SERVER,"server",pos(4),100000,sw,n,3));req.add(new BandwidthEngine.Request("invalid",invalid,null,100000,100000));put(t,4,PhysicalTopology.Kind.RACK,ds.toArray(new PhysicalTopology.Device[0]));links.add(term,sw,3);
                BandwidthEngine.Result result=new BandwidthTopology(t,links.all(),p->true).engine().solve(req);double expectedTotal=Math.min(total,n*Math.min(port,LogicalLinks.speed(cable)));
                check(Math.abs(result.wan-expectedTotal)<.001);check(result.requests.get("invalid").served<.001);check(result.devices.get(sw).served<=total+.001);for(int i=0;i<n;i++)check(result.requests.get("s"+i).served<=Math.min(port,LogicalLinks.speed(cable))+.001);
            }
        }
        check(SwitchLimits.tier("odc:unknown")==0);check(SwitchLimits.ports(0)==0);check(SwitchLimits.totalMbps(999)==0);System.out.println("Switch catalogue PASS: "+checks+" checks");
    }
}
