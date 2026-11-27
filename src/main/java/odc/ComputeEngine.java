package odc;

import java.util.*;

/** Pure Java resource math. No Minecraft or real cryptocurrency dependencies. */
public final class ComputeEngine {
    public static final int TICKS = 20;
    public static final int NONE=0, MINING=1, BANK=2, AI=3;
    public static final int IDLE=0, NO_CONTROLLER=1, CONTROLLER_OFFLINE=2,
            TASK_DISABLED=3, INCOMPATIBLE=4, NO_NETWORK=5, NO_POWER=6, RUNNING=7, THROTTLED=8, MAINTENANCE=9, INCOMPLETE=10;
    private ComputeEngine() {}

    /** One physical server's demands for a single independent task. */
    public static final class Profile {
        public final int work, mbps, fePerTick;
        public final long microMilliPerSecond;
        Profile(int work,int mbps,int fePerTick,long reward) {
            this.work=work;this.mbps=mbps;this.fePerTick=fePerTick;this.microMilliPerSecond=reward;
        }
        public int energy(int allocated) {
            if(allocated<=0||mbps<=0)return 0;
            return (int)(((long)fePerTick*TICKS*Math.min(allocated,mbps)+mbps-1)/mbps);
        }
        public int bandwidthForEnergy(int fe) {
            if(fePerTick<=0||mbps<=0)return 0;
            return (int)Math.min(mbps,Math.max(0L,(long)fe*mbps/(fePerTick*TICKS)));
        }
        public long reward(int allocated) {
            return mbps==0?0:microMilliPerSecond*Math.min(Math.max(0,allocated),mbps)/mbps;
        }
        public int effectiveWork(int allocated) {
            return mbps==0?0:(int)((long)work*Math.min(Math.max(0,allocated),mbps)/mbps);
        }
    }
    static int ceil(int a,int b){return (a+b-1)/b;}
    /** CPU T1/T2, GPU T1 4 GB / T2 16 GB. AI jobs require >=8 GB on each participating GPU. */
    public static Profile profile(int job,int cpu,int basic,int pro,int miningDivisor) {
        return profile(job,cpu,basic,pro,miningDivisor,1,15);
    }
    public static Profile profile(int job,int cpu,int basic,int pro,int miningDivisor,int bankMilli,int aiMilli) {
        if((cpu!=1&&cpu!=2)||basic<0||pro<0||basic+pro>4) return new Profile(0,0,0,0);
        int cpuPower=cpu==1?8:20;
        switch(job) {
            case MINING: {
                int hash=basic*40+pro*160;
                return hash==0?new Profile(0,0,0,0):new Profile(hash,ceil(hash,40),8+ceil(cpuPower,4)+25*basic+85*pro,20000L*hash/Math.max(1,miningDivisor));
            }
            case BANK: {
                int cu=cpu==1?40:160;
                return new Profile(cu,ceil(cu,10),8+cpuPower,1000L*cu*Math.max(0,bankMilli));
            }
            case AI: {
                int vram=pro*16;
                return vram==0?new Profile(0,0,0,0):new Profile(vram,vram*4,8+ceil(cpuPower,2)+85*pro,1000L*vram*Math.max(0,aiMilli));
            }
            default: return new Profile(0,0,0,0);
        }
    }
    /** Exact proportional allocation. No overbooking, no floating point, deterministic rotating ties. */
    public static int[] allocate(int[] demand,int capacity,int rotation) {
        int[] result=new int[demand.length];
        long total=0;for(int d:demand){if(d<0)throw new IllegalArgumentException("negative demand");total+=d;}
        if(capacity<=0||total==0)return result;
        if(total<=capacity)return Arrays.copyOf(demand,demand.length);
        long[] remainders=new long[demand.length];int left=capacity;
        Integer[] order=new Integer[demand.length];
        for(int i=0;i<demand.length;i++){
            long product=(long)demand[i]*capacity;
            result[i]=(int)(product/total);remainders[i]=product%total;left-=result[i];order[i]=i;
        }
        final int start=Math.floorMod(rotation,Math.max(1,demand.length));
        Arrays.sort(order,(a,b)->{int c=Long.compare(remainders[b],remainders[a]);return c!=0?c:Integer.compare(Math.floorMod(a-start,demand.length),Math.floorMod(b-start,demand.length));});
        for(int i:order)if(left>0&&result[i]<demand[i]){result[i]++;left--;}
        return result;
    }
    public static int networkCapacity(int station,int switchMbps){return Math.max(0,Math.min(station,switchMbps));}
    /** Result of one one-second compute batch. Entries use the input request order. */
    public static final class Plan {
        public final int[] bandwidth, energyLimitedBandwidth, energyCost;
        Plan(int n){bandwidth=new int[n];energyLimitedBandwidth=new int[n];energyCost=new int[n];}
    }
    /** First cap each rack by its own FE budget, then share the domain's network budget.
     * The sum of charged FE per rack never exceeds its budget; the sum of allocated Mbps never exceeds capacity. */
    public static Plan plan(Profile[] profiles,int[] rackOf,int[] budgets,int capacity,int rotation){
        if(profiles.length!=rackOf.length)throw new IllegalArgumentException("request length");
        Plan out=new Plan(profiles.length);
        List<List<Integer>> byRack=new ArrayList<>();
        for(int budget:budgets){if(budget<0)throw new IllegalArgumentException("negative budget");byRack.add(new ArrayList<>());}
        for(int i=0;i<profiles.length;i++){
            if(profiles[i]==null||rackOf[i]<0||rackOf[i]>=budgets.length)throw new IllegalArgumentException("bad rack or profile");
            byRack.get(rackOf[i]).add(i);
        }
        for(int rack=0;rack<byRack.size();rack++){
            List<Integer> ids=byRack.get(rack);int[] demand=new int[ids.size()];
            for(int j=0;j<ids.size();j++){Profile p=profiles[ids.get(j)];demand[j]=p.energy(p.mbps);}
            int[] share=allocate(demand,budgets[rack],rotation);
            int remaining=budgets[rack];
            for(int j=0;j<ids.size();j++){int i=ids.get(j);out.energyLimitedBandwidth[i]=profiles[i].bandwidthForEnergy(share[j]);remaining-=profiles[i].energy(out.energyLimitedBandwidth[i]);}
            // Redistribute rounding residue; even a very small FE budget can run one server
            // rather than being divided into unusable fractions. Rotate the first recipient.
            if(!ids.isEmpty())for(int j=0;j<ids.size()&&remaining>0;j++){
                int i=ids.get((Math.floorMod(rotation,ids.size())+j)%ids.size());Profile p=profiles[i];int oldCost=p.energy(out.energyLimitedBandwidth[i]);
                int max=p.bandwidthForEnergy((int)Math.min(Integer.MAX_VALUE,(long)oldCost+remaining));
                if(max>out.energyLimitedBandwidth[i]){out.energyLimitedBandwidth[i]=max;remaining-=p.energy(max)-oldCost;}
            }
        }
        int[] granted=allocate(out.energyLimitedBandwidth,capacity,rotation);
        for(int i=0;i<profiles.length;i++){out.bandwidth[i]=granted[i];out.energyCost[i]=profiles[i].energy(granted[i]);}
        return out;
    }
}
