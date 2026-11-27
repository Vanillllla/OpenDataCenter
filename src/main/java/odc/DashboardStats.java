package odc;

import java.util.*;

/** Read-only resource accounting. Does not allocate, release or move any binding. */
final class DashboardStats {
    static final int CPU=0,GPU=1,RAM=2,NVME=3,SSD=4,HDD=5,NET=6,SIZE=7;
    static int index(PoolResources.Kind k){switch(k){case CPU:return CPU;case GPU:return GPU;case RAM:return RAM;case NVME:return NVME;case SSD:return SSD;case HDD:return HDD;default:return NET;}}
    static final class Totals {
        final long[] total=new long[SIZE],used=new long[SIZE],free=new long[SIZE],offlineHeld=new long[SIZE];
        void add(int i,long capacity,long reserved,boolean available){long t=Math.max(0,capacity),u=Math.min(t,Math.max(0,reserved));total[i]+=t;used[i]+=u;if(available)free[i]+=t-u;else offlineHeld[i]+=u;}
        void add(Totals a){for(int i=0;i<SIZE;i++){total[i]+=a.total[i];used[i]+=a.used[i];free[i]+=a.free[i];offlineHeld[i]+=a.offlineHeld[i];}}
        long unavailable(int i){return total[i]-used[i]-free[i];}
    }
    static Map<String,Integer> reservations(Collection<? extends TaskEngine.Task> jobs){Map<String,Integer> held=new HashMap<>();for(TaskEngine.Task j:jobs)for(PoolResources.Binding b:j.bindings)if(j.enabled||b.storage())held.merge(FixedResources.key(b),b.amount,(a,c)->(int)Math.min(Integer.MAX_VALUE,(long)a+c));return held;}
    static Totals inventory(List<PoolResources.Device> devices,Map<String,Integer> held,boolean available,int port){Totals t=new Totals();for(PoolResources.Device d:devices){int i=index(d.kind);int u=Math.min(d.capacity,held.getOrDefault(d.key(),0));t.add(i,d.capacity,u,available);if(i==NET)t.free[i]=Math.max(0,Math.min(t.free[i],(long)port-u));}return t;}
    /** A pool shares rack switches and WAN with other pools. Free bandwidth is a maximum, not a reservation. */
    static long networkFree(long wan,long totalHeld,Map<?,Long> candidate,Map<?,Long> capacities,Map<?,Long> held){long room=0;for(Map.Entry<?,Long> e:candidate.entrySet())room+=Math.min(e.getValue(),Math.max(0,capacities.getOrDefault(e.getKey(),0L)-held.getOrDefault(e.getKey(),0L)));return Math.min(room,Math.max(0,wan-totalHeld));}
    /** Gross current recurring income, in milli-coins per hour. No completed/future one-off contracts. */
    static long hourly(int type,long rate,boolean running,double mineFactor,double serviceFactor){if(!running||rate<=0||Workload.contract(type))return 0;double factor=Workload.service(type)?serviceFactor:mineFactor;double perPeriod=Math.round(rate*factor*1000)/(double)TaskEngine.SCALE;return Math.max(0,Math.round(perPeriod*(Workload.service(type)?6:120)));}
}
