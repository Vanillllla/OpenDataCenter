package odc;
import net.minecraft.nbt.*;
/** Shared server snapshot encoding tested through the actual window message codec. */
final class PowerStatsWire {
    static void write(NBTTagCompound out,PowerStats.History history,long now,boolean fresh){
        PowerStats.Sample last=history==null?null:history.latest;
        fresh=fresh&&last!=null;
        out.setBoolean("Sample",fresh);out.setBoolean("Funded",fresh&&last.funded);
        long available=fresh?last.available:0,draw=fresh?last.draw:0;
        out.setLong("Available",available);out.setLong("Draw",draw);out.setLong("Free",PowerStats.free(available,draw));
        for(int i=0;i<PowerStats.SIZE;i++)out.setLong("C"+i,fresh?last.categories[i]:0);
        NBTTagList points=new NBTTagList();
        if(history!=null)for(PowerStats.Sample s:history.points(now)){
            if(s.tick>now)continue;
            NBTTagCompound p=new NBTTagCompound();p.setInteger("Age",(int)(now-s.tick));p.setLong("Available",s.available);p.setLong("Draw",s.draw);p.setBoolean("Known",s.known&&!s.bypass);points.appendTag(p);
        }
        out.setTag("History",points);
    }
}
