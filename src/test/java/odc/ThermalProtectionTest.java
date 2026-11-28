package odc;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThermalProtectionTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static ThermalEngine.State state(){
        int[] p=ThermalEngineTest.config("Tower Titan",4,false);
        return new ThermalEngine.State(ServerSpec.thermalProfile(ChassisSpec.GPU_4,p),25);
    }
    static void set(ThermalEngine.State s,int slot,double temp){
        int[] a=s.snapshot();a[slot]=(int)Math.round(temp*1000);s.restore(a,25);
    }
    public static void main(String[] args){
        ThermalEngine.State s=state();int cpu=ServerSpec.CPU,gpu=ServerSpec.PCIE;
        int limit=s.profile.criticalC(cpu),gpuLimit=s.profile.criticalC(gpu);
        check(limit>0&&gpuLimit>0&&s.profile.internalLimitC>0,"individual limits");
        int count=0;long eligible=0;for(int i=0;i<ServerSpec.COUNT;i++)if(s.profile.part(i).electronic()){count++;eligible|=1L<<i;}
        AtomicInteger rolls=new AtomicInteger();
        set(s,cpu,limit-.001);
        check(ThermalProtection.evaluate(s,true,false,0,5,n->{rolls.incrementAndGet();return 0;})==null&&rolls.get()==0,"strict below threshold");
        set(s,cpu,limit);
        ThermalProtection.Accident a=ThermalProtection.evaluate(s,true,false,0,5,n->{rolls.incrementAndGet();return 5;});
        check(a!=null&&a.limit.slot==cpu&&a.limit.actual==limit&&a.damage==0&&a.reason()==ComputeEngine.OVERHEAT,"threshold inclusive; 5 is not below 5%");
        check(rolls.get()==count,"independent per physical component");
        check(ThermalProtection.evaluate(s,false,true,0,100,n->{throw new AssertionError("off reroll");})==null,"latched off no reroll");
        check(ThermalProtection.evaluate(s,true,true,0,100,n->{throw new AssertionError("latched reroll");})==null,"latch guards duplicate callback");
        a=ThermalProtection.evaluate(s,true,false,0,5,n->4);
        check(a.damage==eligible&&a.reason()==ComputeEngine.HARDWARE_FAILURE,"4 below 5%, all electronics, no coolers");
        check(ThermalProtection.damage(s.profile,eligible,100,n->{throw new AssertionError("already broken rerolled");})==0,"idempotent mask");
        check(ThermalProtection.damage(s.profile,0,0,n->0)==0,"0% disables damage");
        check(ThermalProtection.damage(s.profile,0,100,n->99)==eligible,"100% damages all electronics");
        rolls.set(0);long first=ThermalProtection.damage(s.profile,0,5,n->rolls.getAndIncrement()==0?0:99);
        check(Long.bitCount(first)==1&&rolls.get()==count,"per part, not all-or-none");
        set(s,cpu,25);set(s,gpu,gpuLimit);
        check(ThermalProtection.exceeded(s).slot==gpu,"GPU alone shuts down");
        set(s,gpu,25);s.internal=s.profile.internalLimitC;
        ThermalProtection.Limit hit=ThermalProtection.exceeded(s);
        check(hit!=null&&hit.critical==s.profile.internalLimitC,"lowest internal component threshold");
        check(!ThermalProtection.canRestart(s,0),"hot restart denied");
        s.internal=25;set(s,cpu,limit-ThermalProtection.RESTART_MARGIN+.001);
        check(!ThermalProtection.canRestart(s,0),"restart hysteresis");
        set(s,cpu,limit-ThermalProtection.RESTART_MARGIN);
        check(ThermalProtection.canRestart(s,0)&&!ThermalProtection.canRestart(s,first),"cooled but broken cannot restart");
        set(s,cpu,limit);ThermalEngine.State copy=state();copy.restore(s.snapshot(),25);
        check(ThermalProtection.exceeded(copy)!=null&&!ThermalProtection.canRestart(copy,0),"hot persisted across state reload");
        for(int i=0;i<1800;i++)copy.step(null,0,0,25,null);
        check(ThermalProtection.canRestart(copy,0)&&!ThermalProtection.canRestart(copy,first),"cooling allows manual restart only after repair");
        System.out.println("ThermalProtection: "+checks+" checks passed");
    }
}
