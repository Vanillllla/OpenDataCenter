package odc;
import java.util.*;
public final class PowerStatsTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static PowerStats.Sample sample(long tick,long cap,long draw){return new PowerStats.Sample(tick,cap,1000,new long[]{draw,0,0,0,0,0},GridProtection.ON,true,false,true);}
    public static void main(String[] args){
        long[] categories={500,30,20,150,30,10};
        PowerStats.Sample s=new PowerStats.Sample(100,1000,2000,categories,GridProtection.ON,true,false,true);
        check(s.draw==740&&s.available==1000&&s.rated==2000&&PowerStats.free(s.available,s.draw)==260,"actual category total and free supply");
        categories[0]=0;check(s.categories[0]==500,"immutable sample input");
        check(PowerStats.free(0,100)==0,"nonnegative free capacity");
        check(new PowerStats.Sample(100,5000,5000,new long[]{30},GridProtection.TRIPPED,true,false,false).draw==0,"tripped no draw");
        check(new PowerStats.Sample(100,5000,5000,new long[]{30},GridProtection.OFF,true,false,false).available==0,"off no supply");
        check(new PowerStats.Sample(100,0,25000,new long[]{30},GridProtection.ON,true,false,false).draw==0,"unfunded demand not counted");
        check(new PowerStats.Sample(100,5000,5000,new long[]{30},GridProtection.ON,false,false,true).available==0,"unknown topology no total");
        PowerStats.Sample bypass=new PowerStats.Sample(100,5000,5000,new long[]{30},GridProtection.ON,true,true,true);
        check(bypass.available==0&&bypass.draw==30,"bypass actual draw not free power");
        UUID a=PowerStats.key(0,Arrays.asList(1L,2L,3L)),b=PowerStats.key(0,Arrays.asList(4L,5L));
        check(a.equals(PowerStats.key(0,Arrays.asList(3L,1L,2L,1L))),"stable unique footprint");
        check(!a.equals(b)&&!a.equals(PowerStats.key(1,Arrays.asList(1L,2L,3L))),"independent grid/dimension");
        check(!a.equals(PowerStats.key(0,Arrays.asList(1L,2L,3L,4L,5L))),"merge has new identity");
        check(PowerStats.selected(Arrays.asList(a,b),b)==1&&PowerStats.selected(Arrays.asList(a,b),UUID.randomUUID())==0,"selection and stale fallback");
        check(PowerStats.selected(Collections.emptyList(),a)==-1,"no networks");
        check(PowerStats.step(0,-1,2)==0&&PowerStats.step(0,1,2)==1&&PowerStats.step(1,1,2)==1,"bounded selection");
        PowerStats.History h=new PowerStats.History(),other=new PowerStats.History();
        for(int i=0;i<=130;i++)h.record(sample(i*100,1000,i));
        check(h.points(13000).size()==120&&h.points(13000).get(0).tick==1100,"ten minute retention");
        check(h.latest.draw==130,"latest actual");
        h.record(sample(13020,1000,300));check(h.latest.draw==300&&h.points(13020).size()==120,"one second latest independent from five second history");
        h.record(sample(13100,1000,500));h.record(sample(13100,1000,600));
        check(h.points(13100).size()==120&&h.points(13100).get(119).draw==600,"same tick replacement");
        other.record(sample(13100,200,100));check(other.latest.available==200&&h.latest.available==1000,"independent histories");
        h.record(sample(0,50,10));check(h.points(0).size()==1,"clock rollback clears future history");
        check(other.points(25100).isEmpty(),"expired history removed");
        System.out.println("PowerStats: "+checks+" checks passed");
    }
}
