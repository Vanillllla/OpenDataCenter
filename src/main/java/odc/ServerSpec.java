package odc;

/** Physical slots and component characteristics, independent of Forge. */
public final class ServerSpec {
    public static final int BOARD=0,CPU=1,RAM=2,SATA=10,NVME=18,PCIE=20,GPU=PCIE,PSU=24,CPU2=28,COUNT=29;
    public static final int NIC1=5,NIC2=6,PCIE_SSD=7;
    public static final int READY=0,NO_BOARD=1,NO_CPU=2,NO_RAM=3,NO_DISK=4,INVALID=5,CAPACITY=6,NO_PSU=7;
    private ServerSpec(){}
    public static boolean chassis(int u){return u==1||u==2||u==4;}
    public static int capacity(int u,int board,int slot){
        if(!chassis(u)||slot<0||slot>=COUNT)return 0;
        if(slot==BOARD)return 1;
        if(slot>=PSU&&slot<CPU2)return u;
        if(board<1||board>4)return 0;
        if(slot==CPU)return 1;
        if(slot==CPU2)return board==4?1:0;
        if(slot<SATA)return Math.min(8,1<<board);
        if(slot<NVME)return Math.min(8,1<<board);
        if(slot<PCIE)return Math.min(2,board-1);
        return Math.min(u,1<<Math.min(2,board-1));
    }
    public static boolean fits(int u,int board,int slot){
        if(slot<0||slot>=COUNT)return false;
        int first=slot<=CPU||slot==CPU2?slot:slot<SATA?RAM:slot<NVME?SATA:slot<PCIE?NVME:slot<PSU?PCIE:PSU;
        return slot-first<capacity(u,board,slot);
    }
    public static int maxTier(int i){return i==BOARD?4:i==CPU||i==CPU2?3:i<SATA?4:i<NVME?7:i<PCIE?3:i<PSU?7:3;}
    public static int state(int u,int[] p){
        if(!chassis(u)||p.length!=COUNT)return INVALID;
        for(int i=0;i<COUNT;i++)if(p[i]<0||p[i]>maxTier(i))return INVALID;
        if(p[BOARD]==0)return NO_BOARD;
        for(int i=0;i<COUNT;i++)if(p[i]!=0&&!fits(u,p[BOARD],i))return CAPACITY;
        if(p[CPU]==0)return NO_CPU;
        if(sum(p,RAM,SATA)==0)return NO_RAM;if(diskGB(p)==0)return NO_DISK;
        if(sum(p,PSU,CPU2)==0)return NO_PSU;return READY;
    }
    private static int sum(int[] p,int a,int b){int n=0;for(int i=a;i<b;i++)n+=p[i];return n;}
    public static int ramGB(int[] p){int n=0;int[] v={0,4,16,32,64};for(int i=RAM;i<SATA;i++)if(p[i]>=0&&p[i]<v.length)n+=v[p[i]];return n;}
    public static int diskGB(int[] p){int n=0;int[] sata={0,512,2048,256,1024,4096,8192,2048},nvme={0,512,2048,4096};for(int i=SATA;i<NVME;i++)if(p[i]>=0&&p[i]<sata.length)n+=sata[p[i]];for(int i=NVME;i<PCIE;i++)if(p[i]>=0&&p[i]<nvme.length)n+=nvme[p[i]];for(int i=PCIE;i<PSU;i++)if(p[i]==PCIE_SSD)n+=4096;return n;}
    public static int diskCount(int[] p){int n=0;for(int i=SATA;i<PCIE;i++)if(p[i]>0)n++;for(int i=PCIE;i<PSU;i++)if(p[i]==PCIE_SSD)n++;return n;}
    public static int cpuCU(int tier){return tier==1?40:tier==2?160:tier==3?480:0;}
    public static int cpuWatts(int tier){return tier==1?8:tier==2?20:tier==3?55:0;}
    public static int cpuCU(int[] p){return cpuCU(p[CPU])+cpuCU(p[CPU2]);}
    public static int cpuWatts(int[] p){return cpuWatts(p[CPU])+cpuWatts(p[CPU2]);}
    public static int gpuCount(int[] p,int tier){if(tier<1||tier>4)return 0;int n=0;for(int i=PCIE;i<PSU;i++)if(p[i]==tier)n++;return n;}
    public static int[] gpuSlots(int[] p){int[] g=new int[PSU-PCIE];for(int i=0;i<g.length;i++)g[i]=p[PCIE+i]>=1&&p[PCIE+i]<=4?p[PCIE+i]:0;return g;}
    public static int gpuVram(int t){return t==1?4:t==2?16:t==3?24:t==4?48:0;}
    public static int gpuHash(int t){return t==1?40:t==2?160:t==3?480:t==4?1280:0;}
    public static int gpuIdle(int t){return t==1?2:t==2?5:t==3?8:t==4?12:0;}
    public static int gpuPeak(int t){return t==1?25:t==2?85:t==3?180:t==4?350:0;}
    public static int gpuTotal(int[] p){int n=0;for(int t:gpuSlots(p))if(t>0)n++;return n;}
    public static int vram(int[] p){int n=0;for(int t:gpuSlots(p))n+=gpuVram(t);return n;}
    public static int gpuIdle(int[] p){int n=0;for(int t:gpuSlots(p))n+=gpuIdle(t);return n;}
    public static int psu(int[] p){int n=0;for(int i=PSU;i<CPU2;i++)if(p[i]>0&&p[i]<=3)n+=250<<(p[i]-1);return n;}
    public static int onboardNic(int board){return board==3?1000:board==4?1000:0;}
    /** One physical patch lead per server: use fastest installed interface, not sum of NICs. */
    public static int nic(int[] p){int n=onboardNic(p[BOARD]);for(int i=PCIE;i<PSU;i++)n=Math.max(n,p[i]==NIC1?100:p[i]==NIC2?1000:0);return n;}
    public static boolean canRemoveBoard(int[] p){for(int i=CPU;i<COUNT;i++)if((i<PSU||i==CPU2)&&p[i]!=0)return false;return true;}
}
