package odc;

import java.util.Arrays;
import java.util.Random;

public final class ServerSpecTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int u:new int[]{1,2,4})for(int board=1;board<=2;board++){
            int[] p=new int[ServerSpec.COUNT];
            check(ServerSpec.state(u,p)==ServerSpec.NO_BOARD,"empty chassis");
            p[0]=board;check(ServerSpec.state(u,p)==ServerSpec.NO_CPU,"missing CPU");
            p[1]=1;check(ServerSpec.state(u,p)==ServerSpec.NO_RAM,"missing RAM");
            p[2]=1;check(ServerSpec.state(u,p)==ServerSpec.NO_DISK,"missing disk");
            p[6]=1;check(ServerSpec.state(u,p)==ServerSpec.READY,"GPU optional");
            for(int slot=2;slot<12;slot++){
                int old=p[slot];p[slot]=2;check((ServerSpec.state(u,p)==ServerSpec.READY)==ServerSpec.fits(u,board,slot),"slot capacity");p[slot]=old;
            }
            for(int slot=0;slot<12;slot++){
                int old=p[slot];p[slot]=-1;check(ServerSpec.state(u,p)==ServerSpec.INVALID,"wrong item");p[slot]=old;
            }
            for(int slot:new int[]{0,1,2,6}){int old=p[slot];p[slot]=0;check(ServerSpec.state(u,p)!=ServerSpec.READY,"removal prevents boot");p[slot]=old;}
        }
        int[] max={2,2,2,2,2,2,2,2,2,2,2,2};
        check(ServerSpec.state(4,max)==ServerSpec.READY,"max configuration");
        check(ServerSpec.ramGB(max)==64,"RAM total");check(ServerSpec.diskGB(max)==1024,"disk total");check(ServerSpec.gpuCount(max,2)==4,"GPU count");
        max[0]=1;check(ServerSpec.state(4,max)==ServerSpec.CAPACITY,"board downgrade cannot boot");
        max[0]=2;check(ServerSpec.state(2,max)==ServerSpec.CAPACITY,"chassis downgrade cannot boot");
        check(ServerSpec.state(3,max)==ServerSpec.INVALID,"invalid chassis");
        // Compare with an independently constructed expected layout for randomized part sets.
        Random random=new Random(37);
        for(int n=0;n<30000;n++){
            int u=new int[]{1,2,4}[random.nextInt(3)];int[] p=new int[12];for(int i=0;i<12;i++)p[i]=random.nextInt(3);
            int ramSlots=p[0]==1||u!=4?2:4, diskSlots=p[0]==1||u==1?1:2, gpuSlots=p[0]==1?1:u;
            boolean fits=true;for(int i=2+ramSlots;i<6;i++)fits&=p[i]==0;for(int i=6+diskSlots;i<8;i++)fits&=p[i]==0;for(int i=8+gpuSlots;i<12;i++)fits&=p[i]==0;
            boolean ready=fits&&p[0]>0&&p[1]>0&&Arrays.stream(p,2,6).sum()>0&&Arrays.stream(p,6,8).sum()>0;
            check((ServerSpec.state(u,p)==ServerSpec.READY)==ready,"random boot validation");
            check(ServerSpec.ramGB(p)==Arrays.stream(p,2,6).map(x->x==1?4:x==2?16:0).sum(),"random RAM");
            check(ServerSpec.diskGB(p)==Arrays.stream(p,6,8).map(x->x==1?128:x==2?512:0).sum(),"random storage");
        }
        System.out.println("PASS: "+checks+" server hardware assertions; 30,000 randomized configurations.");
    }
}
