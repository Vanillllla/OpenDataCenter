package odc;
import java.util.*;

/** Tests the production reservation engine, not the obsolete automatic allocator. */
public final class FixedResourcesTest {
    static int checks;static final UUID POOL=new UUID(0,9),OTHER=new UUID(0,10);
    static final int[] CAPS={1000,1000};static final Set<Integer> NONE=Collections.emptySet();
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static TaskEngine.Node node(int id,int rack,UUID pool){int[] p=ServerSpecTest.minimal();p[ServerSpec.BOARD]=ServerSpecTest.model("SuperMacro X79");p[ServerSpec.CPU]=ServerSpecTest.model("InTroll Zeon 2670 v2");p[ServerSpec.PCIE]=ServerSpecTest.model("NoVideo Teslo M10");p[ServerSpec.PCIE+1]=ServerSpecTest.model("FakeTek G1000");return new TaskEngine.Node(new UUID(0,id),rack,1000,p,pool);}
    static PoolResources.Device device(TaskEngine.Node n,PoolResources.Kind kind,int unit){return PoolResources.catalog(n.id,n.parts).stream().filter(d->d.kind==kind&&d.unit==unit).findFirst().get();}
    static void add(TaskEngine.Task j,TaskEngine.Node n,PoolResources.Kind k,int unit,int amount){j.bindings.add(FixedResources.binding(device(n,k,unit),amount));}
    static TaskEngine.Task mining(int id,TaskEngine.Node n,int core,int gpu){TaskEngine.Task j=new TaskEngine.Task(id,1);j.pool=n.pool;j.enabled=true;add(j,n,PoolResources.Kind.CPU,core,1);add(j,n,PoolResources.Kind.GPU,gpu,1);add(j,n,PoolResources.Kind.RAM,0,1);add(j,n,PoolResources.Kind.NETWORK,0,8);return j;}
    static TaskEngine.Task storage(int id,TaskEngine.Node n,int core,int gb){TaskEngine.Task j=new TaskEngine.Task(id,9);j.pool=n.pool;j.enabled=true;add(j,n,PoolResources.Kind.CPU,core,1);add(j,n,PoolResources.Kind.CPU,core+1,1);add(j,n,PoolResources.Kind.RAM,0,2);add(j,n,PoolResources.Kind.HDD,0,gb);add(j,n,PoolResources.Kind.NETWORK,0,40);return j;}
    static FixedResources.Ledger ledger(List<TaskEngine.Node> ns,List<TaskEngine.Task> jobs,TaskEngine.Task ignore){return new FixedResources.Ledger(ns,CAPS,1000,NONE,jobs,ignore);}
    static TaskEngine.Plan run(List<TaskEngine.Node> ns,List<TaskEngine.Task> jobs,Integer... ids){return FixedResources.plan(ns,jobs,new HashSet<>(Arrays.asList(ids)),CAPS,1000,NONE);}
    static void commit(TaskEngine.Task j,List<TaskEngine.Node> ns,List<TaskEngine.Task> jobs){FixedResources.Evaluation e=FixedResources.evaluate(j,ledger(ns,jobs,j),true);check(e.error.isEmpty(),"preview valid: "+e.error+" "+e.detail);j.bindings.clear();j.bindings.addAll(e.committed);}
    public static void main(String[] args){
        TaskEngine.Node a=node(1,0,POOL),b=node(2,1,POOL),c=node(3,1,OTHER);List<TaskEngine.Node> ns=Arrays.asList(a,b,c);
        TaskEngine.Task one=mining(1,a,0,0),two=mining(2,a,1,1);List<TaskEngine.Task> jobs=new ArrayList<>(Arrays.asList(one,two));commit(one,ns,Collections.singletonList(one));commit(two,ns,jobs);
        TaskEngine.Plan p=run(ns,jobs,1,2);check(p.tasks.get(1).units==1&&p.tasks.get(2).units==1,"two chips of same card independent");check(p.gpu[0]==3&&p.cpu[0]==2,"exact mask");check(p.tasks.get(1).rate==Math.round(1.5*(TaskEngine.SCALE+2500+1333+8000+20*1033)),"preserved score formula");
        TaskEngine.Task collision=mining(3,a,0,2);check(!FixedResources.evaluate(collision,ledger(ns,jobs,null),true).error.isEmpty(),"same core rejected");
        collision=mining(3,a,2,1);check(!FixedResources.evaluate(collision,ledger(ns,jobs,null),true).error.isEmpty(),"same GPU chip rejected");
        collision=mining(3,c,0,0);collision.pool=POOL;check(!FixedResources.evaluate(collision,ledger(ns,jobs,null),true).error.isEmpty(),"foreign pool rejected");
        List<PoolResources.Binding> draft=new ArrayList<>();FixedResources.Ledger l=ledger(ns,jobs,null);List<PoolResources.Device> family=l.family(POOL,device(a,PoolResources.Kind.GPU,0).family());check(l.max(POOL,family,draft)==6,"available matching GPUs in pool");check(l.adjust(POOL,family,draft,3)&&FixedResources.hosts(draft).equals(Collections.singleton(b.id)),"concentrated on host with most free chips");check(l.adjust(POOL,family,draft,1)&&draft.size()==1,"slider decrease");check(!l.adjust(POOL,family,draft,7)&&draft.size()==1,"invalid slider atomic");
        // Foreign reservation after a network split does not run here but still occupies actual hardware.
        p=run(Arrays.asList(a,b),jobs,2);check(!p.tasks.containsKey(1)&&p.tasks.get(2).units==1&&p.cpu[0]==2,"split reserves without duplicate compute");
        p=run(Arrays.asList(b,c),jobs,1,2);check(p.tasks.get(1).units==0&&p.gpu[0]==0,"no replacement when host lost");
        p=FixedResources.plan(ns,jobs,new HashSet<>(Arrays.asList(1,2)),CAPS,10,NONE);check(p.tasks.get(1).units==0&&p.tasks.get(2).units==0,"WAN shrink does not steal allocations");
        one.enabled=false;p=run(ns,jobs,1,2);check(p.cpu[0]==1&&p.gpu[0]==2,"pause releases CPU GPU");check(FixedResources.evaluate(mining(3,a,0,0),ledger(ns,jobs,null),true).error.isEmpty(),"released chips selectable");
        // Separate storage and I/O reservation, same drive may serve multiple jobs.
        TaskEngine.Task disk=storage(4,b,0,64);commit(disk,ns,Collections.singletonList(disk));check(disk.bindings.stream().filter(PoolResources.Binding::storage).mapToInt(x->x.io).sum()==50,"exact I/O reserved independent of GB");
        p=run(ns,Collections.singletonList(disk),4);check(p.tasks.get(4).units==1&&p.freeSpace[1][ServerSpec.SATA]==192&&p.freeIO[1][ServerSpec.SATA]==50,"disk and I/O accounted");
        disk.enabled=false;p=run(ns,Collections.singletonList(disk),4);check(p.disk[1]==64&&p.cpu[1]==0&&p.ram[1]==0&&p.net[1]==0&&p.freeIO[1][ServerSpec.SATA]==100,"pause retains data only");
        TaskEngine.Task tooBig=storage(5,b,0,200);check(!FixedResources.evaluate(tooBig,ledger(ns,Collections.singletonList(disk),null),true).error.isEmpty(),"retained disk unavailable");
        TaskEngine.Task next=storage(5,b,0,64);commit(next,ns,Arrays.asList(disk,next));p=run(ns,Arrays.asList(disk,next),4,5);check(p.tasks.get(5).units==1&&p.disk[1]==128,"reuse released throughput");
        // Same selection produces identical committed result independent of node enumeration.
        List<TaskEngine.Node> reverse=Arrays.asList(c,b,a);check(run(reverse,jobs,2).tasks.get(2).rate==run(ns,jobs,2).tasks.get(2).rate,"fixed bindings independent of order");
        TaskEngine.Task partial=mining(6,a,0,0);partial.bindings.removeIf(x->x.kind==PoolResources.Kind.RAM);add(partial,b,PoolResources.Kind.RAM,0,1);check(!FixedResources.evaluate(partial,ledger(ns,Collections.emptyList(),null),true).error.isEmpty(),"resources cannot mix across hosts for one unit");
        // Randomized exclusive core/chip reservations: a second candidate cannot commit over an existing one.
        Random random=new Random(53);for(int i=0;i<1000;i++){int core=random.nextInt(4),gpu=random.nextInt(4);TaskEngine.Task x=mining(7,a,core,gpu),y=mining(8,a,random.nextInt(4),random.nextInt(4));boolean expect=!FixedResources.key(x.bindings.get(0)).equals(FixedResources.key(y.bindings.get(0)))&&!FixedResources.key(x.bindings.get(1)).equals(FixedResources.key(y.bindings.get(1)));check(FixedResources.evaluate(y,ledger(ns,Collections.singletonList(x),null),true).error.isEmpty()==expect,"random exclusivity");}
        System.out.println("PASS fixed resources: "+checks+" assertions");
    }
}
