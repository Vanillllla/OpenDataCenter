package odc;

/** Physical slots. No Minecraft dependency. Tier 0 = absent, -1 = wrong item. */
public final class ServerSpec {
    public static final int BOARD=0,CPU=1,RAM=2,SATA=10,NVME=18,GPU=20,PSU=24,NIC=28,COUNT=29;
    public static final int READY=0,NO_BOARD=1,NO_CPU=2,NO_RAM=3,NO_DISK=4,INVALID=5,CAPACITY=6,NO_PSU=7;
    private ServerSpec(){}
    public static boolean chassis(int u){return u==1||u==2||u==4;}
    public static int capacity(int u,int board,int slot){
        if(!chassis(u)||slot<0||slot>=COUNT)return 0;
        if(slot==BOARD)return 1;
        if(slot>=PSU&&slot<NIC)return u;
        if(board<1||board>3)return 0;
        if(slot==CPU||slot==NIC)return 1;
        if(slot<SATA)return 1<<board;
        if(slot<NVME)return 1<<board;
        if(slot<GPU)return board-1;
        return Math.min(u,1<<(board-1));
    }
    public static boolean fits(int u,int board,int slot){
        if(slot<0||slot>=COUNT)return false;
        int first=slot<2?slot:slot<SATA?RAM:slot<NVME?SATA:slot<GPU?NVME:slot<PSU?GPU:slot<NIC?PSU:NIC;
        return slot-first<capacity(u,board,slot);
    }
    public static int state(int u,int[] p){
        if(!chassis(u)||p.length!=COUNT)return INVALID;
        for(int i=0;i<COUNT;i++){int max=i==BOARD||i>=RAM&&i<SATA||i>=PSU&&i<NIC?3:i>=SATA&&i<NVME?4:2;if(p[i]<0||p[i]>max)return INVALID;}
        for(int i=0;i<COUNT;i++)if(p[i]!=0&&!fits(u,p[BOARD],i))return CAPACITY;
        if(p[BOARD]==0)return NO_BOARD;if(p[CPU]==0)return NO_CPU;
        if(sum(p,RAM,SATA)==0)return NO_RAM;if(sum(p,SATA,GPU)==0)return NO_DISK;
        if(sum(p,PSU,NIC)==0)return NO_PSU;return READY;
    }
    private static int sum(int[] p,int a,int b){int n=0;for(int i=a;i<b;i++)n+=p[i];return n;}
    public static int ramGB(int[] p){int n=0;for(int i=RAM;i<SATA;i++)n+=p[i]==1?4:p[i]==2?16:p[i]==3?32:0;return n;}
    public static int diskGB(int[] p){int n=0;int[] sata={0,512,2048,256,1024};for(int i=SATA;i<NVME;i++)if(p[i]>=0&&p[i]<5)n+=sata[p[i]];for(int i=NVME;i<GPU;i++)n+=p[i]==1?512:p[i]==2?2048:0;return n;}
    public static int gpuCount(int[] p,int tier){int n=0;for(int i=GPU;i<PSU;i++)if(p[i]==tier)n++;return n;}
    public static int psu(int[] p){int n=0;for(int i=PSU;i<NIC;i++)if(p[i]>0&&p[i]<=3)n+=250<<(p[i]-1);return n;}
    public static int nic(int[] p){return p[NIC]==1?100:p[NIC]==2?1000:0;}
    public static boolean canRemoveBoard(int[] p){for(int i=CPU;i<COUNT;i++)if((i<PSU||i==NIC)&&p[i]!=0)return false;return true;}
}
