package odc;
import java.util.*;
public final class ServerSpecTest {
    static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static int model(String name){return Part.models.stream().filter(p->p.name.equals(name)).findFirst().get().index;}
    static int[] minimal(){
        int[] p=new int[ServerSpec.COUNT];
        p[0]=model("ASBrick H81-S");p[1]=model("InTroll Celoron G1820");
        p[ServerSpec.RAM]=model("KingStoned Value 4");p[ServerSpec.SATA]=model("SeaBait Basic 256");
        p[ServerSpec.PSU]=model("AeroHot VX500");return p;
    }
    public static void main(String[] args){
        for(int facing=0;facing<4;facing++){
            double angle=Math.toRadians(180-facing*90),c=Math.cos(angle),s=Math.sin(angle);
            for(double height:new double[]{1,2})for(double y:new double[]{.5,height-.2}){
                double[] p=RackLayout.Door.local(.5+c*.1+s*(-1),y,.5-s*.1+c*(-1),facing);
                check(Math.abs(p[0]-.6)<1e-8&&Math.abs(p[2]+.5)<1e-8,"door local rotation");
                check(RackLayout.Door.hit(p,new double[]{.6,y,.5},0,height)>=0,"closed glass ray");
                check(RackLayout.Door.hit(p,new double[]{.6,y,.5},90,height)<0,"open rack front unobstructed");
                check(RackLayout.Door.hit(new double[]{-.5,y,-.4},new double[]{.8,y,-.4},90,height)>=0,"open leaf outside block selectable");
                check(RackLayout.Door.hit(new double[]{-.5,height+.1,-.4},new double[]{.8,height+.1,-.4},90,height)<0,"above leaf miss");
            }
        }
        check(RackLayout.Door.progress(true,0,5)==.5,"opening midpoint");
        check(RackLayout.Door.progress(false,.5,2)==.3,"smooth reversal from current progress");
        check(RackLayout.Door.angle(.5)==45&&RackLayout.Door.angle(1)==90,"door angle");
        check(!RackLayout.Door.accessible(true,true,.99)&&RackLayout.Door.accessible(true,true,1),"access only fully open");
        check(!RackLayout.Door.accessible(true,false,1)&&RackLayout.Door.accessible(false,false,0),"closing locks instantly; open rack unchanged");
        int onboard=0;for(Part p:Part.models)if(p.kind==Part.Kind.BOARD){int[] bare=new int[ServerSpec.COUNT];bare[ServerSpec.BOARD]=p.index;int expected=p.tier==3||p.sockets==2?1000:0;check(p.onboardNicMbps()==expected&&ServerSpec.nic(bare)==expected,"onboard NIC "+p.name);if(expected>0)onboard++;}check(onboard==5,"five boards with onboard NIC");
        int[] server=minimal();
        check(ServerSpec.state(1,server)==ServerSpec.READY,"complete server");
        server[ServerSpec.PCIE]=model("NoVideo Teslo M10");
        check(ServerSpec.state(1,server)==ServerSpec.READY && ServerSpec.gpuTotal(server)==4,"two slots, four GPUs");
        server[ServerSpec.PCIE+1]=model("FakeTek G1000");
        check(ServerSpec.state(1,server)==ServerSpec.CAPACITY,"aggregate PCIe width");
        server[ServerSpec.PCIE]=0;
        check(ServerSpec.nic(server)==1000,"1 Gbit NIC");
        server[ServerSpec.RAM]=model("Susung Server 16 Pro");
        check(ServerSpec.state(1,server)==ServerSpec.INVALID,"incompatible DIMM");
        server=minimal();server[ServerSpec.CPU]=model("InTroll Zeon 2670 v2");
        check(ServerSpec.state(1,server)==ServerSpec.INVALID,"incompatible socket");
        int gpu3=model("NoVideo RTX 3080"),gpu4=model("NoVideo RTX 6090"),gpu2=model("NoVideo Teslo M10"),nic=model("FakeTek G1000"),disk=model("InTroll Optan P100");
        int[] pc=new int[ServerSpec.COUNT];pc[ServerSpec.BOARD]=model("ASBrick Rack EP2C612 WS");
        int start=ServerSpec.PCIE;
        check(ServerSpec.capacity(1,pc[0],start)==2&&ServerSpec.capacity(2,pc[0],start)==4&&ServerSpec.capacity(4,pc[0],start)==7,"chassis limit");
        check(!ServerSpec.accepts(1,pc,start,Part.at(gpu3)),"3-slot GPU cannot fit 1U");
        check(!ServerSpec.accepts(2,pc,start+2,Part.at(gpu3)),"cannot extend past end");
        pc[start+1]=nic;
        check(!ServerSpec.accepts(4,pc,start,Part.at(gpu3)),"fragmented space rejected despite sufficient total");
        pc[start+1]=0;pc[start]=gpu3;
        check(ServerSpec.pcieOwner(pc,start+1)==start&&ServerSpec.pcieOwner(pc,start+2)==start,"derived follower owner");
        check(ServerSpec.slotAvailable(4,pc,start)&&!ServerSpec.slotAvailable(4,pc,start+1)&&!ServerSpec.slotAvailable(4,pc,start+2)&&ServerSpec.slotAvailable(4,pc,start+3),"only following slots locked");
        check(!ServerSpec.accepts(4,pc,start+1,Part.at(nic))&&!ServerSpec.accepts(4,pc,start+2,Part.at(disk)),"all PCIe devices blocked");
        check(ServerSpec.accepts(4,pc,start,Part.at(gpu4)),"replace at anchor");
        pc[start+3]=disk;check(!ServerSpec.accepts(4,pc,start,Part.at(gpu4)),"wider replacement conflicts");
        check(ServerSpec.accepts(4,pc,start,Part.at(gpu2)),"narrower replacement");
        pc[start]=gpu2;check(ServerSpec.slotAvailable(4,pc,start+2),"narrower releases slot");
        pc[start]=0;check(ServerSpec.slotAvailable(4,pc,start+1),"removal releases follower");
        pc[start]=gpu3;pc[start+1]=nic;pc[start+3]=0;
        check(ServerSpec.occupiedPcie(pc)==4&&!ServerSpec.validPcie(4,pc)&&ServerSpec.state(4,pc)==ServerSpec.CAPACITY,"old overlap invalid even within total");
        pc[start+1]=0;check(ServerSpec.validPcie(4,pc),"legacy conflict removable");
        pc[start]=0;pc[0]=model("ASBrick H81-S");
        check(ServerSpec.capacity(4,pc[0],start)==2&&!ServerSpec.accepts(4,pc,start,Part.at(gpu3)),"board limit");
        // Exhaust every sparse 7-position layout (empty, NIC, widths 2/3/4).
        int[] ids={0,nic,gpu2,gpu3,gpu4};
        for(int key=0;key<78125;key++){
            int code=key;pc[0]=model("ASBrick Rack EP2C612 WS");int[] widths=new int[7];
            for(int i=0;i<7;i++){pc[start+i]=ids[code%5];widths[i]=code%5;code/=5;}
            for(int u:new int[]{1,2,4}){
                int cap=ServerSpec.chassisPcie(u);boolean valid=true;boolean[] occupied=new boolean[7];
                for(int i=0;i<7;i++)if(widths[i]>0){
                    if(i+widths[i]>cap)valid=false;
                    for(int k=i;k<Math.min(7,i+widths[i]);k++){if(occupied[k])valid=false;occupied[k]=true;}
                }
                check(ServerSpec.validPcie(u,pc)==valid,"exhaustive PCIe placement");
            }
        }
        Random r=new Random(37);
        int layouts=0;int[] sizes={0,1,2,4};for(int key=0;key<(1<<18);key++){int[] a=new int[9];boolean[] busy=new boolean[9];boolean valid=true;for(int i=0;i<9;i++){a[i]=sizes[(key>>(i*2))&3];if(i+a[i]>9){valid=false;break;}for(int k=i;k<i+a[i];k++){if(busy[k])valid=false;busy[k]=true;}}if(!valid)continue;layouts++;
            for(int i=0;i<9;i++){check((RackLayout.startAt(a,i)>=0)==busy[i],"9U occupied");for(int u:new int[]{1,2,4}){boolean expect=i+u<=9;for(int j=0;j<9;j++)if(j!=i&&a[j]>0&&j<i+u&&i<j+a[j])expect=false;check(expect==RackLayout.canInsert(a,i,u),"9U collision");}if(a[i]>0){check((RackLayout.retainedIndex(i,a[i],false)>=0)==(i+a[i]<=4),"lower retained");check((RackLayout.retainedIndex(i,a[i],true)>=0)==(i>=5),"upper retained");}}}
        check(RackLayout.retainedIndex(4,1,true)==-1&&RackLayout.retainedIndex(4,1,false)==-1,"central unit falls");check(RackLayout.retainedIndex(3,2,false)==-1&&RackLayout.retainedIndex(3,2,true)==-1,"crossing falls");check(layouts>1000,"9U layout count");
        int[] ports=new int[8];check(PortMap.toggle(ports,4,4,0,true)==PortMap.REJECT,"T1 port limit");check(PortMap.toggle(ports,8,0,0,false)==PortMap.REJECT,"no cable no connection");check(PortMap.toggle(ports,8,0,0,true)==PortMap.CONNECTED,"connect");check(PortMap.toggle(ports,8,1,0,true)==PortMap.REJECT,"no duplicate connection");check(PortMap.toggle(ports,8,0,0,false)==PortMap.DISCONNECTED,"disconnect without spare cable");
        int available=20;for(int i=0;i<30000;i++){int bay=r.nextInt(9),port=r.nextInt(8);int action=r.nextInt(8);if(action==0)available+=PortMap.clear(ports);else if(action==1)available+=PortMap.detach(ports,bay);else{int result=PortMap.toggle(ports,8,port,bay,available>0);if(result==PortMap.CONNECTED)available--;else if(result==PortMap.DISCONNECTED)available++;}
            int count=0;boolean[] seen=new boolean[9];for(int v:ports)if(v!=0){check(!seen[v-1],"unique port mapping");seen[v-1]=true;count++;}check(available+count==20,"patch cable conservation");}
        System.out.println("PASS: "+checks+" hardware/layout assertions; "+layouts+" valid 9U layouts; 30000 patch operations.");
    }
}
