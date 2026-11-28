package odc;

/** Bounded session history and page geometry shared by the client and server. */
final class CoolingUiState {
    static final int HISTORY=120, PAGE=32, EVENT_LIMIT=12, ROW=22;
    private CoolingUiState(){}
    static int rowsTop(int events){return 219+Math.max(0,Math.min(EVENT_LIMIT,events))*18;}
    static int offset(int scroll,int events){return Math.max(0,(scroll-rowsTop(events))/ROW);}
    static int start(int requested,int total){return Math.min(Math.max(0,requested),Math.max(0,total-1));}
    static boolean inPage(int index,int start){return index>=start&&index-start<PAGE;}
    static final class History {
        private final int[] mean=new int[HISTORY],max=new int[HISTORY];
        private int start,size,scope;
        private long tick=Long.MIN_VALUE;
        void reset(){start=size=0;tick=Long.MIN_VALUE;}
        void sample(long now,int identity,int a,int b){
            if(tick!=Long.MIN_VALUE&&(now<tick||now-tick>40||scope!=identity))reset();
            if(tick!=Long.MIN_VALUE&&now-tick<20)return;
            scope=identity;tick=now;
            int i=(start+size)%HISTORY;mean[i]=a;max[i]=b;
            if(size==HISTORY)start=(start+1)%HISTORY;else size++;
        }
        int[] values(boolean peak){int[] r=new int[size];for(int i=0;i<size;i++)r[i]=(peak?max:mean)[(start+i)%HISTORY];return r;}
    }
}
