package odc;

import java.util.*;

/** A non-persistent receipt ledger for one 20-tick demand window. All transformers
 * on a grid share the same outstanding demand, even for different external units.
 * Receipts cannot be spent twice or carried forward as stored energy. */
final class ExternalWindow {
    final long demandMilliJoules;
    private long paidMilliJoules;
    private boolean closed;
    final Map<Object,Receipt> receipts=new IdentityHashMap<>();
    static final class Receipt {
        final long limit;
        long mj,he,fe;
        Receipt(long limit){this.limit=limit;}
    }
    ExternalWindow(long demandWatts){demandMilliJoules=TransformerCore.energyMilliJoules(demandWatts,20);}
    void add(Object source,long ratedWatts){if(closed)throw new IllegalStateException();receipts.putIfAbsent(source,new Receipt(TransformerCore.energyMilliJoules(ratedWatts,20)));}
    long remaining(Object source){
        Receipt r=receipts.get(source);
        return closed||r==null?0:Math.max(0,Math.min(demandMilliJoules-paidMilliJoules,r.limit-r.mj));
    }
    long offer(Object source,long units,int rate,TransformerCore.Input input,boolean simulate){
        if(units<=0||rate<=0)return 0;
        long room=remaining(source);
        if(room==0)return 0;
        // Round up only the final indivisible unit; any excess is lost, not banked.
        long accepted=Math.min(units,room/rate+(room%rate==0?0:1));
        if(!simulate){
            long funded=Math.min(room,accepted*(long)rate);
            Receipt r=receipts.get(source);r.mj+=funded;paidMilliJoules+=funded;
            if(input==TransformerCore.Input.HE)r.he+=accepted;else r.fe+=accepted;
        }
        return accepted;
    }
    long paidWatts(){return paidMilliJoules/1000;}
    boolean sufficient(){return paidMilliJoules>=demandMilliJoules;}
    void close(){closed=true;}
}
