package odc;

/** Pure conversion contract. Rates are millijoules per external energy unit,
 * not watts per unit: at 20 TPS one watt-tick is exactly 50 mJ.
 * No storage or implicit source exists. An adapter must supply actual paid units. */
final class TransformerCore {
    static final int MILLIS_PER_WATT_TICK=50;
    static final int MAX_WATTS=100000000;
    enum Input { HE, FE }
    static final int[] DEFAULT_WATTS={5000,25000,100000};
    private TransformerCore(){}
    static int tierIndex(int tier){
        if(tier<1||tier>DEFAULT_WATTS.length)throw new IllegalArgumentException("tier");
        return tier-1;
    }
    static int defaultWatts(int tier){return DEFAULT_WATTS[tierIndex(tier)];}
    static long energyMilliJoules(long watts,int ticks){
        return Math.multiplyExact(PowerEngine.energyUnits(watts,ticks),MILLIS_PER_WATT_TICK);
    }
    private static void validate(int rate,int ticks){if(rate<=0||ticks<=0)throw new IllegalArgumentException("rate/duration");}
    /** Integral external units to buy; ceiling rounding must never create energy. */
    static long requiredUnits(long demandWatts,int ticks,int rate){
        validate(rate,ticks);
        long mj=energyMilliJoules(demandWatts,ticks);
        return mj/rate+(mj%rate==0?0:1);
    }
    /** Conservative integral watts supported by genuinely received energy. */
    static long paidWatts(long units,int ticks,int rate,int ratedWatts){
        validate(rate,ticks);
        if(units<0||ratedWatts<0||ratedWatts>MAX_WATTS)throw new IllegalArgumentException("units/rating");
        long cap=energyMilliJoules(ratedWatts,ticks);
        long needed=cap/rate+(cap%rate==0?0:1);
        if(units>=needed)return ratedWatts;
        return Math.multiplyExact(units,(long)rate)/((long)ticks*MILLIS_PER_WATT_TICK);
    }
    /** Optional integration contract for 0.6.2.5. Implementations must return
     * actual debited units (not a simulation) and never return more than requested. */
    interface ExternalInput {
        long extract(long units,boolean simulate);
    }
    static final class Delivery {
        final long units,watts;
        Delivery(long units,long watts){this.units=units;this.watts=watts;}
    }
    /** Pure adapter boundary; not connected to any external API in 0.6.2.2.
     * Unused/rounded energy is not stored or exported. */
    static Delivery deliver(ExternalInput input,boolean enabled,int ratedWatts,long demandWatts,int ticks,int rate){
        validate(rate,ticks);
        if(demandWatts<0||ratedWatts<0||ratedWatts>MAX_WATTS)throw new IllegalArgumentException("demand/rating");
        long target=Math.min(demandWatts,ratedWatts);
        if(!enabled||input==null||target==0)return new Delivery(0,0);
        long requested=requiredUnits(target,ticks,rate);
        long paid=input.extract(requested,false);
        if(paid<0||paid>requested)throw new IllegalStateException("invalid external debit");
        return new Delivery(paid,Math.min(target,paidWatts(paid,ticks,rate,ratedWatts)));
    }
}
