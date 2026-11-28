package odc;
import java.util.*;
import java.nio.ByteBuffer;

/** Immutable, whole-grid monitoring samples. All values are watts, not requests or nameplate ratings. */
final class PowerStats {
    static final int SERVER=0,SWITCH=1,TERMINAL=2,ANTENNA=3,DATAOS=4,OTHER=5,SIZE=6;
    static final int INTERVAL=100,WINDOW=12000,POINTS=120;
    static UUID key(int dimension,Collection<Long> positions){
        List<Long> sorted=new ArrayList<>(new HashSet<>(positions));Collections.sort(sorted);
        ByteBuffer b=ByteBuffer.allocate(4+8*sorted.size());b.putInt(dimension);for(long p:sorted)b.putLong(p);
        return UUID.nameUUIDFromBytes(b.array());
    }
    static long total(long[] categories){long sum=0;for(long v:categories)sum=PowerEngine.sum(sum,Math.max(0,v));return sum;}
    static long free(long capacity,long draw){return Math.max(0,capacity-draw);}
    static final class Sample {
        final long tick,available,draw,rated;final long[] categories;
        final int state;final boolean known,bypass,funded;
        Sample(long tick,long available,long rated,long[] categories,int state,boolean known,boolean bypass,boolean funded){
            this.tick=tick;this.state=state;this.known=known;this.bypass=bypass;this.funded=funded;this.rated=Math.max(0,rated);
            this.available=state==GridProtection.ON&&known&&!bypass?Math.max(0,available):0;
            this.categories=categories.clone();
            if(state!=GridProtection.ON||!funded&&!bypass)Arrays.fill(this.categories,0);
            draw=total(this.categories);
        }
    }
    static final class History {
        final Deque<Sample> samples=new ArrayDeque<>();
        Sample latest;
        void record(Sample sample){
            if(latest!=null&&sample.tick<latest.tick)samples.clear();
            latest=sample;
            if(sample.tick%INTERVAL==0){
                if(!samples.isEmpty()&&samples.peekLast().tick==sample.tick)samples.removeLast();
                samples.addLast(sample);
            }
            trim(sample.tick);
        }
        void trim(long tick){while(!samples.isEmpty()&&(samples.size()>POINTS||samples.peekFirst().tick<=tick-WINDOW))samples.removeFirst();}
        List<Sample> points(long tick){trim(tick);return new ArrayList<>(samples);}
    }
    static int selected(List<UUID> keys,UUID requested){int i=keys.indexOf(requested);return i>=0?i:keys.isEmpty()?-1:0;}
    static int step(int index,int delta,int count){return count<=0?-1:Math.max(0,Math.min(count-1,index+Integer.signum(delta)));}
}
