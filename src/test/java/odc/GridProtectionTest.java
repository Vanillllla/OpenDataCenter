package odc;
import java.util.*;
public final class GridProtectionTest {
    static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    public static void main(String[] args){
        GridProtection p=new GridProtection();
        List<Long> a=Arrays.asList(1L,2L,3L),b=Arrays.asList(4L,5L),joined=Arrays.asList(1L,2L,3L,4L,5L);
        check(p.state(a)==GridProtection.ON,"new grid");
        check(GridProtection.sufficient(2000,2000)&&!GridProtection.sufficient(2000,2001),"exact limit");
        p.set(a,GridProtection.TRIPPED);
        check(p.state(a)==GridProtection.TRIPPED&&p.state(b)==GridProtection.ON,"independent grids");
        check(p.state(joined)==GridProtection.TRIPPED,"merge inherits trip");
        p.set(joined,p.state(joined));
        check(p.state(b)==GridProtection.TRIPPED,"split inherits trip");
        p.set(a,GridProtection.ON);
        check(p.state(a)==GridProtection.ON&&p.state(b)==GridProtection.TRIPPED,"independent reset after split");
        p.set(joined,p.state(joined));p.set(joined,GridProtection.ON);
        check(p.state(a)==GridProtection.ON&&p.state(b)==GridProtection.ON,"one reset whole grid");
        p.set(a,GridProtection.OFF);p.set(b,GridProtection.TRIPPED);
        check(p.state(joined)==GridProtection.TRIPPED,"trip stronger than manual off");
        GridProtection restored=new GridProtection();restored.restore(p.snapshot());
        check(restored.state(a)==GridProtection.OFF&&restored.state(b)==GridProtection.TRIPPED,"saved cell states");
        restored.forget(4L);check(restored.state(Collections.singleton(4L))==GridProtection.ON,"removed block clears cell only");
        check(restored.state(Collections.singleton(5L))==GridProtection.TRIPPED,"other cell remains latched");
        restored.set(joined,GridProtection.ON);
        if(!GridProtection.sufficient(100,101))restored.set(joined,GridProtection.TRIPPED);
        check(restored.state(a)==GridProtection.TRIPPED&&restored.state(b)==GridProtection.TRIPPED,"retrip after reset");
        PowerEngine.Budget budget=new PowerEngine.Budget(100,20);
        check(budget.use(30)&&budget.use(70)&&!budget.use(1),"cumulative demand triggers trip");
        check(!GridProtection.sufficient(0,1),"missing source");
        System.out.println("GridProtection: "+checks+" checks passed");
    }
}
