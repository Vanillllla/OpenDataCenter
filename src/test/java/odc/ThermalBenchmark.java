package odc;

import java.lang.management.ManagementFactory;
import java.util.BitSet;
import java.util.Locale;

/** Actual timed thermal-only workload, not a TPS estimate. 2 CPU + 4 GPU
 * per server; all servers share a saturated liquid contour. */
public final class ThermalBenchmark {
    static final int WARMUP=400,MEASURE=1200;
    static final class Case {
        final ThermalEngine.State[] states;
        final int[] dc=new int[ServerSpec.COUNT];
        final int nominal;
        final LiquidCore.Loop loop;
        Case(int count){
            int[] p=ServerSpecTest.minimal();
            p[ServerSpec.BOARD]=ServerSpecTest.model("ASBrick Rack EP2C612 WS");
            p[ServerSpec.CPU]=p[ServerSpec.CPU2]=ServerSpecTest.model("InTroll Zeon 2696 v4");
            for(int i=0;i<4;i++){p[ServerSpec.PCIE+i]=ServerSpecTest.model("NoVideo Teslo P4");p[ServerSpec.GPU_COOLER+i]=ServerSpecTest.model("Hydro GPU 200");}
            p[ServerSpec.CPU_COOLER]=p[ServerSpec.CPU_COOLER+1]=ServerSpecTest.model("Hydro CPU 300");
            for(int i=0;i<8;i++)p[ServerSpec.FAN+i]=ServerSpecTest.model("Breeze 60");
            for(int i=ServerSpec.PSU;i<ServerSpec.CPU2;i++)p[i]=ServerSpecTest.model("SuperMacro 1500 Titanium");
            TaskEngine.Node node=new TaskEngine.Node(null,0,0,p);BitSet cores=new BitSet();cores.set(0,node.cores);nominal=node.inputWatts(node.watts(cores,15,dc));
            if(nominal<=0)throw new AssertionError("invalid benchmark load");
            ThermalProfile profile=ServerSpec.thermalProfile(ChassisSpec.GPU_4,p);
            states=new ThermalEngine.State[count];for(int i=0;i<count;i++)states[i]=new ThermalEngine.State(profile,25);
            loop=new LiquidCore.Loop(count+1,25);
        }
        double cycle(){
            loop.begin(states.length*200.0);
            for(ThermalEngine.State s:states){s.prepare(dc,nominal,nominal,25);loop.request(s.liquidDemand(loop.temp));}
            loop.allocate();
            for(ThermalEngine.State s:states)s.finish(25,loop);
            loop.finish(25);return loop.accepted;
        }
    }
    static com.sun.management.ThreadMXBean allocations(){
        java.lang.management.ThreadMXBean bean=ManagementFactory.getThreadMXBean();
        if(!(bean instanceof com.sun.management.ThreadMXBean))return null;
        com.sun.management.ThreadMXBean b=(com.sun.management.ThreadMXBean)bean;
        if(!b.isThreadAllocatedMemorySupported())return null;
        if(!b.isThreadAllocatedMemoryEnabled())b.setThreadAllocatedMemoryEnabled(true);
        return b;
    }
    static long used(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    public static void main(String[] args){
        System.out.println("ThermalBenchmark JVM="+System.getProperty("java.version")+" CPU="+Runtime.getRuntime().availableProcessors());
        com.sun.management.ThreadMXBean bean=allocations();long thread=Thread.currentThread().getId();
        for(int n:new int[]{200,500,1000}){
            System.gc();long heapBefore=used(),allocBefore=bean==null?-1:bean.getThreadAllocatedBytes(thread);
            Case c=new Case(n);
            long setup=bean==null?-1:bean.getThreadAllocatedBytes(thread)-allocBefore;
            for(int i=0;i<WARMUP;i++)c.cycle();
            System.gc();long heap=Math.max(0,used()-heapBefore);
            long bytesBefore=bean==null?-1:bean.getThreadAllocatedBytes(thread);
            long start=System.nanoTime();double checksum=0;
            for(int i=0;i<MEASURE;i++)checksum+=c.cycle();
            long elapsed=System.nanoTime()-start;
            long bytes=bean==null?-1:bean.getThreadAllocatedBytes(thread)-bytesBefore;
            if(!Double.isFinite(checksum)||checksum<=0||c.loop.accepted>c.loop.capacity+1e-6)throw new AssertionError("invalid thermal result");
            System.out.printf(Locale.ROOT,"THERMAL_BENCH servers=%d components=%d cycles=%d mean_ms=%.6f alloc_B_per_cycle=%.2f setup_B=%d heap_delta_B=%d checksum=%.3f%n",n,n*6,MEASURE,elapsed/1e6/MEASURE,bytes<0?-1:bytes/(double)MEASURE,setup,heap,checksum);
        }
    }
}
