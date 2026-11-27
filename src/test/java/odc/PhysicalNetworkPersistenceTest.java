package odc;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;

/** Executed by Gradle with actual Minecraft NBT classes; not part of standalone JDK tests. */
public final class PhysicalNetworkPersistenceTest {
    public static void main(String[] args){
        PhysicalNetwork original=new PhysicalNetwork();
        PhysicalTopology.Pos p=new PhysicalTopology.Pos(-42,64,17),f=new PhysicalTopology.Pos(-41,64,17),a=new PhysicalTopology.Pos(-40,64,17);
        UUID gateway=UUID.randomUUID(),antenna=UUID.randomUUID();
        PhysicalTopology.Device g=new PhysicalTopology.Device(gateway,PhysicalTopology.Type.TERMINAL,"Gateway","T1",p,-1,1000,-1,true,null,-1,-1);
        PhysicalTopology.Device r=new PhysicalTopology.Device(antenna,PhysicalTopology.Type.RADIO,"Radio","radio",a,-1,128,-1,true,null,-1,-1);
        original.topology.put(p,new PhysicalTopology.Cell(PhysicalTopology.Kind.TERMINAL,Collections.singletonList(g)));
        original.topology.put(f,new PhysicalTopology.Cell(PhysicalTopology.Kind.FIBER,Collections.emptyList()));
        original.topology.put(a,new PhysicalTopology.Cell(PhysicalTopology.Kind.ANTENNA,Collections.singletonList(r)));
        LogicalLinks.Link link=original.uplinks.add(gateway,UUID.randomUUID(),3);
        NBTTagCompound saved=original.writeToNBT(new NBTTagCompound());PhysicalNetwork loaded=new PhysicalNetwork();loaded.readFromNBT(saved);
        if(loaded.uplinks.all().size()!=1||!loaded.uplinks.all().iterator().next().id.equals(link.id)||loaded.uplinks.all().iterator().next().tier!=3)throw new AssertionError("Uplink persistence");
        if(!loaded.topology.cells().equals(original.topology.cells()))throw new AssertionError("Persistent cells or UUIDs changed");
        PhysicalTopology.View v=loaded.topology.view(p,x->false);
        if(v.devices.size()!=2||v.links.size()!=1||v.links.get(0).verified||v.complete||v.devices.get(gateway).available)throw new AssertionError("Saved cells must be known but not verified after restart");
        loaded.topology.put(f,null);if(loaded.topology.view(p,x->true).devices.containsKey(antenna))throw new AssertionError("Deleted cable must not create link");
        PhysicalNetwork second=new PhysicalNetwork();second.readFromNBT(loaded.writeToNBT(new NBTTagCompound()));if(second.topology.view(p,x->true).devices.containsKey(antenna))throw new AssertionError("Deleted cable returned after persistence");
        System.out.println("Physical network NBT persistence PASS");
    }
}
