package odc;

import java.util.Arrays;

/** Client-independent geometry, malformed hardware and bounded monitor tests. */
public final class CoolingRegressionTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static int model(String name){return ServerSpecTest.model(name);}
    private static void mesh(RackPowerMesh.Face[] faces,double minY,double maxY){
        for(RackPowerMesh.Face f:faces){
            check(f.material==0||f.material==1,"supply/return material");
            check(f.vertices.length==4,"quad");
            for(double[] p:f.vertices){
                check(Double.isFinite(p[0])&&Double.isFinite(p[1])&&Double.isFinite(p[2]),"finite vertex");
                check(p[0]>=0&&p[0]<.5&&p[1]>=minY-1e-9&&p[1]<=maxY+1e-9&&p[2]>.90&&p[2]<1,"rear left, outside PDU and front LAN");
                for(int facing=0;facing<4;facing++){
                    double[] q=RackLayout.Door.local(p[0],p[1],p[2],facing);
                    check(q[0]>=-1e-9&&q[0]<=1+1e-9&&q[2]>=-1e-9&&q[2]<=1+1e-9,"rotated bounds");
                }
            }
            double[] a=f.vertices[0],b=f.vertices[1],c=f.vertices[2];
            double x=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double y=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double z=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            check(x*x+y*y+z*z>1e-16,"nondegenerate");
        }
    }
    public static void main(String[] args){
        CoolingUiState.History h=new CoolingUiState.History();
        for(int i=0;i<500;i++){h.sample(i*20L,1,i,i+1);h.sample(i*20L+5,1,999,999);}
        int[] a=h.values(false),b=h.values(true);
        check(a.length==120&&b.length==120&&a[0]==380&&a[119]==499&&b[119]==500,"ring, order, rate");
        h.sample(10000,2,3,4);check(Arrays.equals(h.values(false),new int[]{3}),"scope reset");
        h.sample(10080,2,5,6);check(Arrays.equals(h.values(true),new int[]{6}),"gap reset");
        h.reset();check(h.values(false).length==0,"empty reset");
        for(int n:new int[]{0,1,32,33,128,9999})for(int offset:new int[]{-1,0,5,31,32,127,9998,Integer.MAX_VALUE}){
            int start=CoolingUiState.start(offset,n);
            check(start>=0&&start<=Math.max(0,n-1),"clamp offset");
            int count=0;for(int i=start;i<n&&i-start<CoolingUiState.PAGE;i++)if(CoolingUiState.inPage(i,start))count++;
            check(count==Math.min(CoolingUiState.PAGE,Math.max(0,n-start)),"bounded exact page");
        }
        for(int e=0;e<=12;e++){
            int y=CoolingUiState.rowsTop(e);
            check(CoolingUiState.offset(y-1,e)==0&&CoolingUiState.offset(y+22*20,e)==20,"page scroll geometry");
        }
        for(int cap:new int[]{4,9}){
            double height=cap==9?2:1;
            RackPowerMesh.Face[] rail=RackCoolingMesh.rail(cap);
            check(rail==RackCoolingMesh.rail(cap)&&rail.length==12,"cached paired rail");mesh(rail,0,height);
            for(int u:new int[]{1,2,4})for(int bay=0;bay+u<=cap;bay++){
                RackPowerMesh.Face[] hoses=RackCoolingMesh.hoses(cap,bay,u);
                check(hoses==RackCoolingMesh.hoses(cap,bay,u)&&hoses.length<=36,"cached bounded hose batch");
                double pitch=RackPowerMesh.pitch(cap);
                mesh(hoses,.0625+bay*pitch+.012,.0625+(bay+u)*pitch-.012);
                double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
                for(RackPowerMesh.Face f:hoses)for(double[] p:f.vertices){if(f.material==0)hi=Math.max(hi,p[1]);else lo=Math.min(lo,p[1]);}
                check(hi<lo,"supply and return separated");
            }
        }
        int[] parts=ServerSpecTest.minimal();
        parts[ServerSpec.CPU_COOLER]=model("Hydro CPU 300");
        check(RackCoolingMesh.connected(ChassisSpec.GPU_4,parts),"waterblock and supported chassis");
        check(!RackCoolingMesh.connected(ChassisSpec.BASIC_1,parts),"dry chassis");
        check(ServerSpec.thermalProfile(ChassisSpec.BASIC_1,parts).liquidRatedWatts==0,"incompatible coolant cannot grant cooling");
        parts[ServerSpec.CPU]=0;
        check(!RackCoolingMesh.connected(ChassisSpec.GPU_4,parts),"orphan cooler does not connect");
        check(ServerSpec.thermalProfile(ChassisSpec.GPU_4,parts).liquidRatedWatts==0,"orphan coolant ignored");
        parts[ServerSpec.FAN+7]=model("Breeze Turbo");
        check(ServerSpec.thermalProfile(ChassisSpec.BASIC_1,parts).airConductance==2,"out of range fan ignored");
        System.out.println("CoolingRegression: "+checks+" checks passed");
    }
}
