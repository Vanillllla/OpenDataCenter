package odc;

import java.util.*;

/** Cache only immutable committed reservations; no implicit allocation or reassignment. */
final class AllocationCache {
    private List<?> inputs;
    private TaskEngine.Plan result;
    TaskEngine.Plan get(List<TaskEngine.Node> nodes,List<TaskData.Job> jobs,Set<Integer> local,int[] caps,int wan,Set<Integer> excluded,Set<Integer> networkBlocked){
        List<Object> key=new ArrayList<>();key.add(new ArrayList<>(nodes));key.add(Arrays.toString(caps));key.add(wan);key.add(new HashSet<>(excluded));key.add(new HashSet<>(local));key.add(networkBlocked==null?null:new HashSet<>(networkBlocked));
        Set<UUID> present=new HashSet<>();for(TaskEngine.Node n:nodes)present.add(n.id);
        for(TaskData.Job j:jobs){boolean relevant=local.contains(j.id)||j.bindings.stream().anyMatch(b->present.contains(b.server));if(!relevant)continue;
            key.add(Arrays.asList(j.id,j.type,j.enabled,j.pool,j.cpu,j.gpus,j.ram,j.disk,j.net,j.base));
            for(PoolResources.Binding b:j.bindings)key.add(Arrays.asList(FixedResources.key(b),b.amount,b.io));
        }
        if(!key.equals(inputs)){result=FixedResources.plan(nodes,jobs,local,caps,wan,excluded,networkBlocked);inputs=key;}
        return result;
    }
}
