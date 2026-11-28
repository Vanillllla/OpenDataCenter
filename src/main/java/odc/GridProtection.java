package odc;
import java.util.*;

/** Persisted cell latches: a split inherits the old state, a merge takes the
 * strongest state, and only explicit reset clears a connected component. */
final class GridProtection {
    static final int ON=0, OFF=1, TRIPPED=2;
    private final Map<Long,Integer> cells=new HashMap<>();
    int state(Collection<Long> footprint){
        int state=ON;for(long cell:footprint)state=Math.max(state,cells.getOrDefault(cell,ON));return state;
    }
    boolean set(Collection<Long> footprint,int state){
        if(state<ON||state>TRIPPED)throw new IllegalArgumentException("state");
        boolean changed=false;
        for(long cell:footprint){
            if(state==ON)changed|=cells.remove(cell)!=null;
            else changed|=!Objects.equals(cells.put(cell,state),state);
        }
        return changed;
    }
    boolean forget(long cell){return cells.remove(cell)!=null;}
    Map<Long,Integer> snapshot(){return new HashMap<>(cells);}
    void restore(Map<Long,Integer> saved){cells.clear();for(Map.Entry<Long,Integer> e:saved.entrySet())if(e.getValue()==OFF||e.getValue()==TRIPPED)cells.put(e.getKey(),e.getValue());}
    static boolean sufficient(long capacity,long demand){return capacity>=0&&demand>=0&&demand<=capacity;}
}
