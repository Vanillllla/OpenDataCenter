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
        // Fluctuations apply to the whole nominal sum, with consistent rounding.
        for(int factor:new int[]{9000,10000,11000,0,20000}){
            PowerEngine.Budget scaled=new PowerEngine.Budget(1000,20,factor);
            PowerEngine.Preview trial=new PowerEngine.Preview();
            check(trial.use(scaled,30)&&trial.use(scaled,70),"preview");
            check(scaled.usedUnits()==0,"preview untouched");
            check(scaled.use(30)&&scaled.use(70),"commit");
            check(scaled.nominalWatts()==100&&scaled.usedUnits()==PowerEngine.scale(100,factor)*20,"actual sum");
            long[] cats=PowerEngine.scaleCategories(new long[]{31,32,37},factor);
            check(cats[0]+cats[1]+cats[2]==PowerEngine.scale(100,factor),"category sum");
        }
        PowerEngine.Budget high=new PowerEngine.Budget(100,20,11000);
        check(high.use(90)&&!high.canUse(2)&&!high.use(2),"upward excursion trips capacity");
        PowerEngine.Budget low=new PowerEngine.Budget(100,20,9000);
        check(low.use(110)&&low.usedUnits()==99*20,"downward excursion");
        PowerEngine.Budget round=new PowerEngine.Budget(11,20,11000);
        PowerEngine.Preview trial=new PowerEngine.Preview();
        for(int i=0;i<10;i++)check(trial.use(round,1)&&round.use(1),"cumulative rounding");
        check(round.usedUnits()==220&&!round.use(1),"exact total not per-item rounding");
        int previous=PowerEngine.factor(42,0,10);boolean changes=false,positive=false,negative=false;
        for(int cycle=1;cycle<1000;cycle++){
            int factor=PowerEngine.factor(42,cycle,10);
            check(factor>=9000&&factor<=11000,"range");
            check(factor==PowerEngine.factor(42,cycle,10)&&PowerEngine.factor(42,cycle,0)==10000,"deterministic and disabled");
            changes|=factor!=previous;positive|=factor>10000;negative|=factor<10000;previous=factor;
            long forecast=PowerEngine.scale(1000,PowerEngine.factor(42,cycle+1,10));
            PowerEngine.Budget next=new PowerEngine.Budget(2000,20,PowerEngine.factor(42,cycle+1,10));
            check(next.use(1000)&&next.usedUnits()/20==forecast,"next external receipt matches stable demand");
        }
        check(changes&&positive&&negative,"both directions");
        System.out.println("PowerEngine: "+count+" checks passed");
    }
}
