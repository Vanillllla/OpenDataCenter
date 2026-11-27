package odc;

import java.util.*;

/** Dependency-free regression suite for the exact engine invoked by ClusterScheduler. */
public final class ComputeEngineTest {
    private static int assertions;
    private static void check(boolean ok,String description){assertions++;if(!ok)throw new AssertionError(description);}
    private static void eq(long expected,long actual,String description){check(expected==actual,description+": expected="+expected+", actual="+actual);}
    private static ComputeEngine.Profile profile(int task,int cpu,int basic,int pro){return ComputeEngine.profile(task,cpu,basic,pro,20);}
    public static void main(String[] args){
        // Resource specialisation: CPU-only bank, mining cannot borrow CPU, AI cannot sum small VRAM cards.
        ComputeEngine.Profile mining=profile(1,2,0,1),bank=profile(2,2,0,0),ai=profile(3,2,0,1);
        eq(0,profile(1,2,0,0).work,"CPU cannot mine");
        eq(0,profile(3,2,4,0).work,"four 4 GB cards are not a single 16 GB card");
        eq(160,mining.work,"T2 GPU H/s");eq(4,mining.mbps,"T2 mining bandwidth");eq(98,mining.fePerTick,"T2 mining power");
        eq(160,bank.work,"T2 CPU CU/s");eq(16,bank.mbps,"bank bandwidth");eq(28,bank.fePerTick,"bank does not use GPUs");
        eq(bank.fePerTick,profile(2,2,0,4).fePerTick,"bank GPU load zero");
        eq(16,ai.work,"AI eligible VRAM");eq(64,ai.mbps,"AI bandwidth");eq(103,ai.fePerTick,"AI GPU and support CPU load");
        eq(160000,mining.reward(mining.mbps),"mining micro-NTC per batch");eq(160000,bank.reward(bank.mbps),"bank micro-NTC per batch");eq(240000,ai.reward(ai.mbps),"AI micro-NTC per batch");
        eq(0,profile(1,3,0,1).work,"invalid CPU");eq(0,profile(3,1,4,1).work,"invalid GPU count");
        eq(640,profile(1,2,0,4).work,"4U maximum mining");
        eq(0,ComputeEngine.networkCapacity(0,64),"no station");eq(0,ComputeEngine.networkCapacity(128,0),"no switch");eq(64,ComputeEngine.networkCapacity(128,64),"LAN bottleneck");eq(128,ComputeEngine.networkCapacity(128,256),"WAN bottleneck");
        int[] a=ComputeEngine.allocate(new int[]{1,1,1},1,0),b=ComputeEngine.allocate(new int[]{1,1,1},1,1),c=ComputeEngine.allocate(new int[]{1,1,1},1,2);
        eq(1,a[0],"tie first");eq(1,b[1],"tie rotates");eq(1,c[2],"tie rotates again");
        ComputeEngine.Profile[] jobs={mining,bank,ai};int[] rackOf={0,0,1};
        ComputeEngine.Plan p=ComputeEngine.plan(jobs,rackOf,new int[]{12000,12000},64,0);
        eq(64,Arrays.stream(p.bandwidth).sum(),"three simultaneous jobs share 64 Mbps");
        check(p.bandwidth[0]>0&&p.bandwidth[1]>0&&p.bandwidth[2]>0,"all three get bandwidth");
        check(p.bandwidth[2]<ai.mbps,"AI throttles on small network");
        p=ComputeEngine.plan(jobs,rackOf,new int[]{12000,12000},128,0);
        check(Arrays.equals(new int[]{4,16,64},p.bandwidth),"second switch removes bottleneck");
        p=ComputeEngine.plan(jobs,rackOf,new int[]{0,12000},128,0);
        check(Arrays.equals(new int[]{0,0,64},p.bandwidth),"rack without energy earns no work");
        p=ComputeEngine.plan(jobs,rackOf,new int[]{12000,12000},0,0);
        eq(0,Arrays.stream(p.energyCost).sum(),"no network no consumption");eq(0,Arrays.stream(p.bandwidth).sum(),"no network no work");
        p=ComputeEngine.plan(new ComputeEngine.Profile[]{bank,bank,bank,bank},new int[]{0,0,0,0},new int[]{35},64,0);
        eq(1,Arrays.stream(p.bandwidth).sum(),"sub-slot energy residue runs one server rather than zero");
        // Randomised combined allocation, testing actual production plan and rounding invariants.
        Random r=new Random(20261124L);
        for(int iter=0;iter<12000;iter++){
            int n=1+r.nextInt(24),racks=1+r.nextInt(8),cap=r.nextInt(1024);
            ComputeEngine.Profile[] ps=new ComputeEngine.Profile[n];int[] owners=new int[n],budgets=new int[racks],costs=new int[racks];
            for(int j=0;j<racks;j++)budgets[j]=r.nextInt(12001);
            for(int j=0;j<n;j++){int pro=r.nextInt(5),basic=r.nextInt(5-pro);ps[j]=profile(1+r.nextInt(3),1+r.nextInt(2),basic,pro);owners[j]=r.nextInt(racks);}
            ComputeEngine.Plan result=ComputeEngine.plan(ps,owners,budgets,cap,iter);long used=0,totalPossible=0;
            for(int j=0;j<n;j++){
                int bw=result.bandwidth[j],cost=result.energyCost[j];check(bw>=0&&bw<=ps[j].mbps,"request bandwidth range");check(bw<=result.energyLimitedBandwidth[j],"energy cap respected");eq(ps[j].energy(bw),cost,"cost formula");
                check(ps[j].reward(bw)<=ps[j].microMilliPerSecond,"reward no greater than full load");if(bw==0)eq(0,ps[j].reward(bw),"no work no reward");
                used+=bw;totalPossible+=result.energyLimitedBandwidth[j];costs[owners[j]]+=cost;
            }
            eq(Math.min(cap,totalPossible),used,"network not overbooked and no available bandwidth wasted");for(int j=0;j<racks;j++)check(costs[j]<=budgets[j],"rack FE budget respected");
        }
        // Exhaust all valid four-bay layouts and candidate replacements; compare to a boolean occupancy model.
        int[] sizes={0,1,2,4};int validLayouts=0;
        for(int aa:sizes)for(int bb:sizes)for(int cc:sizes)for(int dd:sizes){int[] slots={aa,bb,cc,dd};boolean[] occupied=new boolean[4];boolean valid=true;
            for(int i=0;i<4;i++)if(slots[i]>0){if(i+slots[i]>4){valid=false;break;}for(int j=i;j<i+slots[i];j++){if(occupied[j])valid=false;occupied[j]=true;}}
            if(!valid)continue;validLayouts++;
            for(int i=0;i<4;i++){int start=RackLayout.startAt(slots,i);check((start>=0)==occupied[i],"layout occupancy");if(start>=0)check(start<=i&&start+slots[start]>i,"valid bay owner");
                for(int u:new int[]{1,2,4}){boolean expected=i+u<=4;for(int k=0;k<4;k++)if(k!=i&&slots[k]>0&&i<k+slots[k]&&k<i+u)expected=false;
                    check(expected==RackLayout.canInsert(slots,i,u),"insertion/overlap");}}
        }
        check(validLayouts>0,"enumerated layouts");
        // Network graph: a physical cable loop must not duplicate devices; removing the switch splits the graph.
        Map<String,List<String>> graph=new HashMap<>();graph.put("PC",Arrays.asList("CableA","CableB"));graph.put("CableA",Arrays.asList("PC","CableB","Switch"));graph.put("CableB",Arrays.asList("PC","CableA"));graph.put("Switch",Arrays.asList("CableA","Rack","Station"));graph.put("Rack",Arrays.asList("Switch"));graph.put("Station",Arrays.asList("Switch"));
        Set<String> full=TopologySearch.component("PC",graph::get,n->true,6);eq(6,full.size(),"loop deduplicated");
        Set<String> split=TopologySearch.component("PC",graph::get,n->!n.equals("Switch"),6);eq(3,split.size(),"offline switch severs path");check(!split.contains("Station"),"unreachable uplink");
        check(TopologySearch.component("PC",graph::get,n->true,5)==null,"overflow fails closed");
        eq(0,TopologySearch.component("PC",graph::get,n->false,6).size(),"offline origin");
        System.out.println("PASS: "+assertions+" assertions; 12000 random compute plans; "+validLayouts+" valid rack layouts; topology loops/overflow.");
    }
}
