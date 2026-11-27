package odc;
import java.util.*;
import static odc.PhysicalTopology.Type.*;
import static odc.PhysicalTopology.Kind.*;
/** Catalog, physical topology adapter and shared switching limits use the same specifications. */
public final class SwitchLimitsTest {
    static int checks,seq;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("switch check "+checks);}
    static UUID id(){return new UUID(0,++seq);}
    static PhysicalTopology.Pos pos(int x){return new PhysicalTopology.Pos(x,0,0);}
    static PhysicalTopology.Device device(UUID id,PhysicalTopology.Type t,String model,PhysicalTopology.Pos p,int cap,UUID parent,int port){return new PhysicalTopology.Device(id,t,t.name(),model,p,0,cap,0,true,parent,port,port,parent==null?null:id(),3);}
    static void cell(PhysicalTopology t,PhysicalTopology.Pos p,PhysicalTopology.Kind k,PhysicalTopology.Device... d){t.put(p,new PhysicalTopology.Cell(k,Arrays.asList(d)));}
    public static void main(String[] args){
        Set<String> expected=new HashSet<>(Arrays.asList("2:100","2:500","2:1000","2:10000","4:200","4:1000","4:10000","8:1000","8:10000","8:50000"));
        check(SwitchLimits.count()==10);check(SwitchLimits.tier(null)==0);check(SwitchLimits.tier("odc:unknown")==0);check(SwitchLimits.totalMbps(11)==0);
        for(int tier=1;tier<=SwitchLimits.count();tier++){
            int n=SwitchLimits.ports(tier),cap=SwitchLimits.totalMbps(tier),port=SwitchLimits.portMbps(tier);String model="odc:"+SwitchLimits.id(tier);
            check(expected.remove(n+":"+cap));check(SwitchLimits.tier(model)==tier);check(port>0&&port<=cap&&port*n>=cap);check(SwitchLimits.power(tier)>0);
            int[] ports=new int[8];for(int i=0;i<n;i++){check(LanPorts.canConnect(ports,n,i,i,3));ports[i]=i+1;check(!LanPorts.canConnect(ports,n,i,i,3));}check(!LanPorts.canConnect(ports,n,n,n,3));
            PhysicalTopology t=new PhysicalTopology();UUID a=id(),g=id(),s=id();LogicalLinks ll=new LogicalLinks();
            cell(t,pos(0),ANTENNA,device(a,TOWER,"tower",pos(0),100000,null,-1));cell(t,pos(1),FIBER);cell(t,pos(2),PhysicalTopology.Kind.TERMINAL,device(g,PhysicalTopology.Type.TERMINAL,"terminal",pos(2),100000,null,-1));cell(t,pos(3),DATA);
            List<PhysicalTopology.Device> rack=new ArrayList<>();rack.add(device(s,SWITCH,model,pos(4),cap,null,-1));List<BandwidthEngine.Request> req=new ArrayList<>();
            for(int i=0;i<=n;i++){UUID x=id();rack.add(device(x,SERVER,"server",pos(4),100000,s,i));req.add(new BandwidthEngine.Request("r"+i,x,null,100000,0));}
            t.put(pos(4),new PhysicalTopology.Cell(RACK,rack));ll.add(g,s,3);BandwidthEngine.Result r=new BandwidthTopology(t,ll.all(),p->true).engine().solve(req);
            check(Math.abs(r.devices.get(s).served-cap)<1e-4);check(Math.abs(r.wan-cap)<1e-4);check(r.requests.get("r"+n).served==0);
            for(int i=0;i<n;i++)check(Math.abs(r.requests.get("r"+i).served-cap/(double)n)<1e-4);
        }
        check(expected.isEmpty());System.out.println("PASS: "+checks+" switch catalog, port and bandwidth checks");
    }
}
