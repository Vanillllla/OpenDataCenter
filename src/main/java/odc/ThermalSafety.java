package odc;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import java.util.Locale;
import static odc.EventJournal.*;

/** Live-world adapter; called once, after final funded heat exchange and before
 * task settlement. Future virtual adapter uses the same pure protection rules. */
final class ThermalSafety {
    private ThermalSafety(){}
    static void check(Tiles.Rack rack,TaskData data){
        for(int bay=0;bay<rack.capacity();bay++){
            ThermalRuntime.Entry entry=rack.thermal[bay];
            if(entry==null)continue;
            ItemStack item=rack.slots.get(bay);
            int bit=1<<bay,latched=HardwareDamage.fault(item);
            if(latched!=0){
                // Update the persisted restart guard at a bounded cadence;
                // the in-memory state is always used for live restart.
                if(rack.getWorld().getTotalWorldTime()%400==0){ThermalRuntime.checkpoint(rack,bay);rack.markDirty();}
            }
            boolean running=(rack.enabledMask&rack.poweredMask&bit)!=0;
            ThermalProtection.Accident accident=ThermalProtection.evaluate(entry.state,running,latched!=0,entry.broken,ODC.thermalFailurePercent,rack.getWorld().rand::nextInt);
            if(accident==null)continue;
            NonNullList<ItemStack> parts=Hardware.parts(item);
            long changed=0;
            for(int slot=0;slot<parts.size();slot++)if((accident.damage&(1L<<slot))!=0&&HardwareDamage.mark(parts.get(slot)))changed|=1L<<slot;
            int reason=changed==0?ComputeEngine.OVERHEAT:ComputeEngine.HARDWARE_FAILURE;
            // Latch before any callbacks; keep cached physical profile (only NBT
            // damage changed). Never create or remove component items.
            HardwareDamage.latch(item,reason);
            if(changed!=0){Hardware.writeParts(item,parts);entry.hardware=item.getTagCompound().getCompoundTag("Hardware");entry.broken|=changed;}
            ThermalRuntime.checkpoint(rack,bay);
            ClusterScheduler.trip(rack,bay,reason,data,false);
            rack.cpuUsed[bay]=rack.gpuUsed[bay]=rack.ramUsed[bay]=rack.diskUsed[bay]=rack.netUsed[bay]=rack.work[bay]=0;
            rack.activeJobs.remove(bay);
            ThermalProtection.Limit hit=accident.limit;
            String detail=String.format(Locale.ROOT,"%s (slot %d): %.1f / %d C; damaged: %d",entry.state.profile.part(hit.slot).name,hit.slot,hit.actual,hit.critical,Long.bitCount(changed));
            EventSystem.rack(rack,bay,Level.CRITICAL,Category.COOLING,"thermal_shutdown",detail);
            // One event per damaged item prevents the 240-character journal
            // limit from silently truncating a list of many components.
            for(int slot=0;slot<parts.size();slot++)if((changed&(1L<<slot))!=0){
                Part model=entry.state.profile.part(slot);
                EventSystem.rack(rack,bay,Level.ERROR,Category.HARDWARE,"thermal_damage",
                    String.format(Locale.ROOT,"%s (slot %d) broken; %.1f / %d C",model.name,slot,ThermalProtection.temperature(entry.state,slot),model.criticalC));
            }
            rack.changed();
        }
    }
}
