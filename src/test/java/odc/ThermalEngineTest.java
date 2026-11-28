package odc;

import java.util.*;

public final class ThermalEngineTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static int model(String name){return ServerSpecTest.model(name);}
    static int[] config(String cooler,int fans,boolean gpuLiquid){
        int[] p=ServerSpecTest.minimal();
        p[ServerSpec.CPU]=model("InTroll CoreX 4770");
        p[ServerSpec.PCIE]=model("NoVideo GTX 750");
        if(cooler!=null)p[ServerSpec.CPU_COOLER]=model(cooler);
        for(int i=0;i<fans;i++)p[ServerSpec.FAN+i]=model("Breeze Turbo");
        if(gpuLiquid)p[ServerSpec.GPU_COOLER]=model("Hydro GPU 400");
        return p;
    }
    static final class Fixture {
        final ThermalEngine.State state;
        final int[] dc=new int[ServerSpec.COUNT];
        final int ac;
        Fixture(int[] p){
            state=new ThermalEngine.State(ServerSpec.thermalProfile(ChassisSpec.GPU_4,p),25);
            TaskEngine.Node node=new TaskEngine.Node(null,0,0,p);
            BitSet cores=new BitSet();cores.set(0,node.cores);
            ac=node.inputWatts(node.watts(cores,1,dc));
        }
        void run(int seconds,double factor,ThermalEngine.LiquidSink sink){
            for(int i=0;i<seconds;i++){
                double old=state.storedHeat(25);
                state.step(dc,ac,ac*factor,25,sink);
                check(Math.abs(state.storedHeat(25)-old-state.generatedWatts+state.liquidWatts+state.ambientWatts)<1e-6,"heat balance");
                check(Math.abs(state.generatedWatts-ac*factor)<1e-6,"AC includes DC and PSU losses once");
                check(Double.isFinite(state.air)&&state.air>=0&&Double.isFinite(state.internal)&&state.internal>=0,"finite temperatures");
            }
        }
    }
    public static void main(String[] args){
        Fixture bare=new Fixture(config(null,4,false)),passive=new Fixture(config("Passive Copper",4,false)),flat=new Fixture(config("Flat Turbo",4,false)),tower=new Fixture(config("Tower Titan",4,false));
        bare.run(300,1,null);passive.run(300,1,null);flat.run(300,1,null);tower.run(300,1,null);
        int cpu=ServerSpec.CPU,gpu=ServerSpec.PCIE;
        check(bare.state.temperature(cpu)>passive.state.temperature(cpu),"no CPU cooler hotter");
        check(flat.state.temperature(cpu)<bare.state.temperature(cpu)&&tower.state.temperature(cpu)<flat.state.temperature(cpu),"active cooler effectiveness");
        Fixture weak=new Fixture(config("Passive Copper",0,false));weak.run(300,1,null);
        check(weak.state.air>passive.state.air&&weak.state.temperature(gpu)>passive.state.temperature(gpu)&&weak.state.internal>passive.state.internal,"fans cool GPU and internal parts");
        double hot=tower.state.temperature(cpu),stored=tower.state.storedHeat(25);
        for(int i=0;i<1800;i++)tower.state.step(null,0,0,25,null);
        check(tower.state.temperature(cpu)<hot&&tower.state.storedHeat(25)<stored&&tower.state.generatedWatts==0,"powered off cools");
        check(tower.state.air<30&&tower.state.temperature(cpu)<30,"approaches ambient");
        Fixture dry=new Fixture(config("Hydro CPU 300",4,true)),wet=new Fixture(config("Hydro CPU 300",4,true));
        ThermalEngine.LiquidSink sink=new ThermalEngine.LiquidSink(){public double temperature(){return 25;}public double take(int slot,double demand){return demand;}};
        dry.run(300,1,null);wet.run(300,1,sink);
        check(dry.state.liquidWatts==0&&wet.state.liquidWatts>0,"no free liquid cooling");
        check(wet.state.temperature(cpu)<dry.state.temperature(cpu)&&wet.state.temperature(gpu)<dry.state.temperature(gpu),"working contour improves both");
        Fixture low=new Fixture(config("Tower Titan",4,false)),high=new Fixture(config("Tower Titan",4,false));
        low.run(120,.9,null);high.run(120,1.1,null);
        check(high.state.temperature(cpu)>low.state.temperature(cpu),"same grid multiplier affects heat");
        high.state.step(null,0,0,Double.NaN,null);check(Double.isFinite(high.state.air),"invalid ambient sanitized");
        high.state.step(null,0,0,-100,null);check(high.state.air>=0,"nonnegative");
        int[] two=config(null,4,false);two[ServerSpec.BOARD]=model("ASBrick Rack EP2C612 WS");two[ServerSpec.CPU]=two[ServerSpec.CPU2]=model("InTroll Zeon 2696 v4");
        two[ServerSpec.PCIE+1]=model("NoVideo GTX 750");
        ThermalEngine.State individual=new ThermalEngine.State(ServerSpec.thermalProfile(ChassisSpec.GPU_4,two),25);
        int[] power=new int[ServerSpec.COUNT];power[ServerSpec.CPU]=120;power[ServerSpec.CPU2]=12;power[gpu]=55;power[gpu+1]=6;
        individual.step(power,193,193,25,null);
        check(individual.temperature(cpu)>individual.temperature(ServerSpec.CPU2)&&individual.temperature(gpu)>individual.temperature(gpu+1),"physical individual temperatures");
        System.out.println("ThermalEngine: "+checks+" checks passed");
    }
}
