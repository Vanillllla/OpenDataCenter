package odc;
import java.util.*;
import static odc.EventJournal.*;
public final class EventJournalTest {
    static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("EventJournal check "+checks);}
    static UUID id(int n){return new UUID(0,n);}
    static Entry add(EventJournal j,long t,UUID owner,UUID scope,String code,Level level){return j.add(t,owner,scope,id(10),level,Category.TASK,Target.TASK,code,"task "+code,-1,0);}
    public static void main(String[] args){UUID o=id(1),a=id(2),b=id(3),other=id(4);EventJournal j=new EventJournal(256);
        Entry e=add(j,1,o,a,"fault",Level.ERROR);check(add(j,2,o,a,"fault",Level.ERROR).id.equals(e.id));check(e.repeats==2&&e.first==1&&e.last==2);check(!add(j,63,o,a,"fault",Level.ERROR).id.equals(e.id));check(!add(j,64,other,a,"fault",Level.ERROR).id.equals(e.id));check(j.recent(o,Arrays.asList(a,a)).size()==2);check(j.recent(other,Collections.singleton(a)).size()==1);
        Entry payment=j.add(65,o,b,id(20),Level.SUCCESS,Category.FINANCE,Target.TASK,"payment","Payment credited",1,30);check(j.add(80,o,b,id(20),Level.SUCCESS,Category.FINANCE,Target.TASK,"payment","Payment credited",1,70)==payment);check(payment.amount==100&&payment.repeats==2);check(j.add(120,o,b,id(20),Level.SUCCESS,Category.FINANCE,Target.TASK,"payment","Payment credited",1,5)!=payment);check(j.recent(o,Collections.singleton(a)).stream().noneMatch(x->x.category==Category.FINANCE));check(j.recent(o,Arrays.asList(a,b)).size()==4);
        List<Entry> f=filter(j.recent(o,Arrays.asList(a,b)),"PAYMENT","FINANCE","SUCCESS",true);check(f.size()==2&&f.get(0)==payment);check(filter(j.recent(o,Arrays.asList(a,b)),"fault","TASK","ERROR",false).size()==2);
        Query q=new Query("look|this","TASK","ERROR",true);check(Query.decode(q.encode()).encode().equals(q.encode()));check(Query.decode("oops").search.isEmpty());check(Query.decode("FAKE||1|test").category.isEmpty());
        EventJournal restored=new EventJournal(256);for(Entry x:j.all())restored.restore(x);check(restored.recent(o,Arrays.asList(a,b)).size()==4);check(add(restored,121,o,a,"start",Level.INFO).sequence>payment.sequence);
        j.move(o,b,a);check(j.recent(o,Collections.singleton(b)).isEmpty());check(j.recent(o,Collections.singleton(a)).size()==4&&payment.origin.equals(b)&&payment.scope.equals(a));
        j.configure(3);check(j.recent(o,Collections.singleton(a)).size()<=3);check(saturate(Long.MAX_VALUE,1)==Long.MAX_VALUE&&saturate(Long.MIN_VALUE,-1)==Long.MIN_VALUE);
        Random rand=new Random(542);for(int n=0;n<200;n++){j=new EventJournal(16+rand.nextInt(240));for(int i=0;i<400;i++){add(j,i,o,rand.nextBoolean()?a:b,"evt"+i,Level.INFO);check(j.recent(o,Arrays.asList(a,b)).size()<=j.limit());}List<Entry> before=j.recent(o,Arrays.asList(a,b));j.prune(o,Arrays.asList(a,b));check(j.all().size()==before.size());check(j.recent(o,Arrays.asList(a,b)).equals(before));Set<UUID> ids=new HashSet<>();for(Entry x:j.all())check(ids.add(x.id));check(j.recent(o,Collections.singleton(a)).stream().allMatch(x->x.scope.equals(a)));}
        System.out.println("EventJournal: "+checks+" checks passed");
    }
}
