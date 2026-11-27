package odc;

/** Slot layout and boot rules, independent of Minecraft. Part tiers: 0 absent, 1 basic, 2 pro. */
public final class ServerSpec {
    public static final int BOARD=0, CPU=1, RAM=2, DISK=6, GPU=8, COUNT=12;
    public static final int READY=0, NO_BOARD=1, NO_CPU=2, NO_RAM=3, NO_DISK=4, INVALID=5, CAPACITY=6;
    private ServerSpec(){}
    public static boolean chassis(int u){return u==1||u==2||u==4;}
    public static int capacity(int u,int board,int slot){
        if(!chassis(u)||slot<0||slot>=COUNT)return 0;
        if(slot<2)return 1;
        if(slot<DISK)return Math.min(u==4?4:2,board==1?2:4);
        if(slot<GPU)return Math.min(u==1?1:2,board==1?1:2);
        return Math.min(u,board==1?1:4);
    }
    public static boolean fits(int u,int board,int slot){
        if(slot<0||slot>=COUNT)return false;
        int first=slot<2?slot:slot<DISK?RAM:slot<GPU?DISK:GPU;
        return slot-first<capacity(u,board,slot);
    }
    /** -1 indicates wrong item/count. Empty slots are 0. Missing parts are legal but cannot boot. */
    public static int state(int u,int[] parts){
        if(!chassis(u)||parts.length!=COUNT)return INVALID;
        for(int p:parts)if(p<0||p>2)return INVALID;
        for(int i=0;i<COUNT;i++)if(parts[i]!=0&&!fits(u,parts[BOARD],i))return CAPACITY;
        if(parts[BOARD]==0)return NO_BOARD;
        if(parts[CPU]==0)return NO_CPU;
        if(sum(parts,RAM,DISK)==0)return NO_RAM;
        if(sum(parts,DISK,GPU)==0)return NO_DISK;
        return READY;
    }
    private static int sum(int[] p,int from,int to){int n=0;for(int i=from;i<to;i++)n+=p[i];return n;}
    public static int ramGB(int[] p){int n=0;for(int i=RAM;i<DISK;i++)n+=p[i]==1?4:p[i]==2?16:0;return n;}
    public static int diskGB(int[] p){int n=0;for(int i=DISK;i<GPU;i++)n+=p[i]==1?128:p[i]==2?512:0;return n;}
    public static int gpuCount(int[] p,int tier){int n=0;for(int i=GPU;i<COUNT;i++)if(p[i]==tier)n++;return n;}
}
