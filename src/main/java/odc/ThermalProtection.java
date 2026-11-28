package odc;
import java.util.function.IntUnaryOperator;

/** Side-independent, deterministic threshold and independent per-part failure
 * decisions. The caller commits one transition from running to latched off. */
final class ThermalProtection {
    static final int RESTART_MARGIN=10,DEFAULT_FAILURE_PERCENT=5;
    private ThermalProtection(){}
    static final class Limit {
        final int slot,critical;
        final double actual;
        Limit(int slot,int critical,double actual){this.slot=slot;this.critical=critical;this.actual=actual;}
    }
    static double temperature(ThermalEngine.State s,int slot){
        Part p=s.profile.part(slot);
        return p.kind==Part.Kind.CPU||p.kind==Part.Kind.GPU?s.temperature(slot):s.internal;
    }
    static Limit exceeded(ThermalEngine.State s){
        int slot=-1,critical=0;double actual=0,excess=-1;
        for(int i=0;i<ServerSpec.COUNT;i++){
            int c=s.profile.criticalC(i);if(c<=0)continue;
            double t=temperature(s,i);
            if(t>=c&&t-c>excess){slot=i;critical=c;actual=t;excess=t-c;}
        }
        return slot<0?null:new Limit(slot,critical,actual);
    }
    static boolean cooled(ThermalEngine.State s){
        for(int i=0;i<ServerSpec.COUNT;i++){
            int c=s.profile.criticalC(i);if(c<=0)continue;
            double t=temperature(s,i);
            if(!Double.isFinite(t)||t>Math.max(0,c-RESTART_MARGIN))return false;
        }
        return true;
    }
    static long damage(ThermalProfile profile,long alreadyBroken,int percent,IntUnaryOperator random){
        int chance=Math.max(0,Math.min(100,percent));long result=0;
        for(int i=0;i<ServerSpec.COUNT;i++)if(profile.part(i).electronic()&&(alreadyBroken&(1L<<i))==0){
            if(random.applyAsInt(100)<chance)result|=1L<<i;
        }
        return result;
    }
    static final class Accident {
        final Limit limit;final long damage;
        Accident(Limit limit,long damage){this.limit=limit;this.damage=damage;}
        int reason(){return damage==0?ComputeEngine.OVERHEAT:ComputeEngine.HARDWARE_FAILURE;}
    }
    static Accident evaluate(ThermalEngine.State s,boolean running,boolean latched,long broken,int percent,IntUnaryOperator random){
        if(!running||latched||broken!=0)return null;
        Limit limit=exceeded(s);return limit==null?null:new Accident(limit,damage(s.profile,broken,percent,random));
    }
    static boolean canRestart(ThermalEngine.State s,long broken){return broken==0&&cooled(s);}
}
