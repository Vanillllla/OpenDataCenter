package odc;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Physical inventory and persistent reservation keys. */
final class PoolResources {
    static final String DEFAULT="Default";
    static String name(String raw) {
        if(raw==null)return null;
        String s=raw.trim();
        if(s.isEmpty())return DEFAULT;
        if(s.length()>32||s.chars().anyMatch(c->Character.isISOControl(c)||c==167))return null;
        return s;
    }
    static UUID id(UUID owner,String name) {
        return UUID.nameUUIDFromBytes((String.valueOf(owner)+"\n"+name).getBytes(StandardCharsets.UTF_8));
    }
    enum Kind { CPU,GPU,RAM,HDD,SSD,NVME,NETWORK }
    static final class Device {
        final UUID server;final Kind kind;final int slot,unit,model,capacity,speed,vram;
        Device(UUID server,Kind kind,int slot,int unit,int model,int capacity,int speed,int vram){
            this.server=Objects.requireNonNull(server);this.kind=kind;this.slot=slot;this.unit=unit;this.model=model;this.capacity=capacity;this.speed=speed;this.vram=vram;
        }
        String key(){return server+":"+kind+":"+slot+":"+unit+":"+model;}
        String family(){return kind+":"+model+":"+speed+":"+vram;}
    }
    /** One physical core/chip per row; RAM and disk rows hold GB, network holds Mbit/s. */
    static List<Device> catalog(UUID server,int[] parts){
        List<Device> out=new ArrayList<>();
        for(int slot=0;slot<parts.length;slot++){
            Part p=Part.at(parts[slot]);
            switch(p.kind){
            case CPU: for(int k=0;k<p.cores;k++)out.add(new Device(server,Kind.CPU,slot,k,p.index,1,p.mhz,0));break;
            case GPU: for(int k=0;k<p.chips;k++)out.add(new Device(server,Kind.GPU,slot,k,p.index,1,p.mhz,p.gb));break;
            case RAM: out.add(new Device(server,Kind.RAM,slot,0,p.index,p.gb,Math.min(p.mhz,ServerSpec.ramClock(parts)),0));break;
            case HDD: case SSD: case NVME: case PCIE:
                out.add(new Device(server,p.kind==Part.Kind.HDD?Kind.HDD:p.kind==Part.Kind.SSD?Kind.SSD:Kind.NVME,slot,0,p.index,p.gb,p.speed,0));break;
            default: break;
            }
        }
        int nic=ServerSpec.nic(parts);if(nic>0)out.add(new Device(server,Kind.NETWORK,-1,0,0,nic,nic,0));
        return out;
    }
    /** A validated physical reservation, stored in world data. */
    static final class Binding {
        final UUID server;final Kind kind;final int slot,unit,model,amount,io;
        Binding(UUID server,Kind kind,int slot,int unit,int model,int amount){this(server,kind,slot,unit,model,amount,0);}
        Binding(UUID server,Kind kind,int slot,int unit,int model,int amount,int io){
            if(io<0||io>0&&!FixedResources.storage(kind)||server==null||kind==null||amount<=0||slot< -1||slot>=ServerSpec.COUNT||unit<0||model<0)throw new IllegalArgumentException("Invalid resource binding");
            this.server=server;this.kind=kind;this.slot=slot;this.unit=unit;this.model=model;this.amount=amount;this.io=io;
        }
        boolean matches(Device d){return server.equals(d.server)&&kind==d.kind&&slot==d.slot&&unit==d.unit&&model==d.model&&amount<=d.capacity;}
        boolean storage(){return kind==Kind.HDD||kind==Kind.SSD||kind==Kind.NVME;}
    }
}
