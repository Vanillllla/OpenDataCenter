package odc;
import java.util.*;

/** Regression tests for the read model shared by Overview, Resources and server details. */
public final class DashboardStatsTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void invariants(DashboardStats.Totals t){for(int i=0;i<DashboardStats.SIZE;i++){check(t.total[i]==t.used[i]+t.free[i]+t.unavailable(i),"partition");check(t.used[i]>=0&&t.free[i]>=0&&t.unavailable(i)>=0,"nonnegative");check(t.offlineHeld[i]>=0&&t.offlineHeld[i]<=t.used[i],"retained offline subset");}}
    public static void main(String[] args){
        TaskEngine.Node a=FixedResourcesTest.node(1,0,FixedResourcesTest.POOL);TaskEngine.Task j=FixedResourcesTest.storage(1,a,0,64);
        List<PoolResources.Device> devices=PoolResources.catalog(a.id,a.parts);
        Map<String,Integer> held=DashboardStats.reservations(Collections.singleton(j));DashboardStats.Totals on=DashboardStats.inventory(devices,held,true,100);
        check(on.used[0]==2&&on.used[5]==64&&on.used[6]==40,"active reservations");check(on.free[6]==60,"port limit");invariants(on);
        DashboardStats.Totals off=DashboardStats.inventory(devices,held,false,0);check(off.used[0]==2&&off.offlineHeld[0]==2&&off.free[0]==0&&off.free[5]==0,"offline hardware is not free");invariants(off);
        j.enabled=false;held=DashboardStats.reservations(Collections.singleton(j));on=DashboardStats.inventory(devices,held,true,100);check(on.used[0]==0&&on.used[1]==0&&on.used[2]==0&&on.used[6]==0&&on.used[5]==64,"paused disk retention only");check(on.free[5]==on.total[5]-64,"retained disk not free");invariants(on);
        off=DashboardStats.inventory(devices,held,false,0);check(off.offlineHeld[5]==64&&off.used[5]==64&&off.free[5]==0,"offline disk retains data");invariants(off);
        Map<String,Long> candidate=new HashMap<>(),caps=new HashMap<>(),reserve=new HashMap<>();candidate.put("rack",900L);caps.put("rack",1000L);reserve.put("rack",400L);check(DashboardStats.networkFree(1000,700,candidate,caps,reserve)==300,"WAN shared with other pools");check(DashboardStats.networkFree(1000,200,candidate,caps,reserve)==600,"switch shared with other pools");check(DashboardStats.networkFree(0,200,candidate,caps,reserve)==0,"no WAN");
        check(DashboardStats.hourly(0,100000,true,1,1)==120000,"mining 120 periods/hour");check(DashboardStats.hourly(21,100000,true,1,1)==6000,"service 6 periods/hour");check(DashboardStats.hourly(9,100000,true,1,1)==0,"one-off excluded");check(DashboardStats.hourly(0,100000,false,1,1)==0,"paused excluded");check(DashboardStats.hourly(21,250000,true,1,2)==30000,"factor");
        Random random=new Random(543);DashboardStats.Totals cluster=new DashboardStats.Totals(),p1=new DashboardStats.Totals(),p2=new DashboardStats.Totals();for(int i=0;i<2000;i++){DashboardStats.Totals t=new DashboardStats.Totals();for(int k=0;k<7;k++){int cap=random.nextInt(10000);t.add(k,cap,random.nextInt(12000),random.nextBoolean());}invariants(t);cluster.add(t);(i%2==0?p1:p2).add(t);}invariants(cluster);p1.add(p2);for(int k=0;k<7;k++){check(p1.total[k]==cluster.total[k]&&p1.used[k]==cluster.used[k]&&p1.free[k]==cluster.free[k]&&p1.offlineHeld[k]==cluster.offlineHeld[k],"pool aggregation");}
        EventJournal log=new EventJournal(256);UUID owner=new UUID(0,1),scope=new UUID(0,2);for(EventJournal.Level level:EventJournal.Level.values())log.add(1,owner,scope,null,level,EventJournal.Category.SYSTEM,EventJournal.Target.NONE,level.name(),"event",-1,0);
        EventJournal.Query q=EventJournal.Query.decode(new EventJournal.Query("","","ERRORS",false).encode());check("ERRORS".equals(q.level),"combined error filter query");check(EventJournal.filter(log.recent(owner,Collections.singleton(scope)),q.search,q.category,q.level,q.oldest).size()==2,"combined errors only");
        System.out.println("DashboardStats: "+checks+" checks passed");
    }
}
