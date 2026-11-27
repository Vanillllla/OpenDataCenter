package odc;
import java.util.*;
/** Pure resource planner, independent of Forge. All quantities are integer reservations. */
public final class TaskEngine {
    public static final int BANK=3, AI=4, INFERENCE=5;
    public static final class Node {
        public final UUID id; public final int rack,cpu,ram,disk,net,psu,idle,cpuPeak; public final int[] gpu;
        public Node(UUID id,int rack,int cpu,int ram,int disk,int net,int psu,int idle,int cpuPeak,int[] gpu){this.id=id;this.rack=rack;this.cpu=cpu;this.ram=ram;this.disk=disk;this.net=net;this.psu=psu;this.idle=idle;this.cpuPeak=cpuPeak;this.gpu=gpu.clone();}
    }
    public static final class Limit {public int cpu=100, mask=15, ram=Integer.MAX_VALUE, net=Integer.MAX_VALUE, disk=Integer.MAX_VALUE; public Limit(){} public Limit(int cpu,int mask){this.cpu=cpu;this.mask=mask;}}
    public static class Task {
        public int id,type,priority=5,cpu=100,gpus=0,ram=4,disk=1,net=4,group=0; public boolean strict=false,auto=true,enabled=false; public long progress;
        public final Map<UUID,Limit> manual=new HashMap<>();
        public Task(int id,int type){this.id=id;this.type=type;if(type==1){cpu=0;gpus=1;}else if(type==2){cpu=0;gpus=1;ram=8;net=8;}else if(type==BANK){ram=4;disk=8;net=16;}else if(type==AI||type==INFERENCE){cpu=0;gpus=1;ram=8;disk=type==AI?32:16;net=32;}}
    }
    public static final class Slice {public final int node,cpu,mask,ram,disk,net,work,loadCPU,loadGPU; Slice(int n,int c,int m,int r,int ds,int b,int w,int lc,int lg){node=n;cpu=c;mask=m;ram=r;disk=ds;net=b;work=w;loadCPU=lc;loadGPU=lg;}}
    public static final class Result {public final List<Slice> slices=new ArrayList<>();public int cpu,gpus,ram,disk,net,work;public boolean full;}
    public static final class Plan {public final Map<Integer,Result> tasks=new LinkedHashMap<>();public final int[] cpu,gpu,ram,disk,net,power,actualCPU;public final int[][] actualGPU;public final Set<Integer> overload=new HashSet<>(); Plan(int n){cpu=new int[n];gpu=new int[n];ram=new int[n];disk=new int[n];net=new int[n];power=new int[n];actualCPU=new int[n];actualGPU=new int[n][4];}}
    static int minVram(int type){return type==2||type==AI||type==INFERENCE?8:type==1?2:0;}
    static int vram(int tier){return ServerSpec.gpuVram(tier);}
    static int cpuWork(int type,int cu,int pct){int f=type==0?150:type==1?25:type==2?10:type==BANK?100:0;return cu*pct*f/10000;}
    static int gpuWork(int type,int tier){int h=ServerSpec.gpuHash(tier);return type==0?h/4:type==1?h:type==2?h*13/10:type==AI?h*2:type==INFERENCE?h:0;}
    public static int power(Node n,int cpu,int[] gpuLoad){int p=n.idle+(n.cpuPeak*cpu+99)/100;for(int i=0;i<n.gpu.length;i++)if(n.gpu[i]>0)p+=((ServerSpec.gpuPeak(n.gpu[i])-ServerSpec.gpuIdle(n.gpu[i]))*gpuLoad[i]+99)/100;return p;}
    public static Plan plan(List<Node> nodes,List<Task> tasks,int[] switchCap,int wan,Map<UUID,Integer> groups,Set<Integer> excluded){
        Plan p=new Plan(nodes.size());int[] local=switchCap.clone();int global=wan;List<Task> sorted=new ArrayList<>(tasks);sorted.sort(Comparator.<Task>comparingInt(t->-t.priority).thenComparingInt(t->t.id));
        for(Task t:sorted){Result r=new Result();p.tasks.put(t.id,r);if(!t.enabled)continue;int needC=Math.max(0,t.cpu),needG=Math.max(0,t.gpus),needR=Math.max(0,t.ram),needD=Math.max(0,t.disk),needN=Math.max(1,t.net);int[] savedLocal=local.clone();int savedGlobal=global;
            for(int i=0;i<nodes.size()&&(needC>0||needG>0);i++){Node n=nodes.get(i);if(excluded.contains(i)||n.net<=0||n.rack<0||n.rack>=local.length||t.group>0&&groups.getOrDefault(n.id,0)!=t.group)continue;Limit lim=t.auto?new Limit():t.manual.get(n.id);if(lim==null)continue;
                int freeC=Math.max(0,100-p.cpu[i]),maxC=Math.min(needC,Math.min(freeC,lim.cpu));if(t.type==AI||t.type==INFERENCE)maxC=0;
                int eligible=0;if(t.type!=BANK)for(int j=0;j<n.gpu.length;j++)if(n.gpu[j]>0&&vram(n.gpu[j])>=minVram(t.type)&&(lim.mask&(1<<j))!=0&&(p.gpu[i]&(1<<j))==0)eligible|=1<<j;
                int ramFree=Math.min(n.ram-p.ram[i],lim.ram),diskFree=Math.min(n.disk-p.disk[i],lim.disk),available=Math.min(Math.min(Math.min(n.net-p.net[i],lim.net),local[n.rack]),global);
                int minimumRAM=t.type==AI||t.type==INFERENCE||t.type==2?8:2;if(ramFree<minimumRAM||diskFree<1||available<=0)continue;
                int total=Math.max(1,t.cpu+100*t.gpus);long weightCap=Math.min((long)ramFree*total/Math.max(1,t.ram),(long)diskFree*total/Math.max(1,t.disk));if(t.strict)weightCap=Math.min(weightCap,(long)available*total/Math.max(1,t.net));
                int c=0,g=0,mask=0,overhead=0,rr=0,ds=0,bw=0,w=0,actualC=0,actualG=0,best=-1;
                // Enumerate at most 16 subsets of physical cards; shrink CPU to fit remaining memory/network.
                for(int candidate=eligible;;candidate=(candidate-1)&eligible){int cg=Integer.bitCount(candidate),co=cg>0?5:0;if(cg<=needG&&freeC>=co&&100L*cg<=weightCap){int cc=(int)Math.min(Math.min(maxC,freeC-co),weightCap-100L*cg),weight=cc+100*cg;if(weight>0){int cr=Math.max(minimumRAM,(int)Math.ceil((double)t.ram*weight/total)),cd=Math.max(1,(int)Math.ceil((double)t.disk*weight/total)),desired=Math.max(1,(int)Math.ceil((double)t.net*weight/total)),cb=Math.min(available,desired);
                    int cw=cpuWork(t.type,n.cpu,cc);for(int j=0;j<n.gpu.length;j++)if((candidate&(1<<j))!=0)cw+=gpuWork(t.type,n.gpu[j]);cw=(int)((long)cw*cb/desired);
                    if(cr<=ramFree&&cd<=diskFree&&(!t.strict||cb==desired)&&cw>0&&(weight>best||weight==best&&(cw>w||cw==w&&candidate<mask))){best=weight;c=cc;g=cg;mask=candidate;overhead=co;rr=cr;ds=cd;bw=cb;w=cw;actualC=Math.min(cc+co,co+(cc*cb+desired-1)/desired);actualG=(100*cb+desired-1)/desired;}}}if(candidate==0)break;}
                if(best<0)continue;
                p.cpu[i]+=c+overhead;p.gpu[i]|=mask;p.ram[i]+=rr;p.disk[i]+=ds;p.net[i]+=bw;local[n.rack]-=bw;global-=bw;needC-=c;needG-=g;needR-=rr;needD-=ds;needN-=bw;r.cpu+=c;r.gpus+=g;r.ram+=rr;r.disk+=ds;r.net+=bw;r.work+=w;r.slices.add(new Slice(i,c+overhead,mask,rr,ds,bw,w,actualC,actualG));}
            r.full=needC<=0&&needG<=0&&needR<=0&&needD<=0&&needN<=0;
            if(t.strict&&!r.full){for(Slice s:r.slices){p.cpu[s.node]-=s.cpu;p.gpu[s.node]&=~s.mask;p.ram[s.node]-=s.ram;p.disk[s.node]-=s.disk;p.net[s.node]-=s.net;}local=savedLocal;global=savedGlobal;r.slices.clear();r.cpu=r.gpus=r.ram=r.disk=r.net=r.work=0;}
        }
        for(Result result:p.tasks.values())for(Slice s:result.slices){p.actualCPU[s.node]+=s.loadCPU;for(int i=0;i<4;i++)if((s.mask&(1<<i))!=0)p.actualGPU[s.node][i]=s.loadGPU;}
        for(int i=0;i<nodes.size();i++){if(excluded.contains(i))continue;Node n=nodes.get(i);p.power[i]=power(n,p.actualCPU[i],p.actualGPU[i]);if(p.power[i]>n.psu)p.overload.add(i);}return p;
    }
}
