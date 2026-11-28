package odc;
import java.util.*;

/** Side-independent policy for exclusive simulation and bounded source loading. */
final class PersistentPower {
    enum Mode { LIVE, VIRTUAL, PARTIAL }
    static Mode mode(int loaded,int total){
        if(total<=0||loaded<0||loaded>total)throw new IllegalArgumentException("consumer count");
        return loaded==0?Mode.VIRTUAL:loaded==total?Mode.LIVE:Mode.PARTIAL;
    }
    static long demand(long fixed,long servers){return PowerEngine.sum(fixed,servers);}
    static boolean funded(long demand,long creative,long paid,boolean require){
        return !require||demand>=0&&creative>=0&&paid>=0&&(demand<=creative||demand-creative<=paid);
    }
    static long externalDemand(long demand,long creative){return Math.max(0,demand-creative);}
    /** A chunk is a coordinate pair, encoded without truncation; one ticket per distinct chunk. */
    static long chunk(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    static Set<Long> bounded(Collection<Long> wanted,int limit){
        if(limit<0)throw new IllegalArgumentException("limit");
        Set<Long> result=new LinkedHashSet<>();
        for(long p:wanted){if(result.size()>=limit)break;result.add(p);}
        return result;
    }
}
