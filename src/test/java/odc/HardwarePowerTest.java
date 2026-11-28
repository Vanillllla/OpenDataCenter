package odc;
import java.util.*;
public final class HardwarePowerTest {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static int model(String name){for(Part p:Part.models)if(p.name.equals(name))return p.index;throw new AssertionError(name);}
    public static void main(String[] args){
        int[] p=new int[ServerSpec.COUNT];
        p[ServerSpec.PSU]=model("AeroHot VX500");
        check(ServerSpec.inputWatts(p,400)==500,"80 percent efficiency");
        check(ServerSpec.inputWatts(p,500)==625,"rating is output not input");
        check(ServerSpec.inputWatts(p,501)==-1,"PSU overload");
        check(ServerSpec.inputWatts(p,1)==2,"round upward");
        p[ServerSpec.PSU+1]=model("CorsError RM1000 Gold");
        check(ServerSpec.psu(p)==1500,"sum ratings");
        check(ServerSpec.inputWatts(p,750)==869,"mixed PSU proportional load");
        check(ServerSpec.inputWatts(p,1500)==1737,"mixed PSU full load");
        int tmp=p[ServerSpec.PSU];p[ServerSpec.PSU]=p[ServerSpec.PSU+1];p[ServerSpec.PSU+1]=tmp;
        check(ServerSpec.inputWatts(p,750)==869,"slot order independent");
        check(ServerSpec.inputWatts(p,0)==0,"zero load");
        for(int w=1;w<=1500;w++){
            int input=ServerSpec.inputWatts(p,w);
            check(input>=w&&input==(int)Math.ceil(w*(500.0/0.8+1000.0/0.9)/1500.0-1e-10),"conservative mixed conversion "+w);
        }
        int[] missing=new int[ServerSpec.COUNT];
        check(ServerSpec.inputWatts(missing,1)==-1,"no PSU");
        p[ServerSpec.BOARD]=model("ASBrick H81-S");
        p[ServerSpec.CPU]=model("InTroll CoreX 4770");
        p[ServerSpec.RAM]=model("KingStoned Value 4");
        p[ServerSpec.SATA]=model("SeaBait Basic 256");
        p[ServerSpec.PCIE]=model("NoVideo Teslo M10");
        p[ServerSpec.PCIE+1]=model("FakeTek G1000");
        TaskEngine.Node n=new TaskEngine.Node(UUID.randomUUID(),0,1000,p);
        int constant=15+5+15+10;
        check(n.watts(0,0)==constant+7+23,"idle CPU/GPU plus constant parts");
        check(n.watts(4,15)==constant+65+225,"full CPU/GPU");
        check(n.watts(2,3)==constant+36+124,"half CPU/GPU");
        check(ServerSpec.drawWatts(p)==n.watts(4,15),"nominal components");
        check(ServerSpec.idleInputWatts(p)==n.inputWatts(n.watts(0,0)),"idle conversion");
        check(ServerSpec.maxInputWatts(p)==n.inputWatts(n.watts(4,15)),"max conversion");
        TaskEngine.Plan plan=TaskEngine.plan(Collections.singletonList(n),Collections.emptyList(),new int[]{1000},1000,Collections.emptySet());
        check(plan.overload.isEmpty()&&plan.power[0]==ServerSpec.idleInputWatts(p),"planner uses input watts");
        int[] heavy=p.clone();heavy[ServerSpec.PSU]=model("AeroHot VX500");heavy[ServerSpec.PSU+1]=0;
        heavy[ServerSpec.CPU]=model("AMDead Epyx 9996 Titan");heavy[ServerSpec.PCIE]=model("NoVideo B300 Titan");
        TaskEngine.Node h=new TaskEngine.Node(UUID.randomUUID(),0,1000,heavy);
        check(h.inputWatts(h.watts(128,1))==-1,"loaded hardware exceeds output");
        PowerEngine.Budget b=new PowerEngine.Budget(800,20);
        check(ServerSpec.inputWatts(p,750)>800&&!b.use(ServerSpec.inputWatts(p,750)),"valid PSU but insufficient grid");
        for(NetworkEquipment e:NetworkEquipment.values())check(e.watts>0,"constant network power");
        for(int t=1;t<=SwitchLimits.count();t++)check(SwitchLimits.power(t)>0,"constant switch power");
        check(NetworkEquipment.DATAOS_WATTS==30,"controller nominal");
        System.out.println("HardwarePower: "+checks+" checks passed");
    }
}
