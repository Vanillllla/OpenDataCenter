package odc;
import java.util.*;
public final class RackPowerMeshTest {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static int psu(){
        for(Part p:Part.models)if(p.kind==Part.Kind.PSU)return p.index;
        throw new AssertionError("missing PSU model");
    }
    private static int count(RackPowerMesh.Face[] faces,int material){int n=0;for(RackPowerMesh.Face f:faces)if(f.material==material)n++;return n;}
    private static void geometry(RackPowerMesh.Face[] faces,double minY,double maxY,double height){
        for(RackPowerMesh.Face f:faces){
            check(f.vertices.length==4,"quad");
            check(f.material>=0&&f.material<RackPowerMesh.TEXTURES.length,"texture");
            for(double[] p:f.vertices){
                check(p.length==3&&Double.isFinite(p[0])&&Double.isFinite(p[1])&&Double.isFinite(p[2]),"finite");
                check(p[0]>=0&&p[0]<=1&&p[1]>=minY-1e-9&&p[1]<=maxY+1e-9&&p[1]<=height&&p[2]>=RackPowerMesh.BACK&&p[2]<1,"rear and lane bounds");
                for(int facing=0;facing<4;facing++){
                    double[] q=RackLayout.Door.local(p[0],p[1],p[2],facing);
                    check(q[0]>=-1e-9&&q[0]<=1+1e-9&&q[2]>=-1e-9&&q[2]<=1+1e-9,"rotated bounds");
                }
            }
            double[] a=f.vertices[0],b=f.vertices[1],c=f.vertices[2];
            double x=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double y=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double z=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            check(x*x+y*y+z*z>1e-16,"nondegenerate face");
        }
    }
    public static void main(String[] args){
        int psu=psu();
        for(int units:new int[]{1,2,4})for(int mask=0;mask<(1<<units);mask++){
            int[] parts=new int[ServerSpec.COUNT];
            for(int i=0;i<units;i++)if((mask&(1<<i))!=0)parts[ServerSpec.PSU+i]=psu;
            check(RackPowerMesh.psuMask(units,parts)==mask,"physical slots including gaps");
            check(RackPowerMesh.wireCount(false,units,parts)==Integer.bitCount(mask),"one lead per installed PSU");
            check(RackPowerMesh.wireCount(true,1,parts)==1,"one switch lead");
            for(int capacity:new int[]{4,9})for(int start=0;start+units<=capacity;start++){
                int cables=0;
                for(int slot=0;slot<units;slot++)if(count(RackPowerMesh.lane(capacity,start+slot,(mask&(1<<slot))!=0),RackPowerMesh.CABLE)>0)cables++;
                check(cables==Integer.bitCount(mask),"rendered cables match installed PSU count");
            }
        }
        int[] malformed=new int[ServerSpec.COUNT];malformed[ServerSpec.PSU+3]=psu;
        check(RackPowerMesh.psuMask(1,malformed)==0,"out of chassis slot ignored");
        for(int capacity:new int[]{4,9}){
            double height=capacity==9?2:1,pitch=RackPowerMesh.pitch(capacity);
            RackPowerMesh.Face[] rail=RackPowerMesh.rail(capacity);
            check(rail==RackPowerMesh.rail(capacity)&&rail.length==6+7*capacity,"cached rail and one socket per U");
            geometry(rail,0,height,height);
            int faces=rail.length;
            for(int bay=0;bay<capacity;bay++){
                double y=RackPowerMesh.center(capacity,bay);
                RackPowerMesh.Face[] cable=RackPowerMesh.lane(capacity,bay,true),empty=RackPowerMesh.lane(capacity,bay,false);
                check(cable==RackPowerMesh.lane(capacity,bay,true),"cached cable");
                check(count(cable,RackPowerMesh.CABLE)==24&&count(cable,RackPowerMesh.PLUG)==12,"single swept lead two connectors");
                check(empty.length==1&&count(empty,RackPowerMesh.CABLE)==0,"uninstalled PSU blank");
                geometry(cable,y-pitch/2+.012,y+pitch/2-.012,height);geometry(empty,y-pitch/2+.012,y+pitch/2-.012,height);
                faces+=cable.length;
            }
            check(faces<=410,"bounded extra geometry");
        }
        check(RackLayout.retainedIndex(5,4,true)==0&&RackLayout.retainedIndex(0,4,false)==0,"merge/split reindexing");
        System.out.println("RackPowerMesh: "+checks+" checks passed");
    }
}
