package odc;
import java.util.*;
public class PoolResourcesTest {
    static void check(boolean pass){if(!pass)throw new AssertionError();}
    public static void main(String[] args){
        UUID owner=UUID.randomUUID(),server=UUID.randomUUID(),other=UUID.randomUUID();
        check(PoolResources.name("  GPU Servers  ").equals("GPU Servers"));check(PoolResources.name("").equals("Default"));check(PoolResources.name("bad\nname")==null);check(PoolResources.name("abcdefghijklmnopqrstuvwxyz1234567")==null);check(PoolResources.name("Пул GPU").equals("Пул GPU"));
        UUID pool=PoolResources.id(owner,"GPU Servers");check(pool.equals(PoolResources.id(owner,"GPU Servers")));check(!pool.equals(PoolResources.id(other,"GPU Servers")));check(!pool.equals(PoolResources.id(owner,"CPU")));
        int[] p=new int[ServerSpec.COUNT];for(Part part:Part.models){switch(part.kind){case CPU:p[ServerSpec.CPU]=part.index;p[ServerSpec.CPU2]=part.index;break;case BOARD:p[0]=part.index;break;case GPU:p[ServerSpec.PCIE]=part.index;break;case RAM:p[ServerSpec.RAM]=part.index;break;case HDD:p[ServerSpec.SATA]=part.index;break;case SSD:p[ServerSpec.SATA+1]=part.index;break;case NVME:p[ServerSpec.NVME]=part.index;break;case PCIE:p[ServerSpec.PCIE+2]=part.index;break;default:break;}}
        List<PoolResources.Device> all=PoolResources.catalog(server,p);Set<String> keys=new HashSet<>();int cores=0,gpu=0,ram=0,disk=0;for(PoolResources.Device d:all){check(keys.add(d.key()));PoolResources.Binding b=new PoolResources.Binding(d.server,d.kind,d.slot,d.unit,d.model,d.capacity);check(b.matches(d));check(!new PoolResources.Binding(other,d.kind,d.slot,d.unit,d.model,d.capacity).matches(d));check(!new PoolResources.Binding(server,d.kind,d.slot,d.unit,d.model,d.capacity+1).matches(d));switch(d.kind){case CPU:cores++;break;case GPU:gpu++;break;case RAM:ram+=d.capacity;break;case HDD:case SSD:case NVME:disk+=d.capacity;break;default:break;}}
        check(cores==ServerSpec.cpuCores(p));check(gpu==ServerSpec.gpuTotal(p));check(ram==ServerSpec.ramGB(p));check(disk==ServerSpec.diskGB(p));
        TaskEngine.Node a=new TaskEngine.Node(server,0,1000,p,pool),b=new TaskEngine.Node(server,0,1000,p,PoolResources.id(owner,"CPU"));check(!a.equals(b));check(a.equals(new TaskEngine.Node(server,0,1000,p,pool)));
        TaskEngine.Task task=new TaskEngine.Task(0,0);task.enabled=true;task.pool=PoolResources.id(owner,"Missing");TaskEngine.Plan plan=TaskEngine.plan(Collections.singletonList(a),Collections.singletonList(task),new int[]{1000},1000,Collections.emptySet());check(plan.tasks.get(0).units==0);check(plan.cpu[0]==0);check(plan.gpu[0]==0);
        System.out.println("Pool catalog and identity tests passed; physical entries: "+all.size());
    }
}
