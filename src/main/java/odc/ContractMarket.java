package odc;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraftforge.common.config.Configuration;

/** Offers, costs and receipts share the WorldSavedData transaction with the company wallet. */
final class ContractMarket {
    NBTTagCompound data=new NBTTagCompound();
    final Random random=new Random();
    static double scorePrice=.01,mineFactor=1,serviceFactor=1,scoreFactor=1;
    static final int[] TIER={50,35,15},SIZE={30,35,20,12,3},RULE={30,20,15,12,8,7,5,3};
    static final double[] TARGET={.5,1,3,10,25},VALUE={.5,1,3.3,12,32};
    static final double[] FEE={0,.2,0,0,.15,.25,0,.4},PAY={1,1.5,1.5,1.4,2,2.2,2,3.5};
    static final boolean[] TIMED={false,false,true,false,true,false,true,true},CONTINUOUS={false,false,false,true,false,true,true,true};
    static final String[] CLIENTS={"Meridian Compute","Northbyte Labs","Copperline Media","Orchid Storage","Atlas Dynamics","Polar Relay"};
    static void configure(Configuration c){
        scorePrice=c.getFloat("NCoinPerScore","tasks",.01f,0,100,"Base contract price");
        mineFactor=c.getFloat("miningFactor","tasks",1,0,100,"KriptoCoin per 30 seconds");
        serviceFactor=c.getFloat("serviceFactor","tasks",1,0,100,"NCoin per 10 minutes");
        scoreFactor=c.getFloat("scoreFactor","tasks",1,0,100,"Contract score per second");
    }
    int pick(int[] weights){int n=random.nextInt(Arrays.stream(weights).sum());for(int i=0;i<weights.length;i++){n-=weights[i];if(n<0)return i;}throw new AssertionError();}
    NBTTagCompound owner(UUID owner){
        String key=owner.toString();
        if(!data.hasKey(key))data.setTag(key,new NBTTagCompound());
        return data.getCompoundTag(key);
    }
    static NBTTagList list(NBTTagCompound n,String key){
        if(!n.hasKey(key))n.setTag(key,new NBTTagList());
        return n.getTagList(key,10);
    }
    NBTTagCompound offer(int type,int rule,int size,long now) {
        Workload w=Workload.ALL[type];
        NBTTagCompound n=new NBTTagCompound();
        n.setUniqueId("Key",UUID.randomUUID());n.setInteger("Type",type);n.setInteger("Rule",rule);n.setInteger("Size",size);
        n.setString("Company",CLIENTS[random.nextInt(CLIENTS.length)]);
        n.setLong("Goal",Math.round(w.score*TARGET[size]));
        double standard=w.score*scorePrice*1000*VALUE[size];
        n.setLong("Fee",Math.round(standard*FEE[rule]));n.setLong("Pay",Math.round(standard*PAY[rule]));
        n.setLong("Expires",now+(5+random.nextInt(26))*60000L);
        if(TIMED[rule]){
            int[] windows={5,15,30,60,120};
            int i=random.nextInt(windows.length);
            while(i<4 && windows[i]*60*w.x*32<n.getLong("Goal"))i++;
            n.setLong("Duration",windows[i]*60000L);n.setInteger("Bonus",random.nextInt(31));
        }
        n.setBoolean("Fixed",CONTINUOUS[rule]&&random.nextBoolean());
        n.setBoolean("Pinned",CONTINUOUS[rule]&&random.nextBoolean());
        n.setBoolean("Single",random.nextInt(5)==0);
        n.setInteger("Grace",new int[]{0,30,60,120}[random.nextInt(4)]*1000);
        return n;
    }
    void supply(UUID owner,long now){
        NBTTagCompound book=owner(owner);
        if(now<book.getLong("Refresh"))return;
        book.setLong("Refresh",now+60000);
        NBTTagList offers=list(book,"Offers");
        for(int i=offers.tagCount()-1;i>=0;i--)if(offers.getCompoundTagAt(i).getLong("Expires")<=now)offers.removeTag(i);
        while(offers.tagCount()<20){
            int tier=pick(TIER)+1,type;
            do{type=9+random.nextInt(12);}while(Workload.ALL[type].tier!=tier);
            offers.appendTag(offer(type,pick(RULE),pick(SIZE),now));
        }
    }
    boolean accept(TaskData d,UUID owner,UUID wallet,UUID key,long now){
        NBTTagList offers=list(owner(owner),"Offers");
        for(int i=0;i<offers.tagCount();i++){
            NBTTagCompound o=offers.getCompoundTagAt(i);
            if(!o.getUniqueId("Key").equals(key))continue;
            long fee=o.getLong("Fee");
            if(now>=o.getLong("Expires") || fee>0 && d.wallet(wallet)[0]<fee)return false;
            TaskData.Job j=new TaskData.Job(d.next++,o.getInteger("Type"),wallet,owner);
            j.terms.merge(o);j.terms.setLong("Accepted",now);
            if(o.getLong("Duration")>0)j.terms.setLong("Deadline",now+o.getLong("Duration"));
            offers.removeTag(i);d.wallet(wallet)[0]-=fee;d.jobs.add(j);d.markDirty();return true;
        }
        return false;
    }
    static long payout(TaskData.Job j,long now){
        long duration=j.terms.getLong("Duration"),pay=j.terms.getLong("Pay");
        return pay+(duration<=0?0:Math.round(pay*j.terms.getInteger("Bonus")*.01*Math.max(0,j.terms.getLong("Deadline")-now)/duration));
    }
    void record(TaskData d,TaskData.Job j,String status,long penalty){
        NBTTagCompound n=j.terms.copy();n.setString("Status",status);n.setLong("End",d.now());
        n.setInteger("Type",j.type);n.setLong("Penalty",penalty);n.setLong("Received",j.terms.getLong("Received"));
        NBTTagList history=list(owner(j.owner),"History");
        if(history.tagCount()>=100)history.removeTag(0);history.appendTag(n);d.markDirty();
    }
    void end(TaskData d,TaskData.Job j,String status,long penalty){
        if(j.terms.hasKey("End"))return;
        j.retained.clear();j.enabled=false;j.cachedRate=0;j.machines=Collections.emptySet();j.terms.setBoolean("End",true);
        record(d,j,status,penalty);
    }
    void tick(TaskData d,long now){
        d.jobs.removeIf(j->j.terms.hasKey("End"));
        d.jobs.removeIf(j->j.terms.getBoolean("Finished"));
        for(TaskData.Job j:d.jobs){
            long deadline=j.terms.getLong("Deadline");
            if(deadline>0 && now>=deadline){end(d,j,"deadline",0);continue;}
            if(Workload.contract(j.type) && CONTINUOUS[j.terms.getInteger("Rule")] && j.terms.hasKey("Start") && j.cachedRate==0){
                long idle=j.terms.getLong("Idle")+d.elapsedMillis;
                j.terms.setLong("Idle",idle);
                if(idle>j.terms.getInteger("Grace"))end(d,j,"downtime",0);
            }
        }
    }
}
