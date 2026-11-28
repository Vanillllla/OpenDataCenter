package odc;
import java.util.*;

public final class LiquidCoreTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static void close(double a,double b,String label){check(Math.abs(a-b)<1e-6,label+" "+a+" != "+b);}
    static int model(String name){return ServerSpecTest.model(name);}
    static ThermalEngine.State state(){
        int[] p=ServerSpecTest.minimal();p[ServerSpec.CPU_COOLER]=model("Hydro CPU 300");p[ServerSpec.PCIE]=model("NoVideo GTX 750");p[ServerSpec.GPU_COOLER]=model("Hydro GPU 200");
        return new ThermalEngine.State(ServerSpec.thermalProfile(ChassisSpec.GPU_4,p),25);
    }
    public static void main(String[] args){
        for(int tier=1;tier<=3;tier++){
            int e=LiquidCore.electric(tier),r=LiquidCore.rated(tier);
            close(LiquidCore.available(tier,e,true),r,"rated");
            close(LiquidCore.available(tier,e,false),0,"unconfirmed");
            close(LiquidCore.available(tier,0,true),0,"no electricity");
            close(LiquidCore.available(tier,e/2,true),r*.5,"scaled receipt");
            close(LiquidCore.available(tier,e*2,true),r,"capacity ceiling");
        }
        LiquidCore.Loop loop=new LiquidCore.Loop(4,25);
        loop.begin(300);loop.request(200);loop.request(400);loop.allocate();
        close(loop.take(0,200),100,"first proportion");close(loop.take(1,400),200,"second proportion");close(loop.accepted,300,"bounded aggregate");
        loop.finish(25);check(loop.temp>25&&loop.rejected>0&&loop.rejected<=300,"thermal inertia");
        loop.begin(300);loop.request(400);loop.request(200);loop.allocate();
        close(loop.take(1,400),200,"independent order");close(loop.take(0,200),100,"independent order 2");
        loop.finish(25);double hot=loop.temp;
        loop.begin(0);loop.request(900);loop.allocate();close(loop.take(0,900),0,"lost power");loop.finish(25);
        check(loop.temp<hot&&loop.rejected==0,"passive contour relaxation only");
        loop.begin(3000);loop.request(100);loop.allocate();close(loop.take(0,100),100,"not overcooling");
        ThermalEngine.State a=state(),b=state(),dry=state();
        int[] dc=new int[ServerSpec.COUNT];dc[ServerSpec.CPU]=100;dc[ServerSpec.PCIE]=50;
        LiquidCore.Loop shared=new LiquidCore.Loop(3,25);
        for(int i=0;i<300;i++){
            a.prepare(dc,150,150,25);b.prepare(dc,150,150,25);dry.prepare(dc,150,150,25);
            shared.begin(180);shared.request(a.liquidDemand(shared.temp));shared.request(b.liquidDemand(shared.temp));shared.allocate();
            a.finish(25,shared);b.finish(25,shared);dry.finish(25,null);
            close(a.liquidWatts,b.liquidWatts,"equal demand fair share");
            close(a.liquidWatts+b.liquidWatts,shared.accepted,"removed heat accounted once");
            check(shared.accepted<=180+1e-8,"shared capacity");
            shared.finish(25);
        }
        check(a.temperature(ServerSpec.CPU)<dry.temperature(ServerSpec.CPU),"connected cooler works");
        check(a.temperature(ServerSpec.PCIE)<dry.temperature(ServerSpec.PCIE),"GPU waterblock works");
        double before=a.temperature(ServerSpec.CPU);for(int i=0;i<20;i++)a.step(dc,150,150,25,null);
        check(a.temperature(ServerSpec.CPU)>before&&a.liquidWatts==0,"pipe cut removes cooling");
        // Generic cached component search: a split must never grant the other
        // fragment's chiller. Physical world adjacency is tested manually.
        Map<Integer,List<Integer>> graph=new HashMap<>();
        graph.put(0,Arrays.asList(1));graph.put(1,Arrays.asList(0,2,3));graph.put(2,Arrays.asList(1));graph.put(3,Arrays.asList(1));
        check(TopologySearch.component(0,graph::get,x->true,10).size()==4,"shared topology");
        check(TopologySearch.component(0,graph::get,x->x!=1,10).size()==1,"pipe cut split");
        check(TopologySearch.component(0,graph::get,x->true,3)==null,"oversized fails closed");
        System.out.println("LiquidCore: "+checks+" checks passed");
    }
}
