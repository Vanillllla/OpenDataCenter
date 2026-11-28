package odc;

import java.util.Arrays;

/** Slots store catalog indices. Multiple accelerators on one card share its electrical load. */
public final class ServerSpec {
    static final int BOARD=0, CPU=1, RAM=2, SATA=10, NVME=22, PCIE=24, PSU=31, CPU2=35, COUNT=36;
    static final int READY=0, NO_BOARD=1, NO_CPU=2, NO_RAM=3, NO_DISK=4, INVALID=5, CAPACITY=6, NO_PSU=7;
    static boolean chassis(int u) { return u==1 || u==2 || u==4; }
    static int chassisPcie(int u) { return u==1?2:u==2?4:u==4?7:0; }
    static int first(int slot) {
        if(slot<=CPU || slot==CPU2) return slot;
        if(slot<SATA) return RAM;
        if(slot<NVME) return SATA;
        if(slot<PCIE) return NVME;
        return slot<PSU?PCIE:PSU;
    }
    static int capacity(int u,int board,int slot) {
        if(!chassis(u)) return 0;
        Part b=Part.at(board);
        switch(first(slot)) {
            case BOARD: return 1;
            case PSU: return u;
            case CPU: return Math.min(1,b.sockets);
            case CPU2: return Math.max(0,b.sockets-1);
            case RAM: return b.ramSlots;
            case SATA: return b.sataSlots;
            case NVME: return b.m2Slots;
            default: return Math.min(chassisPcie(u),b.pciSlots);
        }
    }
    static boolean fits(int u,int board,int slot) { return slot>=0 && slot<COUNT && slot-first(slot)<capacity(u,board,slot); }
    static boolean compatible(int slot,Part p,Part b) {
        switch(first(slot)) {
            case BOARD: return p.kind==Part.Kind.BOARD;
            case CPU: case CPU2: return p.kind==Part.Kind.CPU && p.socket==b.socket && p.ddr==b.ddr;
            case RAM: return p.kind==Part.Kind.RAM && p.ddr==b.ddr;
            case SATA: return p.kind==Part.Kind.SSD || p.kind==Part.Kind.HDD;
            case NVME: return p.kind==Part.Kind.NVME;
            case PSU: return p.kind==Part.Kind.PSU;
            default: return p.kind==Part.Kind.NIC || p.kind==Part.Kind.GPU || p.kind==Part.Kind.PCIE;
        }
    }
    static int width(Part p) { return p.kind==Part.Kind.EMPTY?0:Math.max(1,p.width); }
    static int occupiedPcie(int[] parts) {
        int total=0;
        for(int i=PCIE;i<PSU;i++) total+=width(Part.at(parts[i]));
        return total;
    }
    static boolean accepts(int u,int[] parts,int slot,Part candidate) {
        if(!fits(u,parts[BOARD],slot) || !compatible(slot,candidate,Part.at(parts[BOARD]))) return false;
        return first(slot)!=PCIE || occupiedPcie(parts)-width(Part.at(parts[slot]))+width(candidate)<=capacity(u,parts[BOARD],PCIE);
    }
    static int state(int u,int[] p) {
        if(!chassis(u)||p.length!=COUNT) return INVALID;
        if(p[BOARD]==0) return NO_BOARD;
        for(int i=0;i<COUNT;i++) if(p[i]!=0) {
            if(!compatible(i,Part.at(p[i]),Part.at(p[BOARD]))) return INVALID;
            if(!fits(u,p[BOARD],i)) return CAPACITY;
        }
        if(occupiedPcie(p)>capacity(u,p[BOARD],PCIE)) return CAPACITY;
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
        return p[CPU2]==0;
    }
}
