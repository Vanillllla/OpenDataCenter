package odc;
import java.util.*;

/** Pure 0.3 mining math. Other workloads deliberately wait for the 0.4 scheduler. */
public final class ComputeEngine {
    public static final int TICKS=20;
    public static final int IDLE=0,NO_CONTROLLER=1,CONTROLLER_OFFLINE=2,TASK_DISABLED=3,INCOMPATIBLE=4,NO_NETWORK=5,NO_POWER=6,RUNNING=7,THROTTLED=8,MAINTENANCE=9,INCOMPLETE=10,PSU_OVERLOAD=11,OVERHEAT=12,HARDWARE_FAILURE=13;
    private ComputeEngine(){}
    public static final class Profile {
        public final int work,mbps,fePerTick;public final long microMilliPerSecond;
        Profile(int w,int m,int f,long r){work=w;mbps=m;fePerTick=f;microMilliPerSecond=r;}
        public int energy(int n){return n<=0||mbps<=0?0:(int)(((long)fePerTick*TICKS*Math.min(n,mbps)+mbps-1)/mbps);}
        public int bandwidthForEnergy(int e){return fePerTick<=0||mbps<=0?0:(int)Math.min(mbps,Math.max(0L,(long)e*mbps/(fePerTick*TICKS)));}
        public long reward(int n){return mbps==0?0:microMilliPerSecond*Math.min(Math.max(0,n),mbps)/mbps;}
        public int effectiveWork(int n){return mbps==0?0:(int)((long)work*Math.min(Math.max(0,n),mbps)/mbps);}
    }
    static int ceil(int a,int b){return (a+b-1)/b;}
    public static Profile mining(int cpu,int g1,int g2,int divisor){if(cpu<1||cpu>2||g1<0||g2<0||g1+g2>4)return new Profile(0,0,0,0);int hash=(cpu==1?8:32)+g1*40+g2*160;return new Profile(hash,Math.max(1,ceil(hash,40)),(cpu==1?8:20)+g1*25+g2*85,20000L*hash/Math.max(1,divisor));}
    /** Deterministic proportional integer allocation, rotating tied remainders. */
    public static int[] allocate(int[] demand,int capacity,int rotation){int[] result=new int[demand.length];long total=0;for(int n:demand){if(n<0)throw new IllegalArgumentException("negative demand");total+=n;}if(capacity<=0||total==0)return result;if(total<=capacity)return demand.clone();long[] rem=new long[demand.length];Integer[] order=new Integer[demand.length];int left=capacity;
        for(int i=0;i<demand.length;i++){long product=(long)demand[i]*capacity;result[i]=(int)(product/total);rem[i]=product%total;left-=result[i];order[i]=i;}final int start=Math.floorMod(rotation,Math.max(1,demand.length));Arrays.sort(order,(a,b)->{int cmp=Long.compare(rem[b],rem[a]);return cmp!=0?cmp:Integer.compare(Math.floorMod(a-start,demand.length),Math.floorMod(b-start,demand.length));});for(int i:order)if(left>0&&result[i]<demand[i]){result[i]++;left--;}return result;}
    public static final class Plan {public final int[] local,energyLimited,bandwidth,energyCost;Plan(int n){local=new int[n];energyLimited=new int[n];bandwidth=new int[n];energyCost=new int[n];}}
    /** Local switch and individual NIC/port caps first, then rack FE, then shared WAN. No rack can borrow another switch's resources. */
    public static Plan plan(Profile[] profiles,int[] rackOf,int[] portCaps,int[] switchCaps,int[] budgets,int wan,int rotation){int n=profiles.length;if(rackOf.length!=n||portCaps.length!=n||switchCaps.length!=budgets.length)throw new IllegalArgumentException("length");Plan out=new Plan(n);List<List<Integer>> groups=new ArrayList<>();for(int k=0;k<budgets.length;k++){if(budgets[k]<0||switchCaps[k]<0)throw new IllegalArgumentException("budget");groups.add(new ArrayList<>());}
        for(int i=0;i<n;i++){if(profiles[i]==null||rackOf[i]<0||rackOf[i]>=groups.size()||portCaps[i]<0)throw new IllegalArgumentException("request");groups.get(rackOf[i]).add(i);}
        for(int k=0;k<groups.size();k++){List<Integer> ids=groups.get(k);int m=ids.size();int[] wants=new int[m];for(int j=0;j<m;j++){int i=ids.get(j);wants[j]=Math.min(profiles[i].mbps,portCaps[i]);}int[] local=allocate(wants,switchCaps[k],rotation);int[] costs=new int[m];for(int j=0;j<m;j++){int i=ids.get(j);out.local[i]=local[j];costs[j]=profiles[i].energy(local[j]);}int[] shares=allocate(costs,budgets[k],rotation);int remaining=budgets[k];
            for(int j=0;j<m;j++){int i=ids.get(j);out.energyLimited[i]=Math.min(local[j],profiles[i].bandwidthForEnergy(shares[j]));remaining-=profiles[i].energy(out.energyLimited[i]);}
            // Reuse integer rounding residue without exceeding any request's local allocation.
            if(m>0){int start=Math.floorMod(rotation,m);for(int off=0;off<m&&remaining>0;off++){int j=(start+off)%m,i=ids.get(j);Profile p=profiles[i];int old=out.energyLimited[i],oldCost=p.energy(old);int next=Math.min(local[j],p.bandwidthForEnergy((int)Math.min(Integer.MAX_VALUE,(long)oldCost+remaining)));if(next>old){out.energyLimited[i]=next;remaining-=p.energy(next)-oldCost;}}}}
        int[] global=allocate(out.energyLimited,wan,rotation);for(int i=0;i<n;i++){out.bandwidth[i]=global[i];out.energyCost[i]=profiles[i].energy(global[i]);}return out;}
}
