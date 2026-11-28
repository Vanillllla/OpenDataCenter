package odc;
import java.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.*;
public final class PowerStatsWireTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        PowerStats.History a=new PowerStats.History(),b=new PowerStats.History();
        a.record(new PowerStats.Sample(100,5000000000L,6000000000L,new long[]{3000000000L,30,20,150,30,0},GridProtection.ON,true,false,true));
        b.record(new PowerStats.Sample(100,0,25000,new long[6],GridProtection.TRIPPED,true,false,false));
        UUID key=PowerStats.key(0,Arrays.asList(1L,2L));NBTTagCompound out=new NBTTagCompound();
        out.setInteger("Tab",5);out.setInteger("GridIndex",1);out.setInteger("GridCount",2);out.setUniqueId("GridKey",key);
        PowerStatsWire.write(out,a,120,true);ByteBuf bytes=Unpooled.buffer();
        try{
            new DataOSPackets.View(17,out).toBytes(bytes);
            DataOSPackets.View decoded=new DataOSPackets.View();decoded.fromBytes(bytes);
            check(decoded.window==17&&decoded.tag.equals(out),"actual DataOS View packet round trip");
            check(decoded.tag.getLong("Available")==5000000000L&&decoded.tag.getLong("Draw")==3000000230L,"64 bit values");
            check(decoded.tag.getTagList("History",10).getCompoundTagAt(0).getInteger("Age")==20,"history tick age");
            check(decoded.tag.getUniqueId("GridKey").equals(key),"selected grid identity");
        }finally{bytes.release();}
        PowerStatsWire.write(out,b,120,true);check(out.getLong("Draw")==0&&out.getLong("Available")==0,"isolated tripped grid");
        PowerStatsWire.write(out,a,120,false);check(!out.getBoolean("Sample")&&out.getLong("Available")==0,"stale sample not current");
        System.out.println("PowerStatsWire: passed");
    }
}
