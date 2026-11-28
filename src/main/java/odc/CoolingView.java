package odc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Read-only projection of the existing scheduler; never simulates or reserves power.
 * The scope is loaded, connected servers of this DataOS owner. Shared loops are counted once. */
final class CoolingView {
    private CoolingView(){}
    static int milli(double t){return Double.isFinite(t)?(int)Math.round(t*1000):-1;}
    static ThermalEngine.State state(Menus.Target t){ThermalRuntime.Entry e=t.rack.thermal[t.bay];return e!=null&&e.item==t.item?e.state:null;}
    static void server(NBTTagCompound row,Menus.Target t){
        ThermalEngine.State s=state(t);
        int[] temperatures=s==null?new int[0]:s.snapshot();
        if(s==null&&t.item.hasTagCompound())temperatures=t.item.getTagCompound().getIntArray(HardwareDamage.TEMPS);
        if(temperatures.length==ServerSpec.COUNT+2)row.setIntArray("Temperatures",temperatures);
        row.setBoolean("LiveTemperature",s!=null);
        row.setLong("BrokenMask",HardwareDamage.mask(t.item));
        row.setInteger("ShutdownReason",t.rack.fault[t.bay]);
        ChassisSpec chassis=Hardware.chassis(t.item);
        if(chassis!=null)row.setString("Chassis",chassis.id);
        ThermalProfile profile=s==null?Hardware.thermalProfile(t.item):s.profile;
        row.setInteger("InternalLimit",profile.internalLimitC);
        row.setInteger("LiquidRated",profile.liquidRatedWatts);
        row.setBoolean("Manifold",t.rack.manifold);
        LiquidNetwork.Contour c=LiquidNetwork.contour(t.rack.getWorld(),t.rack.getPos());
        if(c!=null&&c.loop!=null){row.setBoolean("LiquidConnected",profile.liquidRatedWatts>0&&!c.chillers.isEmpty());row.setInteger("LiquidTemperature",milli(c.loop.temperature()));row.setLong("LiquidCapacity",Math.round(c.loop.capacity));}
    }
    static NBTTagCompound snapshot(PanelMenu menu,NBTTagCompound out,List<Menus.Target> servers,TaskData data){
        long now=menu.tile.getWorld().getTotalWorldTime();
        double heat=0,air=0,liquid=0,capacity=0,used=0,sum=0,peak=0;
        int known=0,nodes=0,hot=0,scope=0;
        Set<LiquidNetwork.Contour> seen=Collections.newSetFromMap(new IdentityHashMap<LiquidNetwork.Contour,Boolean>());
        List<LiquidNetwork.Contour> loops=new ArrayList<>();
        for(Menus.Target t:servers){
            scope+=ClusterScheduler.serverId(t.item).hashCode();
            ThermalEngine.State s=state(t);
            boolean fault=HardwareDamage.fault(t.item)!=0;
            if(s!=null){
                double maximum=Math.max(s.internal,s.air);sum+=s.internal+s.air;nodes+=2;
                boolean limit=s.profile.internalLimitC>0&&s.internal>=s.profile.internalLimitC;
                for(int slot:s.slots){double temp=s.temperature(slot);maximum=Math.max(maximum,temp);sum+=temp;nodes++;limit|=s.profile.criticalC(slot)>0&&temp>=s.profile.criticalC(slot);}
                peak=Math.max(peak,maximum);known++;fault|=limit;
                heat+=s.generatedWatts;air+=Math.max(0,s.ambientWatts);liquid+=s.liquidWatts;
            }
            if(fault)hot++;
            LiquidNetwork.Contour c=LiquidNetwork.contour(t.rack.getWorld(),t.rack.getPos());
            if(c!=null&&c.loop!=null&&seen.add(c))loops.add(c);
        }
        int total=loops.size();for(LiquidNetwork.Contour c:loops)total+=c.chillers.size();
        int start=CoolingUiState.start(menu.scrollRow,total),index=0;
        NBTTagList rows=new NBTTagList();int devices=0;
        for(int i=0;i<loops.size();i++){
            LiquidNetwork.Contour c=loops.get(i);capacity+=c.loop.capacity;used+=c.loop.accepted;
            if(CoolingUiState.inPage(index++,start)){
                NBTTagCompound r=new NBTTagCompound();r.setInteger("Loop",i+1);r.setInteger("Temperature",milli(c.loop.temperature()));r.setLong("Capacity",Math.round(c.loop.capacity));r.setLong("Used",Math.round(c.loop.accepted));r.setInteger("Chillers",c.chillers.size());rows.appendTag(r);
            }
            for(Tiles.Chiller ch:c.chillers){
                devices++;
                if(!CoolingUiState.inPage(index++,start))continue;
                NBTTagCompound r=new NBTTagCompound();r.setInteger("Loop",i+1);r.setInteger("Tier",ch.tier());BlockPos p=ch.getPos();r.setIntArray("Position",new int[]{p.getX(),p.getY(),p.getZ()});
                r.setLong("Capacity",Math.round(ch.confirmedCoolingTick!=Long.MIN_VALUE&&now>=ch.confirmedCoolingTick&&now-ch.confirmedCoolingTick<=20&&ch.online?ch.confirmedCooling:0));r.setInteger("Rated",LiquidCore.rated(ch.tier()));r.setBoolean("Powered",ch.online);rows.appendTag(r);
            }
        }
        int avg=known==0?-1:milli(sum/nodes),max=known==0?-1:milli(peak);
        if(known>0)menu.coolingHistory.sample(now,scope,avg,max);else menu.coolingHistory.reset();
        out.setIntArray("MeanHistory",known==0?new int[0]:menu.coolingHistory.values(false));
        out.setIntArray("MaxHistory",known==0?new int[0]:menu.coolingHistory.values(true));
        out.setInteger("Servers",servers.size());out.setInteger("Measured",known);out.setInteger("Overheated",hot);
        out.setInteger("Mean",avg);out.setInteger("Max",max);out.setLong("Heat",Math.round(heat));out.setLong("AirUsed",Math.round(air));out.setLong("ServerLiquid",Math.round(liquid));
        out.setLong("Capacity",Math.round(capacity));out.setLong("Used",Math.round(used));out.setInteger("Loops",loops.size());out.setInteger("Chillers",devices);
        out.setTag("Rows",rows);out.setInteger("Count",total);out.setInteger("TotalRows",total);out.setInteger("Start",start);
        NBTTagList events=new NBTTagList();
        for(EventJournal.Entry e:data.recentEvents(menu.tile,data.journal.limit())){
            if(!e.code.contains("thermal_shutdown")&&!e.code.contains("thermal_damage"))continue;
            NBTTagCompound r=new NBTTagCompound();r.setUniqueId("Key",e.id);r.setString("Code",e.code);r.setLong("Last",e.last);events.appendTag(r);
            if(events.tagCount()>=CoolingUiState.EVENT_LIMIT)break;
        }
        out.setTag("ThermalEvents",events);return out;
    }
}
