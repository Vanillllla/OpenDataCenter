package odc;

import java.util.*;

/** Holds one reusable allocation per network. Power availability is checked on every cycle. */
final class AllocationCache {
    private List<?> inputs;
    private TaskEngine.Plan result;

    TaskEngine.Plan get(List<TaskEngine.Node> nodes,List<TaskData.Job> jobs,int[] caps,int wan,
                        Map<UUID,Integer> groups,Set<Integer> excluded) {
        List<Object> key=new ArrayList<>();
        key.add(new ArrayList<>(nodes));
        for(int cap:caps) key.add(cap);
        key.add(wan);
        key.add(new HashSet<>(excluded));
        for(TaskEngine.Node n:nodes) key.add(groups.getOrDefault(n.id,0));
        for(TaskData.Job job:jobs) {
            if(!job.enabled) continue;
            key.add(Arrays.asList(job.id,job.type,job.base,job.unitLimit,job.priority,job.cpu,job.gpus,
                    job.ram,job.net,job.disk,job.group,job.auto,job.strict));
            if(!job.auto) for(TaskEngine.Node n:nodes) {
                TaskEngine.Limit l=job.manual.get(n.id);
                key.add(l==null?null:Arrays.asList(l.cpu,l.mask,l.ram,l.disk,l.net));
            }
        }
        if(!key.equals(inputs)) {
            result=TaskEngine.plan(nodes,jobs,caps,wan,groups,excluded);
            inputs=key;
        }
        return result;
    }
}
