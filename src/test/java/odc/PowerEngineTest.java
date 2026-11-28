package odc;
import java.util.*;
public final class PowerEngineTest {
    private static int count;
    private static void check(boolean b,String label){count++;if(!b)throw new AssertionError(label);}
    public static void main(String[] args){
        check(PowerEngine.energyUnits(1,1)==1,"exact watt-tick");
        check(PowerEngine.joules(PowerEngine.energyUnits(1000,1))==50,"1000W / 20TPS");
        check(PowerEngine.joules(PowerEngine.energyUnits(1000,20))==1000,"one second");
        PowerEngine.Budget a=new PowerEngine.Budget(PowerEngine.sum(10,20),20);
        check(a.use(5),"base load");
        PowerEngine.Preview p=new PowerEngine.Preview();
        check(p.use(a,20),"first rack");
        check(!p.use(a,6),"second rack shares same supply");
        check(p.use(a,5),"exact capacity");
        check(a.remainingWatts()==25,"preview must not consume");
        check(a.use(20)&&a.use(5)&&!a.use(1),"shared committed capacity");
        check(a.usedUnits()==600,"exact committed energy");
        PowerEngine.Budget b=new PowerEngine.Budget(10,20);
        check(b.use(10)&&a.remainingWatts()==0,"independent network");
        check(!new PowerEngine.Budget(0,20).use(1),"no source");
        check(new PowerEngine.Budget(2L*Integer.MAX_VALUE,20).use(Integer.MAX_VALUE),"large sums");
        Map<Integer,List<Integer>> edges=new HashMap<>();
        edges.put(0,Arrays.asList(1,2));edges.put(1,Arrays.asList(0,3));edges.put(2,Arrays.asList(0,3));edges.put(3,Arrays.asList(1,2,4));edges.put(4,Arrays.asList(3,5));edges.put(5,Arrays.asList(4));
        Set<Integer> on=TopologySearch.component(0,edges::get,x->true,16);
        check(on.size()==6,"loops do not duplicate nodes");
        long supply=0;for(int x:on)if(x==0||x==5)supply+=10;
        check(supply==20,"joined sources");
        Set<Integer> off=TopologySearch.component(0,edges::get,x->x!=4,16);
        check(off.size()==4&&!off.contains(5),"open relay separates domains");
        check(TopologySearch.component(0,edges::get,x->true,3)==null,"oversize fails closed");
        System.out.println("PowerEngine: "+count+" checks passed");
    }
}
