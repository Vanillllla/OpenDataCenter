package odc;
import java.util.*;
public final class TaskEngineTest {
    public static void main(String[] args) {
        int[] parts=ServerSpecTest.minimal();
        parts[ServerSpec.CPU]=ServerSpecTest.model("InTroll CoreX 4770");
        parts[ServerSpec.PCIE]=ServerSpecTest.model("NoVideo Teslo M10");
        TaskEngine.Node node=new TaskEngine.Node(new UUID(0,1),0,32,parts);
        TaskEngine.Task job=new TaskEngine.Task(1,1);
        job.enabled=true;
        TaskEngine.Plan plan=TaskEngine.plan(Collections.singletonList(node),Collections.singletonList(job),new int[]{32},32,Collections.emptyMap(),Collections.emptySet());
        TaskEngine.Result r=plan.tasks.get(1);
        long expected=4L*(TaskEngine.SCALE+3400+1333+8000+20*1033);
        if(r.units!=4 || r.cpu!=4 || r.ram!=4 || r.net!=32 || r.rate!=expected) throw new AssertionError("GPU mining units");
        if(Integer.bitCount(plan.gpu[0])!=4 || !plan.overload.isEmpty()) throw new AssertionError("GPU chip reservations and card power");
        job.unitLimit=5;job.strict=true;
        plan=TaskEngine.plan(Collections.singletonList(node),Collections.singletonList(job),new int[]{32},32,Collections.emptyMap(),Collections.emptySet());
        if(plan.tasks.get(1).units!=0 || plan.gpu[0]!=0 || plan.cpu[0]!=0) throw new AssertionError("strict rollback");
    }
}
