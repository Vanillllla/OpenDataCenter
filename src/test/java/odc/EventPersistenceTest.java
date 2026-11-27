package odc;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import static odc.EventJournal.*;
/** Runs against Forge's real NBT classes via verifyEventPersistence. */
public final class EventPersistenceTest {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){UUID owner=UUID.randomUUID(),scope=UUID.randomUUID(),target=UUID.randomUUID(),other=UUID.randomUUID();TaskData d=new TaskData();d.ticks=15;d.record(owner,scope,target,Level.ERROR,Category.RESOURCE,Target.TASK,"fault","lost",-1,0);d.ticks=16;d.record(owner,scope,target,Level.ERROR,Category.RESOURCE,Target.TASK,"fault","lost",-1,0);d.record(owner,other,target,Level.SUCCESS,Category.FINANCE,Target.TASK,"payment","Payment credited",1,1000);d.record(owner,other,target,Level.SUCCESS,Category.FINANCE,Target.TASK,"payment","Payment credited",1,125);d.record(UUID.randomUUID(),scope,target,Level.INFO,Category.SYSTEM,Target.NONE,"private","other owner",-1,0);
        NBTTagCompound n=d.writeToNBT(new NBTTagCompound());TaskData restored=new TaskData();restored.readFromNBT(n.copy());List<Entry> before=d.journal.recent(owner,Arrays.asList(scope,other)),after=restored.journal.recent(owner,Arrays.asList(scope,other));check(before.size()==2&&after.size()==2,"scope / owner isolation");for(int i=0;i<before.size();i++)check(EventSystem.tag(before.get(i)).equals(EventSystem.tag(after.get(i))),"NBT round-trip");check(after.get(0).amount==1125&&after.get(0).repeats==2,"payments");check(after.get(1).first==15&&after.get(1).last==16&&after.get(1).repeats==2,"repeat timestamps");check(restored.journal.recent(owner,Collections.singleton(scope)).size()==1,"split history");System.out.println("PASS EventPersistenceTest");}
}
