package odc;

import java.util.Arrays;

/** Slots store catalog indices. Multiple accelerators on one card share its electrical load. */
public final class ServerSpec {
    static final int BOARD=0, CPU=1, RAM=2, SATA=10, NVME=22, PCIE=24, PSU=31, CPU2=35, LEGACY_COUNT=36,
        CPU_COOLER=36, GPU_COOLER=38, FAN=45, COUNT=53;
    static final int READY=0, NO_BOARD=1, NO_CPU=2, NO_RAM=3, NO_DISK=4, INVALID=5, CAPACITY=6, NO_PSU=7;
    static boolean chassis(int u) { return u==1 || u==2 || u==4; }
    static int chassisPcie(int u) { ChassisSpec c=ChassisSpec.legacy(u);return c==null?0:c.pcieSlots; }
    static int[] upgradeParts(int[] p){return p.length==LEGACY_COUNT?Arrays.copyOf(p,COUNT):p;}
    static int first(int slot) {
        if(slot>=FAN)return FAN;
        if(slot>=GPU_COOLER)return GPU_COOLER;
        if(slot>=CPU_COOLER)return CPU_COOLER;
        if(slot<=CPU || slot==CPU2) return slot;
        if(slot<SATA) return RAM;
        if(slot<NVME) return SATA;
        if(slot<PCIE) return NVME;
        return slot<PSU?PCIE:PSU;
    }
    static int capacity(int u,int board,int slot){return capacity(ChassisSpec.legacy(u),board,slot);}
    static int capacity(ChassisSpec c,int board,int slot) {
        if(c==null||slot<0||slot>=COUNT)return 0;
        Part b=Part.at(board);
        switch(first(slot)) {
            case BOARD: return 1;
            case PSU: return c.psuSlots;
            case FAN: return c.fanSlots;
            case CPU_COOLER: return Math.min(c.maxSockets,b.sockets);
            case GPU_COOLER: return c.liquid?Math.min(c.pcieSlots,b.pciSlots):0;
            case CPU: return Math.min(1,b.sockets);
            case CPU2: return Math.max(0,b.sockets-1);
            case RAM: return b.ramSlots;
            case SATA: return b.sataSlots;
            case NVME: return b.m2Slots;
            default: return Math.min(c.pcieSlots,b.pciSlots);
        }
    }
    static boolean fits(int u,int board,int slot){return fits(ChassisSpec.legacy(u),board,slot);}
    static boolean fits(ChassisSpec c,int board,int slot) { return slot>=0 && slot<COUNT && slot-first(slot)<capacity(c,board,slot); }
    static int coolingTarget(int slot){if(slot==CPU_COOLER)return CPU;if(slot==CPU_COOLER+1)return CPU2;if(slot>=GPU_COOLER&&slot<FAN)return PCIE+slot-GPU_COOLER;return -1;}
    static boolean compatible(int slot,Part p,Part b) {
        switch(first(slot)) {
            case BOARD: return p.kind==Part.Kind.BOARD;
            case CPU: case CPU2: return p.kind==Part.Kind.CPU && p.socket==b.socket && p.ddr==b.ddr;
            case RAM: return p.kind==Part.Kind.RAM && p.ddr==b.ddr;
            case SATA: return p.kind==Part.Kind.SSD || p.kind==Part.Kind.HDD;
            case NVME: return p.kind==Part.Kind.NVME;
            case PSU: return p.kind==Part.Kind.PSU;
            // Reserved for 0.6.3.3.
            case CPU_COOLER: case GPU_COOLER: case FAN: return false;
            default: return p.kind==Part.Kind.NIC || p.kind==Part.Kind.GPU || p.kind==Part.Kind.PCIE;
        }
    }
    static int width(Part p) { return p.kind==Part.Kind.EMPTY?0:Math.max(1,p.width); }
    static int occupiedPcie(int[] parts) {
        int total=0;
        for(int i=PCIE;i<PSU;i++) total+=width(Part.at(parts[i]));
        return total;
    }
    /** A PCIe item is stored only at its start; following positions are derived,
     * never stored as placeholder items or duplicated resources. */
    static int pcieOwner(int[] parts,int slot) {
        if(slot<PCIE||slot>=PSU||parts.length!=COUNT)return -1;
        for(int i=PCIE;i<=slot;i++)if(i+width(Part.at(parts[i]))>slot)return i;
        return -1;
    }
    static boolean coveredPcie(int[] parts,int slot) {
        int owner=pcieOwner(parts,slot);return owner>=0&&owner!=slot;
    }
    static boolean slotAvailable(int u,int[] parts,int slot){return slotAvailable(ChassisSpec.legacy(u),parts,slot);}
    static boolean slotAvailable(ChassisSpec c,int[] parts,int slot) {
        if(parts.length!=COUNT||!fits(c,parts[BOARD],slot)||coveredPcie(parts,slot))return false;
        int target=coolingTarget(slot);if(target<0)return true;
        Part p=Part.at(parts[target]);return slot<GPU_COOLER?p.kind==Part.Kind.CPU:p.kind==Part.Kind.GPU&&!coveredPcie(parts,target);
    }
    static boolean validPcie(int u,int[] parts){return validPcie(ChassisSpec.legacy(u),parts);}
    static boolean validPcie(ChassisSpec c,int[] parts) {
        if(c==null||parts.length!=COUNT)return false;
        int limit=PCIE+capacity(c,parts[BOARD],PCIE),end=PCIE;
        for(int i=PCIE;i<PSU;i++)if(parts[i]!=0){
            int w=width(Part.at(parts[i]));
            if(w==0||i<end||i+w>limit)return false;
            end=i+w;
        }
        return true;
    }
    static boolean accepts(int u,int[] parts,int slot,Part candidate){return accepts(ChassisSpec.legacy(u),parts,slot,candidate);}
    static boolean accepts(ChassisSpec c,int[] parts,int slot,Part candidate) {
        if(!slotAvailable(c,parts,slot)||!compatible(slot,candidate,Part.at(parts[BOARD])))return false;
        if(slot==BOARD&&!c.supports(candidate))return false;
        if(slot==BOARD&&parts[BOARD]!=0&&!canRemoveBoard(parts))return false;
        if(first(slot)!=PCIE)return true;
        int end=slot+width(candidate);
        if(end>PCIE+capacity(c,parts[BOARD],PCIE))return false;
        // Exclude the old card at this exact start when replacing it.
        for(int i=PCIE;i<PSU;i++)if(i!=slot&&parts[i]!=0&&i<end&&i+width(Part.at(parts[i]))>slot)return false;
        return true;
    }
    static int state(int u,int[] p){return state(ChassisSpec.legacy(u),p);}
    static int state(ChassisSpec c,int[] p) {
        if(c==null||p.length!=COUNT) return INVALID;
        if(p[BOARD]==0) return NO_BOARD;
        if(!c.supports(Part.at(p[BOARD])))return INVALID;
        for(int i=0;i<COUNT;i++) if(p[i]!=0) {
            if(!compatible(i,Part.at(p[i]),Part.at(p[BOARD]))) return INVALID;
            if(!fits(c,p[BOARD],i)) return CAPACITY;
            if(first(i)!=PCIE&&!slotAvailable(c,p,i))return CAPACITY;
        }
        if(!validPcie(c,p)) return CAPACITY;
        if(p[CPU]==0) return NO_CPU;
        if(ramGB(p)==0) return NO_RAM;
        if(diskGB(p)==0) return NO_DISK;
        return psu(p)==0?NO_PSU:READY;
    }
    static int ramGB(int[] p) {
        int total=0;
        for(int i=RAM;i<SATA;i++) total+=Part.at(p[i]).gb;
        return total;
    }
    static int ramClock(int[] p) {
        int mhz=Integer.MAX_VALUE;
        for(int i=RAM;i<SATA;i++) if(p[i]>0) mhz=Math.min(mhz,Part.at(p[i]).mhz);
        return mhz==Integer.MAX_VALUE?0:mhz;
    }
    static int[] storageGB(int[] p) {
        int[] totals=new int[3];
        for(int i=SATA;i<PSU;i++) {
            Part disk=Part.at(p[i]);
            switch(disk.kind) {
                case NVME: case PCIE: totals[0]+=disk.gb; break;
                case SSD: totals[1]+=disk.gb; break;
                case HDD: totals[2]+=disk.gb; break;
                default: break;
            }
        }
        return totals;
    }
    static int[] allocatedStorage(int[] p,int used) {
        int[] reserved=storageGB(p);
        for(int i=0;i<reserved.length;i++) { reserved[i]=Math.min(reserved[i],Math.max(0,used)); used-=reserved[i]; }
        return reserved;
    }
    static int diskGB(int[] p) { return Arrays.stream(storageGB(p)).sum(); }
    static int cpuCores(int[] p) { return Part.at(p[CPU]).cores+Part.at(p[CPU2]).cores; }
    static int gpuTotal(int[] p) {
        int total=0;
        for(int i=PCIE;i<PSU;i++) total+=Part.at(p[i]).chips;
        return total;
    }
    static int[] gpuSlots(int[] p) {
        int[] devices=new int[gpuTotal(p)];
        int next=0;
        for(int i=PCIE;i<PSU;i++) {
            int end=next+Part.at(p[i]).chips;
            Arrays.fill(devices,next,end,p[i]);
            next=end;
        }
        return devices;
    }
    static int vram(int[] p) {
        int total=0;
        for(int i=PCIE;i<PSU;i++) { Part card=Part.at(p[i]); total+=card.chips*card.gb; }
        return total;
    }
    static int psu(int[] p) {
        int watts=0;
        for(int i=PSU;i<CPU2;i++){Part unit=Part.at(p[i]);if(unit.kind==Part.Kind.PSU)watts+=unit.watts;}
        return watts;
    }
    static int drawWatts(int[] p) {
        int watts=Part.at(p[CPU2]).watts;
        for(int i=0;i<PSU;i++) watts+=Part.at(p[i]).watts;
        return watts;
    }
    /** Each PSU carries the same fraction of its own rating (no redundancy).
     * Negative means the requested DC output cannot be supplied by the installed PSU set.
     * Exact rational arithmetic; round only the total AC input upward to whole watts. */
    static int inputWatts(int[] p,int loadWatts) {
        if(loadWatts<0)throw new IllegalArgumentException("negative load");
        int capacity=psu(p);
        if(capacity<=0||loadWatts>capacity)return -1;
        long denominator=1;
        for(int i=PSU;i<CPU2;i++){
            Part unit=Part.at(p[i]);if(unit.kind==Part.Kind.PSU)denominator*=unit.efficiency;
        }
        long weighted=0;
        for(int i=PSU;i<CPU2;i++){
            Part unit=Part.at(p[i]);if(unit.kind==Part.Kind.PSU)weighted+=100L*unit.watts*(denominator/unit.efficiency);
        }
        long numerator=weighted*loadWatts, divisor=denominator*capacity;
        return (int)(numerator/divisor+(numerator%divisor==0?0:1));
    }
    static int maxInputWatts(int[] p){return inputWatts(p,drawWatts(p));}
    static int idleInputWatts(int[] p){return inputWatts(p,new TaskEngine.Node(null,0,0,p).watts(0,0));}
    static int nic(int[] p) {
        Part board=Part.at(p[BOARD]);
        int fastest=board.onboardNicMbps();
        for(int i=PCIE;i<PSU;i++) fastest=Math.max(fastest,Part.at(p[i]).mbps);
        return fastest;
    }
    static boolean canRemoveBoard(int[] p) {
        for(int i=CPU;i<PSU;i++) if(p[i]!=0) return false;
        for(int i=CPU_COOLER;i<FAN;i++)if(p[i]!=0)return false;
        return p[CPU2]==0;
    }
}
