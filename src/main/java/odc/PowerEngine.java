package odc;

import java.util.*;

/** Side-independent, lossless power budget. Watt-ticks are exact twentieths of a joule
 * at 20 TPS; a twenty-tick reservation of one watt consumes one joule. */
final class PowerEngine {
    static final int TICKS_PER_SECOND=20;
    private PowerEngine(){}
    static long energyUnits(long watts,int ticks){
        if(watts<0||ticks<0)throw new IllegalArgumentException("negative power or duration");
        return Math.multiplyExact(watts,(long)ticks);
    }
    static double joules(long units){return units/(double)TICKS_PER_SECOND;}
    static long sum(long a,long b){if(a<0||b<0)throw new IllegalArgumentException("negative power");return Math.addExact(a,b);}
    static final class Budget {
        final long capacityWatts;
        final int ticks;
        final long capacityUnits;
        private long usedUnits;
        Budget(long watts,int ticks){
            if(ticks<=0)throw new IllegalArgumentException("duration");
            capacityWatts=watts;this.ticks=ticks;capacityUnits=energyUnits(watts,ticks);
        }
        long usedUnits(){return usedUnits;}
        long remainingWatts(){return (capacityUnits-usedUnits)/ticks;}
        boolean use(long watts){
            long units=energyUnits(watts,ticks);
            if(units>capacityUnits-usedUnits)return false;
            usedUnits+=units;return true;
        }
    }
    /** Non-mutating trial budget shared by all consumers of the same physical grid. */
    static final class Preview {
        private final Map<Budget,Long> remaining=new IdentityHashMap<>();
        boolean use(Budget b,long watts){
            if(b==null||watts<0)return false;
            long left=remaining.getOrDefault(b,b.remainingWatts());
            if(watts>left)return false;
            remaining.put(b,left-watts);return true;
        }
    }
}
