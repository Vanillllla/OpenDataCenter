package odc;

import java.util.*;

/** Two rear hoses in the left-hand 0U channel, disjoint from the right PDU and front LAN. */
final class RackCoolingMesh {
    private static final RackPowerMesh.Face[][] RAIL=new RackPowerMesh.Face[2][];
    private static final RackPowerMesh.Face[][][][] HOSES=new RackPowerMesh.Face[2][9][5][];
    private RackCoolingMesh(){}
    static boolean connected(ChassisSpec c,int[] parts){
        if(c==null||!c.liquid||parts.length!=ServerSpec.COUNT)return false;
        for(int i=ServerSpec.CPU_COOLER;i<ServerSpec.FAN;i++)
            if(Part.at(parts[i]).liquidCooling()&&ServerSpec.accepts(c,parts,i,Part.at(parts[i])))return true;
        return false;
    }
    private static double[] p(double x,double y,double z){return new double[]{x,y,z};}
    private static void face(List<RackPowerMesh.Face> f,int m,int s,double[]... p){f.add(new RackPowerMesh.Face(m,s,p));}
    private static void box(List<RackPowerMesh.Face> f,int m,double l,double b,double z,double r,double t,double back){
        face(f,m,185,p(l,b,z),p(r,b,z),p(r,t,z),p(l,t,z));
        face(f,m,255,p(r,b,back),p(l,b,back),p(l,t,back),p(r,t,back));
        face(f,m,205,p(l,b,back),p(l,b,z),p(l,t,z),p(l,t,back));
        face(f,m,185,p(r,b,z),p(r,b,back),p(r,t,back),p(r,t,z));
        face(f,m,235,p(l,t,z),p(r,t,z),p(r,t,back),p(l,t,back));
        face(f,m,150,p(l,b,back),p(r,b,back),p(r,b,z),p(l,b,z));
    }
    private static int index(int cap){return cap==9?1:0;}
    static RackPowerMesh.Face[] rail(int cap){
        int k=index(cap);if(RAIL[k]!=null)return RAIL[k];List<RackPowerMesh.Face> f=new ArrayList<>();
        double h=cap==9?2:1;
        box(f,0,.105,.065,.925,.136,h-.065,.95);
        box(f,1,.105,.065,.959,.136,h-.065,.984);
        return RAIL[k]=f.toArray(new RackPowerMesh.Face[0]);
    }
    static RackPowerMesh.Face[] hoses(int cap,int bay,int units){
        int k=index(cap);RackPowerMesh.Face[] old=HOSES[k][bay][units];if(old!=null)return old;
        List<RackPowerMesh.Face> f=new ArrayList<>();
        double mid=RackPowerMesh.center(cap,bay)+(units-1)*RackPowerMesh.pitch(cap)/2;
        for(int m=0;m<2;m++){
            double y=mid+(m==0?-.027:.027),z=m==0?.9375:.9715,r=.006;
            // Swept square cross-section, no overlapping coplanar faces at elbows.
            double[][] path={{.136,y,z},{.316,y,z},{.335,y,z-.014},{.335,y,.913}};
            double[][][] rings=new double[path.length][4][];
            for(int i=0;i<path.length;i++){
                double dx=0,dz=0;
                if(i>0){double a=path[i][0]-path[i-1][0],b=path[i][2]-path[i-1][2],n=Math.hypot(a,b);dx+=a/n;dz+=b/n;}
                if(i+1<path.length){double a=path[i+1][0]-path[i][0],b=path[i+1][2]-path[i][2],n=Math.hypot(a,b);dx+=a/n;dz+=b/n;}
                double n=Math.hypot(dx,dz),nx=-dz/n*r,nz=dx/n*r;
                double x=path[i][0],zz=path[i][2];
                rings[i][0]=p(x+nx,y-r,zz+nz);rings[i][1]=p(x-nx,y-r,zz-nz);
                rings[i][2]=p(x-nx,y+r,zz-nz);rings[i][3]=p(x+nx,y+r,zz+nz);
            }
            for(int i=0;i<rings.length-1;i++)for(int q=0;q<4;q++){int next=(q+1)%4;face(f,m,q==2?240:q==0?155:200,rings[i][q],rings[i][next],rings[i+1][next],rings[i+1][q]);}
            box(f,m,.318,y-.013,.901,.352,y+.013,.913);
        }
        return HOSES[k][bay][units]=f.toArray(new RackPowerMesh.Face[0]);
    }
}
