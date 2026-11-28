package odc;

import java.util.Arrays;

/** One simulated second. Finite-volume lumped thermal network; no world access.
 * Pairwise exact exchange preserves energy and cannot overshoot equilibrium.
 * All temperatures are Celsius, capacities J/K, conductances W/K, heat W. */
final class ThermalEngine {
    static final double MAX_C=1000000;
    private ThermalEngine(){}
    interface LiquidSink {
        double temperature();
        // Return actual accepted thermal watts, bounded by the request.
        double take(int componentSlot,double requestedWatts);
    }
    static double safe(double value,double fallback){
        if(!Double.isFinite(value))value=Double.isFinite(fallback)?fallback:25;
        return Math.max(0,Math.min(MAX_C,value));
    }
    static double exchange(double conductance,double a,double b){
        return -Math.expm1(-conductance*(1/a+1/b))/(1/a+1/b);
    }
    static final class State {
        final ThermalProfile profile;
        final int[] slots;
        private final double[] temp,capacity,onExchange,offExchange;
        private final double internalOn,internalOff,airOn,airOff;
        double internal,air,generatedWatts,liquidWatts,ambientWatts;
        private boolean active;
        State(ThermalProfile profile,double ambient){
            this.profile=profile;
            int n=0;
            for(int i=0;i<ServerSpec.COUNT;i++)if(processor(profile.part(i)))n++;
            slots=new int[n];temp=new double[n];capacity=new double[n];onExchange=new double[n];offExchange=new double[n];
            double flow=Math.max(1,profile.airConductance/2);
            for(int i=0,k=0;i<ServerSpec.COUNT;i++)if(processor(profile.part(i))){
                slots[k]=i;capacity[k]=Math.max(1,profile.heatCapacity(i));
                double resistance=profile.resistance(i),onR=resistance,offR=resistance;
                Part cooler=profile.cooler(i);
                if(profile.liquid(i)){onR=offR=3.0;} // weak leakage to air, not free liquid cooling
                else if(profile.requiresCaseAirflow(i)){onR=resistance/Math.sqrt(flow);offR=resistance;}
                else if(cooler.watts>0){offR=Math.max(1.2,resistance*4);}
                onExchange[k]=exchange(1/Math.max(.01,onR),capacity[k],profile.airHeatCapacity);
                offExchange[k]=exchange(1/Math.max(.01,offR),capacity[k],profile.airHeatCapacity);k++;
            }
            internalOn=exchange(3+profile.airConductance*.5,profile.internalHeatCapacity,profile.airHeatCapacity);
            internalOff=exchange(4,profile.internalHeatCapacity,profile.airHeatCapacity);
            airOn=-Math.expm1(-profile.airConductance/profile.airHeatCapacity);
            airOff=-Math.expm1(-2/profile.airHeatCapacity);
            air=internal=safe(ambient,25);Arrays.fill(temp,air);
        }
        private static boolean processor(Part p){return p.kind==Part.Kind.CPU||p.kind==Part.Kind.GPU;}
        double temperature(int slot){for(int k=0;k<slots.length;k++)if(slots[k]==slot)return temp[k];return Double.NaN;}
        // Existing per-slot DC values are nominal. Scale the whole AC receipt once;
        // preserve the same ratio for DC and PSU losses, including cumulative rounding.
        void step(int[] dc,int nominalAC,double actualAC,double ambient,LiquidSink sink){prepare(dc,nominalAC,actualAC,ambient);finish(ambient,sink);}
        void prepare(int[] dc,int nominalAC,double actualAC,double ambient){
            ambient=safe(ambient,25);air=safe(air,ambient);internal=safe(internal,ambient);
            for(int i=0;i<temp.length;i++)temp[i]=safe(temp[i],ambient);
            double ratio=dc!=null&&dc.length==ServerSpec.COUNT&&nominalAC>0&&Double.isFinite(actualAC)&&actualAC>0?actualAC/nominalAC:0;
            double totalDC=0,inside=0,airHeat=0;
            if(ratio>0)for(int i=0;i<dc.length;i++){
                double p=Math.max(0,dc[i])*ratio;totalDC+=p;
                Part part=profile.part(i);
                if(processor(part))continue;
                if(i>=ServerSpec.CPU_COOLER)airHeat+=p;
                else inside+=p;
            }
            // Rated PSU output is never counted as heat; only measured conversion loss.
            inside+=Math.max(0,(ratio>0?actualAC:0)-totalDC);
            generatedWatts=inside+airHeat;
            active=ratio>0;
            internal+=inside/profile.internalHeatCapacity;
            air+=airHeat/profile.airHeatCapacity;
            for(int k=0;k<slots.length;k++){
                double p=active?Math.max(0,dc[slots[k]])*ratio:0;generatedWatts+=p;temp[k]+=p/capacity[k];
            }
            liquidWatts=0;
        }
        private double request(int k,double coolant){
            if(!profile.liquid(slots[k]))return 0;
            double diff=Math.max(0,temp[k]-coolant);
            return Math.min(profile.coolingRating(slots[k]),Math.min(diff*capacity[k],diff/Math.max(.01,profile.resistance(slots[k]))));
        }
        double liquidDemand(double coolant){
            double total=0;for(int k=0;k<slots.length;k++)total+=request(k,coolant);return total;
        }
        void finish(double ambient,LiquidSink sink){
            ambient=safe(ambient,25);
            if(sink!=null){
                double coolant=safe(sink.temperature(),ambient);
                for(int k=0;k<slots.length;k++)if(profile.liquid(slots[k])){
                    double request=request(k,coolant);
                    if(request>0){double got=sink.take(slots[k],request);if(!Double.isFinite(got))got=0;got=Math.max(0,Math.min(request,got));temp[k]-=got/capacity[k];liquidWatts+=got;}
                }
            }
            for(int k=0;k<slots.length;k++){
                double q=(temp[k]-air)*(active?onExchange[k]:offExchange[k]);
                temp[k]-=q/capacity[k];air+=q/profile.airHeatCapacity;
            }
            double q=(internal-air)*(active?internalOn:internalOff);
            internal-=q/profile.internalHeatCapacity;air+=q/profile.airHeatCapacity;
            ambientWatts=(air-ambient)*(active?airOn:airOff)*profile.airHeatCapacity;
            air-=ambientWatts/profile.airHeatCapacity;
            air=safe(air,ambient);internal=safe(internal,ambient);for(int k=0;k<temp.length;k++)temp[k]=safe(temp[k],ambient);
        }
        double storedHeat(double reference){
            double energy=(air-reference)*profile.airHeatCapacity+(internal-reference)*profile.internalHeatCapacity;
            for(int k=0;k<slots.length;k++)energy+=(temp[k]-reference)*capacity[k];return energy;
        }
    }
}
