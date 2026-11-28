package odc;

import java.util.*;

/** Side-independent power budget; watt-ticks are exact twentieths of a joule.
 * One fixed multiplier per grid/cycle is applied to cumulative nominal load,
 * not individual reservations. Preview and commitment therefore round identically. */
final class PowerEngine {
    static final int TICKS_PER_SECOND=20, ONE=10000;
    private PowerEngine(){}
    static long energyUnits(long watts,int ticks){
        if(watts<0||ticks<0)throw new IllegalArgumentException("negative power or duration");
        return Math.multiplyExact(watts,(long)ticks);
    }
    static double joules(long units){return units/(double)TICKS_PER_SECOND;}
    static long sum(long a,long b){if(a<0||b<0)throw new IllegalArgumentException("negative power");return Math.addExact(a,b);}
    /** Stable pseudorandom basis points, inclusive ±percent. No graph traversal
     * or shared RNG state; the next receipt window can use its future factor. */
    static int factor(long seed,long cycle,int percent){
        if(percent<0||percent>100)throw new IllegalArgumentException("percent");
        if(percent==0)return ONE;
        long z=seed+0x9e3779b97f4a7c15L*(cycle+1);
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;z^=z>>>31;
        int span=percent*100;
        return ONE-span+(int)Math.floorMod(z,2L*span+1);
    }
    /** Nearest whole watt, with no intermediate multiplication overflow. */
    static long scale(long watts,int factor){
        if(watts<0||factor<0||factor>2*ONE)throw new IllegalArgumentException("power/factor");
        return Math.addExact(Math.multiplyExact(watts/ONE,(long)factor),((watts%ONE)*factor+ONE/2)/ONE);
    }
    static long[] scaleCategories(long[] nominal,int factor){
        long[] result=new long[nominal.length];long prefix=0,previous=0;
        for(int i=0;i<nominal.length;i++){
            prefix=sum(prefix,nominal[i]);long actual=scale(prefix,factor);
            result[i]=actual-previous;previous=actual;
        }
        return result;
    }
    static final class Budget {
        final long capacityWatts;
        final int ticks,factor;
        final long capacityUnits;
        private long nominalWatts,usedUnits;
        Budget(long watts,int ticks){this(watts,ticks,ONE);}
        Budget(long watts,int ticks,int factor){
            if(ticks<=0||factor<0||factor>2*ONE)throw new IllegalArgumentException("duration/factor");
            capacityWatts=watts;this.ticks=ticks;this.factor=factor;capacityUnits=energyUnits(watts,ticks);
        }
        long nominalWatts(){return nominalWatts;}
        long usedUnits(){return usedUnits;}
        long remainingWatts(){return (capacityUnits-usedUnits)/ticks;}
        boolean canUse(long watts){return watts>=0&&fitsTotal(sum(nominalWatts,watts));}
        private boolean fitsTotal(long total){return scale(total,factor)<=capacityWatts;}
        boolean use(long watts){
            if(!canUse(watts))return false;
            nominalWatts=sum(nominalWatts,watts);
            usedUnits=energyUnits(scale(nominalWatts,factor),ticks);return true;
        }
    }
    /** Non-mutating trial budget, using exactly the same cumulative rounding. */
    static final class Preview {
        private final Map<Budget,Long> totals=new IdentityHashMap<>();
        boolean use(Budget b,long watts){
            if(b==null||watts<0)return false;
            long total=sum(totals.getOrDefault(b,b.nominalWatts()),watts);
            if(!b.fitsTotal(total))return false;
            totals.put(b,total);return true;
        }
    }
}
