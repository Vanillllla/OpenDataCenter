package odc;
import java.util.*;
public final class PersistentPowerTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(PersistentPower.mode(0,3)==PersistentPower.Mode.VIRTUAL,"source loaded does not count as consumer");
        check(PersistentPower.mode(1,3)==PersistentPower.Mode.PARTIAL,"mixed consumers pause");
        check(PersistentPower.mode(3,3)==PersistentPower.Mode.LIVE,"live authority");
        check(PersistentPower.demand(30,70)==100,"fixed plus dynamic load");
        check(PersistentPower.externalDemand(100,40)==60,"creative covers first");
        check(PersistentPower.externalDemand(20,40)==0,"no unnecessary input");
        Object t=new Object();
        ExternalWindow first=new ExternalWindow(60);first.add(t,100);
        first.offer(t,1000,50,TransformerCore.Input.HE,false);
        check(!first.sufficient()&&!PersistentPower.funded(100,40,first.paidWatts(),true),"missing external energy");
        first.offer(t,200,50,TransformerCore.Input.FE,false);
        check(first.sufficient()&&PersistentPower.funded(100,40,first.paidWatts(),true),"actual mixed receipts");
        first.close();check(first.offer(t,1,50,TransformerCore.Input.FE,false)==0,"old receipt closed on transition");
        ExternalWindow next=new ExternalWindow(60);next.add(t,100);
        check(!PersistentPower.funded(100,40,next.paidWatts(),true),"new cycle cannot reuse receipt");
        check(!PersistentPower.funded(101,40,60,true),"growth requires new receipt");
        PowerEngine.Budget grid=new PowerEngine.Budget(100,20);
        check(grid.use(30)&&grid.use(70)&&!grid.use(1),"virtual fixed and compute use same capacity");
        GridProtection latch=new GridProtection();List<Long> g=Arrays.asList(1L,2L),other=Arrays.asList(3L,4L);
        latch.set(g,GridProtection.TRIPPED);
        check(latch.state(g)==GridProtection.TRIPPED&&latch.state(other)==GridProtection.ON,"independent failure");
        GridProtection reload=new GridProtection();reload.restore(latch.snapshot());
        check(reload.state(g)==GridProtection.TRIPPED,"failure persists");
        long a=PersistentPower.chunk(-1,0),b=PersistentPower.chunk(0,-1),c=PersistentPower.chunk(1,1);
        check(a!=b&&b!=c&&a!=c,"signed chunk coordinates");
        check(PersistentPower.bounded(Arrays.asList(a,a,b,c),2).equals(new LinkedHashSet<>(Arrays.asList(a,b))),"deduplicated bounded chunks");
        check(PersistentPower.bounded(Arrays.asList(a,b),0).isEmpty(),"chunk loading disabled");
        System.out.println("PersistentPower: "+checks+" checks passed");
    }
}
