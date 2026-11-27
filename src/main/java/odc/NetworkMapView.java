package odc;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
/** Server-side serializer; never sends topology outside the local LAN. */
final class NetworkMapView {
    static NBTTagCompound snapshot(Tiles.NetworkTerminal terminal){World w=terminal.getWorld();PhysicalTopology.View v=PhysicalNetwork.view(w,terminal.getPos());BandwidthEngine.Result flow=PhysicalNetwork.bandwidth(w);NetworkMonitor monitor=new NetworkMonitor(v,PhysicalNetwork.logicalView(w,terminal.getPos()),flow);
        NBTTagCompound root=new NBTTagCompound();root.setUniqueId("Self",terminal.networkId);root.setDouble("Wan",monitor.wan);root.setDouble("Lan",monitor.lan);root.setDouble("Free",monitor.ingressFree);root.setInteger("Active",monitor.active);root.setInteger("Unavailable",monitor.unavailable);root.setInteger("Boundary",monitor.boundary);root.setBoolean("Complete",monitor.complete);
        NBTTagList nodes=new NBTTagList(),links=new NBTTagList();for(NetworkMonitor.Node n:monitor.nodes.values()){PhysicalTopology.Device d=n.state.device;NBTTagCompound t=new NBTTagCompound();t.setUniqueId("Id",d.id);t.setInteger("Type",d.type.ordinal());t.setString("Name",limit(d.name));t.setString("Model",limit(d.model));t.setInteger("Capacity",d.capacityMbps);t.setInteger("State",!n.verified||n.usage==null?0:n.state.available?2:1);t.setDouble("Used",n.usage==null||!n.verified?-1:n.usage.served);t.setDouble("Free",n.usage==null||!n.verified?-1:n.usage.free);t.setDouble("Wan",n.usage==null||!n.verified?-1:n.usage.wan);t.setDouble("Lan",n.usage==null||!n.verified?-1:n.usage.lan);t.setString("Pos",d.pos.toString());t.setInteger("Slot",d.slot);t.setInteger("Port",d.port);t.setString("Reason",String.join(",",n.reasons));NBTTagList bottlenecks=new NBTTagList();t.setInteger("LimitCount",n.bottlenecks.size());for(UUID id:n.bottlenecks){if(bottlenecks.tagCount()>=128)break;NBTTagCompound z=new NBTTagCompound();z.setUniqueId("Id",id);bottlenecks.appendTag(z);}t.setTag("Limits",bottlenecks);
            if(d.type==PhysicalTopology.Type.SERVER&&n.state.loaded){TileEntity tile=w.getTileEntity(PhysicalNetwork.block(d.pos));if(tile instanceof Tiles.Rack){Tiles.Rack r=(Tiles.Rack)tile;if(r.isServer(d.slot)){ItemStack s=r.slots.get(d.slot);int[] spec=Hardware.spec(s);Part board=Part.at(spec[ServerSpec.BOARD]);String nic=board.tier==3||board.sockets==2?"Integrated 1G":"";int best=nic.isEmpty()?0:1000;for(int i=ServerSpec.PCIE;i<ServerSpec.PSU;i++){Part p=Part.at(spec[i]);if(p.kind==Part.Kind.NIC&&p.mbps>=best){best=p.mbps;nic=p.name;}}t.setString("NIC",nic);}}}
            nodes.appendTag(t);
        }
        for(LogicalLinks.Link e:monitor.links){NBTTagCompound t=new NBTTagCompound();t.setUniqueId("Id",e.id);t.setUniqueId("From",e.from);t.setUniqueId("To",e.to);t.setInteger("Kind",e.kind.ordinal());t.setInteger("Tier",e.tier);t.setInteger("Capacity",e.capacityMbps);t.setInteger("State",e.state.ordinal());BandwidthEngine.Usage u=flow.links.get(e.id);t.setDouble("Used",u==null||e.state==LogicalLinks.State.UNVERIFIED?-1:u.served);t.setDouble("Free",u==null||e.state==LogicalLinks.State.UNVERIFIED?-1:u.free);links.appendTag(t);}
        root.setTag("Nodes",nodes);root.setTag("Links",links);return root;
    }
    private static String limit(String s){return s.substring(0,Math.min(128,s.length()));}
}
