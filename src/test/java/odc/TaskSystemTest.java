package odc;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;

public class TaskSystemTest {
    static final UUID owner=UUID.randomUUID(),wallet=UUID.randomUUID();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TaskEngine.Result allocate(int type, TaskEngine.Node... nodes){
        TaskEngine.Task t=new TaskEngine.Task(0,type);t.enabled=true;
        return TaskEngine.plan(Arrays.asList(nodes),Collections.singletonList(t),new int[]{100000},100000,Collections.emptyMap(),Collections.emptySet()).tasks.get(0);
    }
    static int part(String name){return ServerSpecTest.model(name);}
    static TaskEngine.Node node(int[] p){return new TaskEngine.Node(UUID.randomUUID(),0,100000,p);}
    static void hardware(){
        int[] p=ServerSpecTest.minimal();check(allocate(0,node(p)).units==2,"budget CPU");
        p[0]=part("ASBrick Rack EP2C612 WS");p[1]=p[ServerSpec.CPU2]=part("InTroll Zeon 2696 v4");
        p[ServerSpec.RAM]=p[ServerSpec.RAM+1]=part("Susung Enterprise 64");
        p[ServerSpec.SATA]=part("Susung Enterprise 8");p[ServerSpec.NVME]=part("Susung 1200 Titan");
        p[ServerSpec.PSU]=p[ServerSpec.PSU+1]=part("SuperMacro 1500 Titanium");
        p[ServerSpec.PCIE]=part("NoVideo Teslo M10");
        p[ServerSpec.PCIE+2]=p[ServerSpec.PCIE+3]=part("NoVideo Teslo P4");
        p[ServerSpec.PCIE+4]=part("Melonox Connect 100");
        check(ServerSpec.cpuCores(p)==88 && ServerSpec.state(4,p)==0,"dual CPU");
        TaskEngine.Node multi=node(p);check(allocate(1,multi).units==6 && allocate(15,multi).units==0,"independent GPU memory");
        check(allocate(10,multi,node(p)).units==2*allocate(10,multi).units,"distributed score");
        TaskEngine.Task a=new TaskEngine.Task(1,1),b=new TaskEngine.Task(2,1);a.enabled=b.enabled=true;
        TaskEngine.Plan plan=TaskEngine.plan(Collections.singletonList(multi),Arrays.asList(a,b),new int[]{1000},1000,Collections.emptyMap(),Collections.emptySet());
        check(plan.tasks.get(1).units+plan.tasks.get(2).units==6,"exclusive reservation");
        p[0]=part("GigaBite SP6 Titan");p[1]=part("AMDead Epyx 9654");p[ServerSpec.CPU2]=0;
        p[ServerSpec.RAM]=part("Susung Enterprise 128");p[ServerSpec.RAM+1]=0;
        p[ServerSpec.PCIE]=part("NoVideo H200X");p[ServerSpec.PCIE+1]=p[ServerSpec.PCIE+2]=p[ServerSpec.PCIE+3]=0;
        check(allocate(15,node(p)).units==1,"AI server");
    }
    static TaskData.Job accept(TaskData d,int rule,long now){
        NBTTagCompound offer=d.market.offer(9,rule,1,now);
        ContractMarket.list(d.market.owner(owner),"Offers").appendTag(offer);
        UUID key=offer.getUniqueId("Key");long before=d.wallet(wallet)[0];
        check(d.market.accept(d,owner,wallet,key,now,PoolResources.id(owner,"GPU Pool"),"GPU Pool"),"accept");
        check(!d.market.accept(d,owner,wallet,key,now,PoolResources.id(owner,"GPU Pool"),"GPU Pool") && d.wallet(wallet)[0]==before-offer.getLong("Fee"),"atomic fee");
        TaskData.Job job=d.jobs.get(d.jobs.size()-1);check(job.pool.equals(PoolResources.id(owner,"GPU Pool"))&&job.poolLabel.equals("GPU Pool")&&!job.auto,"pool assignment");return job;
    }
    static void contracts(){
        long now=System.currentTimeMillis();
        for(int rule=0;rule<8;rule++){
            TaskData d=new TaskData();d.wallet(wallet)[0]=1000000;TaskData.Job j=accept(d,rule,now);
            j.enabled=true;j.cachedRate=j.terms.getLong("Goal")*TaskEngine.SCALE;j.lastAccounting=now;
            d.settle(j,now+1000);long paid=d.wallet(wallet)[0];d.settle(j,now+2000);
            check(j.terms.hasKey("End") && paid==d.wallet(wallet)[0],"single completion");
            j=accept(d,rule,now);
            if(ContractMarket.TIMED[rule]){long deadline=j.terms.getLong("Deadline");TaskData saved=new TaskData();saved.readFromNBT(d.writeToNBT(new NBTTagCompound()));saved.market.tick(saved,deadline+1);check(saved.jobs.stream().allMatch(k->k.terms.hasKey("End")),"deadline after reload");}
            else if(ContractMarket.CONTINUOUS[rule]){j.terms.setLong("Start",now);d.elapsedMillis=121000;d.market.tick(d,now+121000);check(j.terms.hasKey("End"),"cumulative downtime");}
            else{d.market.end(d,j,"cancelled",0);check(j.terms.hasKey("End"),"cancel");}
        }
        TaskData d=new TaskData();d.market.supply(owner,now);NBTTagCompound stored=d.writeToNBT(new NBTTagCompound());TaskData loaded=new TaskData();loaded.readFromNBT(stored);
        loaded.market.supply(owner,now+1);check(loaded.market.data.equals(d.market.data),"stable offers");
        NBTTagCompound paid=d.market.offer(10,7,1,now);ContractMarket.list(d.market.owner(owner),"Offers").appendTag(paid);
        check(!d.market.accept(d,owner,wallet,paid.getUniqueId("Key"),now,PoolResources.id(owner,"GPU Pool"),"GPU Pool"),"insufficient funds");
    }
    static void services(){
        TaskData d=new TaskData();TaskData.Job j=new TaskData.Job(0,21,wallet,owner);d.jobs.add(j);
        UUID host=UUID.randomUUID();long now=System.currentTimeMillis();j.enabled=true;j.cachedRate=TaskEngine.SCALE;j.lastAccounting=now-600000;j.machines=Collections.singleton(host);
        d.settle(j,now);check(d.wallet(wallet)[0]==1000,"service interval");
        d.serverStopped(host,false);d.serverStopped(host,false);check(d.wallet(wallet)[0]==0 && d.market.owner(owner).getLong("Debt")==3000,"one penalty, missing payouts and debt");
        j.enabled=true;j.cachedRate=TaskEngine.SCALE;j.machines=Collections.singleton(host);d.maintenance(host,true);check(d.maintenance(host,false)>0,"notice");
        j.notice=now-1;d.serverStopped(host,true);check(d.wallet(wallet)[0]==0 && d.market.owner(owner).getLong("Debt")==3000,"planned stop");
    }
    static void reservations(){
        int[] parts=ServerSpecTest.minimal();parts[1]=part("AMDead Epyx 9654");parts[ServerSpec.RAM]=part("Susung Enterprise 128");
        parts[ServerSpec.SATA]=part("SeaBait Basic 256");
        TaskEngine.Node n=node(parts);
        TaskEngine.Task disk=new TaskEngine.Task(1,9);disk.enabled=true;
        TaskEngine.Plan p=TaskEngine.plan(Collections.singletonList(n),Collections.singletonList(disk),new int[]{10000},10000,Collections.emptyMap(),Collections.emptySet());
        check(p.tasks.get(1).units==2 && p.freeIO[0][ServerSpec.SATA]==0,"HDD bandwidth independent of capacity");
        disk.retained.putAll(p.tasks.get(1).placement);disk.enabled=false;
        p=TaskEngine.plan(Collections.singletonList(n),Collections.singletonList(disk),new int[]{10000},10000,Collections.emptyMap(),Collections.emptySet());
        check(p.disk[0]==128 && p.cpu[0]==0 && p.net[0]==0 && p.freeIO[0][ServerSpec.SATA]==100,"paused storage");
        TaskEngine.Task mining=new TaskEngine.Task(2,0);mining.enabled=true;
        p=TaskEngine.plan(Collections.singletonList(n),Collections.singletonList(mining),new int[]{1},1,Collections.emptyMap(),Collections.emptySet());
        check(p.tasks.get(2).units==1,"fractional Mbit network");
        check(TaskEngine.plan(Collections.singletonList(n),Collections.singletonList(mining),new int[]{1000},0,Collections.emptyMap(),Collections.emptySet()).tasks.get(2).units==0,"network loss");
        check(TaskEngine.plan(Collections.singletonList(n),Collections.singletonList(mining),new int[]{1000},1000,Collections.emptyMap(),Collections.singleton(0)).tasks.get(2).units==0,"power exclusion");
    }
    static void persistence(){
        long now=System.currentTimeMillis();
        TaskData d=new TaskData();TaskData.Job j=new TaskData.Job(1,1,wallet,owner);j.enabled=true;j.cachedRate=2*TaskEngine.SCALE;j.lastAccounting=now;
        j.bindings.add(new PoolResources.Binding(UUID.randomUUID(),PoolResources.Kind.CPU,ServerSpec.CPU,0,part("InTroll CoreX 4770"),1));d.jobs.add(j);d.settle(j,now+15000);check(d.wallet(wallet)[1]==0,"partial interval");
        TaskData restored=new TaskData();restored.readFromNBT(d.writeToNBT(new NBTTagCompound()));TaskData.Job saved=restored.jobs.get(0);
        restored.settle(saved,now+30000);check(restored.wallet(wallet)[1]==0,"no offline reward");saved.cachedRate=2*TaskEngine.SCALE;restored.settle(saved,now+45000);restored.settle(saved,now+45000);
        check(restored.wallet(wallet)[1]==2000,"checkpoint pays once after live resumption");
        d=new TaskData();d.market.supply(owner,now);Set<String> combos=new HashSet<>();
        for(int t=9;t<21;t++)for(int rule=0;rule<8;rule++)for(int size=0;size<5;size++){
            NBTTagCompound o=d.market.offer(t,rule,size,now);combos.add(o.getInteger("Type")+"/"+o.getInteger("Rule")+"/"+o.getInteger("Size"));
        }
        check(combos.size()==480,"contract variants");
    }
    static void poolPersistence(){TaskData d=new TaskData();UUID server=UUID.randomUUID();d.pools.put(server,"GPU Pool");TaskData.Job j=new TaskData.Job(0,0,wallet,owner);j.pool=d.poolId(owner,server);j.poolLabel="GPU Pool";j.bindings.add(new PoolResources.Binding(server,PoolResources.Kind.CPU,ServerSpec.CPU,0,1,1));d.jobs.add(j);TaskData copy=new TaskData();copy.readFromNBT(d.writeToNBT(new NBTTagCompound()));check(copy.poolName(server).equals("GPU Pool")&&copy.jobs.get(0).pool.equals(j.pool)&&copy.jobs.get(0).bindings.get(0).server.equals(server),"pool binding persistence");}
    public static void main(String[] args){poolPersistence();hardware();contracts();services();reservations();persistence();System.out.println("PASS task system");}
}
