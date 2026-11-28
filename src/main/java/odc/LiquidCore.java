package odc;

/** Thermal capacity and proportional arbitration, without world access.
 * All flow figures are thermal watts over the single one-second cycle. */
final class LiquidCore {
    static final int[] RATED={5000,25000,100000}, ELECTRIC={1500,6000,20000};
    static int rated(int tier){return RATED[tier-1];}
    static int electric(int tier){return ELECTRIC[tier-1];}
    static double available(int tier,long actualAC,boolean confirmed){
        return confirmed?rated(tier)*Math.min(1,Math.max(0,actualAC)/(double)electric(tier)):0;
    }
    static final class Loop implements ThermalEngine.LiquidSink {
        final double heatCapacity;
        double temp,capacity,requested,accepted,rejected,ratio;
        Loop(int endpoints,double ambient){heatCapacity=2000+1000.0*Math.max(1,endpoints);temp=ThermalEngine.safe(ambient,25);}
        void begin(double capacity){this.capacity=Double.isFinite(capacity)?Math.max(0,capacity):0;requested=accepted=rejected=ratio=0;}
        void request(double watts){if(Double.isFinite(watts)&&watts>0)requested+=watts;}
        void allocate(){ratio=requested>0?Math.min(1,capacity/requested):0;}
        @Override public double temperature(){return temp;}
        @Override public double take(int slot,double watts){
            if(!Double.isFinite(watts)||watts<=0)return 0;
            double granted=Math.min(watts*ratio,Math.max(0,capacity-accepted));
            accepted+=granted;return granted;
        }
        void finish(double ambient){
            ambient=ThermalEngine.safe(ambient,25);
            // Lumped coolant thermal mass: heat removed from chips enters this
            // node first; powered chillers reject heat with a 10 s response.
            // No forced flow at zero confirmed capacity. A small passive
            // ambient relaxation affects only the loop, never disconnected chips.
            double stored=(temp-ambient)*heatCapacity+accepted;
            rejected=Math.min(capacity,Math.max(0,stored)*.1);
            double remaining=stored-rejected;
            remaining*=.998;
            temp=ThermalEngine.safe(ambient+remaining/heatCapacity,ambient);
        }
    }
}
