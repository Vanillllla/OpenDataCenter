package odc;
import java.util.*;

/** Decorative rear-only geometry. Front patch leads and all power logic are independent.
 * Every physical PSU slot has its own vertical lane; no cable crosses another lane.
 * Cached meshes are in the same local coordinates as RackRenderer. */
final class RackPowerMesh {
    static final double BACK=.90, PDU_LEFT=.825, PDU_RIGHT=.895, PDU_FRONT=.925, PDU_BACK=.985;
    static final int RAIL=0, SOCKET=1, PLUG=2, CABLE=3, PSU=4, BLANK=5;
    static final String[] TEXTURES={"pdu_rail","pdu_socket","power_plug","power_lead","psu_rear","psu_blank"};
    static final class Face {
        final int material,shade;final double[][] vertices;
        Face(int material,int shade,double[]... vertices){this.material=material;this.shade=shade;this.vertices=vertices;}
    }
    private static final Face[][] RAILS=new Face[2][];
    private static final Face[][][][] LEADS=new Face[2][9][2][];
    private RackPowerMesh(){}
    private static int index(int capacity){if(capacity==4)return 0;if(capacity==9)return 1;throw new IllegalArgumentException("rack capacity");}
    static double pitch(int capacity){return index(capacity)==0?.875/4:1.875/9;}
    static double center(int capacity,int bay){return .0625+(bay+.5)*pitch(capacity);}
    static int psuMask(int units,int[] parts){
        if(!RackLayout.validSize(units)||parts==null||parts.length!=ServerSpec.COUNT)return 0;
        int mask=0;for(int i=0;i<units;i++)if(Part.at(parts[ServerSpec.PSU+i]).kind==Part.Kind.PSU)mask|=1<<i;return mask;
    }
    static int wireCount(boolean isSwitch,int units,int[] parts){return isSwitch?1:Integer.bitCount(psuMask(units,parts));}
    private static double[] p(double x,double y,double z){return new double[]{x,y,z};}
    private static void face(List<Face> f,int m,int s,double[]... p){f.add(new Face(m,s,p));}
    private static void box(List<Face> f,int m,double l,double b,double z,double r,double t,double back){
        face(f,m,185,p(l,b,z),p(r,b,z),p(r,t,z),p(l,t,z));
        face(f,m,255,p(r,b,back),p(l,b,back),p(l,t,back),p(r,t,back));
        face(f,m,205,p(l,b,back),p(l,b,z),p(l,t,z),p(l,t,back));
        face(f,m,185,p(r,b,z),p(r,b,back),p(r,t,back),p(r,t,z));
        face(f,m,235,p(l,t,z),p(r,t,z),p(r,t,back),p(l,t,back));
        face(f,m,150,p(l,b,back),p(r,b,back),p(r,b,z),p(l,b,z));
    }
    static Face[] rail(int capacity){
        int k=index(capacity);if(RAILS[k]!=null)return RAILS[k];
        List<Face> f=new ArrayList<>();
        double h=capacity==9?2:1;
        box(f,RAIL,PDU_LEFT,.065,PDU_FRONT,PDU_RIGHT,h-.065,PDU_BACK);
        for(int bay=0;bay<capacity;bay++){
            double y=center(capacity,bay);
            // Socket protrudes toward the rack centre, plug mates with its left face.
            box(f,SOCKET,.805,y-.027,.938,PDU_LEFT,y+.027,.974);
            // Repeated socket detail is visible when viewed from behind.
            face(f,SOCKET,255,p(.884,y-.028,.9855),p(.836,y-.028,.9855),p(.836,y+.028,.9855),p(.884,y+.028,.9855));
        }
        return RAILS[k]=f.toArray(new Face[0]);
    }
    static Face[] lane(int capacity,int bay,boolean plugged){
        int k=index(capacity);if(bay<0||bay>=capacity)throw new IllegalArgumentException("bay");
        int state=plugged?1:0;Face[] saved=LEADS[k][bay][state];if(saved!=null)return saved;
        double y=center(capacity,bay);
        List<Face> f=new ArrayList<>();
        // Rear PSU bay face; no connector or cable on an empty slot.
        face(f,plugged?PSU:BLANK,255,p(.665,y-.047,BACK+.0006),p(.515,y-.047,BACK+.0006),p(.515,y+.047,BACK+.0006),p(.665,y+.047,BACK+.0006));
        if(plugged){
            box(f,PLUG,.609,y-.018,BACK+.001,.646,y+.018,.924);
            box(f,PLUG,.779,y-.017,.944,.805,y+.017,.968);
            // Swept square cable, rounded path in horizontal plane. One mesh, no overlapping elbows.
            double[][] path={{.6275,.924},{.6275,.942},{.639,.958},{.658,.966},{.745,.966},{.765,.962},{.779,.956}};
            double radius=.007;
            double[][][] rings=new double[path.length][4][];
            for(int i=0;i<path.length;i++){
                double dx=0,dz=0;
                if(i>0){double a=path[i][0]-path[i-1][0],b=path[i][1]-path[i-1][1],n=Math.hypot(a,b);dx+=a/n;dz+=b/n;}
                if(i+1<path.length){double a=path[i+1][0]-path[i][0],b=path[i+1][1]-path[i][1],n=Math.hypot(a,b);dx+=a/n;dz+=b/n;}
                double n=Math.hypot(dx,dz),nx=-dz/n*radius,nz=dx/n*radius;
                double x=path[i][0],z=path[i][1];
                rings[i][0]=p(x+nx,y-radius,z+nz);rings[i][1]=p(x-nx,y-radius,z-nz);
                rings[i][2]=p(x-nx,y+radius,z-nz);rings[i][3]=p(x+nx,y+radius,z+nz);
            }
            for(int i=0;i<rings.length-1;i++)for(int j=0;j<4;j++){
                int next=(j+1)%4;face(f,CABLE,j==2?240:j==0?155:200,rings[i][j],rings[i][next],rings[i+1][next],rings[i+1][j]);
            }
        }
        return LEADS[k][bay][state]=f.toArray(new Face[0]);
    }
}
