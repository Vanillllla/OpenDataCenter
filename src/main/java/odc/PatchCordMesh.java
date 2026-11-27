package odc;

/** Static, low-poly patch lead geometry in rack-local coordinates (front is negative Z).
 * No Minecraft dependencies; the renderer and regression tests share this geometry.
 */
final class PatchCordMesh {
    static final int CABLE=0, PLUG=1;
    static final double PANEL_Z=.15, GLASS_Z=.038;
    static final class Face {
        final int material,shade;
        final double[][] vertices;
        Face(int material,int shade,double[] a,double[] b,double[] c,double[] d){this.material=material;this.shade=shade;vertices=new double[][]{a,b,c,d};}
    }
    private static final Face[][][][][][] CACHE=new Face[2][9][3][8][2][]; // see cache below
    private PatchCordMesh(){}
    static double portX(int port,int count){return .125+(port+.5)*.75/count;}
    static double portY(double bottom,double top){return bottom+(top-bottom)*.5;}
    static Face[] get(int capacity,int bay,int count,int port,boolean up){
        int a=capacity==9?1:0,b=count==2?0:count==4?1:2,d=up?1:0;
        if((capacity!=4&&capacity!=9)||bay<0||bay>=capacity||(count!=2&&count!=4&&count!=8)||port<0||port>=count)throw new IllegalArgumentException("Invalid patch cord location");
        Face[] result=CACHE[a][bay][b][port][d];
        if(result==null){double pitch=capacity==9?1.875/9:.875/4;double bottom=.0625+bay*pitch+.012,top=.0625+(bay+1)*pitch-.012;result=create(portX(port,count),bottom,top,up);CACHE[a][bay][b][port][d]=result;}
        return result;
    }
    private static double[] p(double x,double y,double z){return new double[]{x,y,z};}
    private static Face f(int mat,int shade,double[] a,double[] b,double[] c,double[] d){return new Face(mat,shade,a,b,c,d);}
    static Face[] create(double x,double bottom,double top,boolean up){
        double y=portY(bottom,top),s=up?1:-1,edge=up?top:bottom,gap=edge+s*.012;
        java.util.ArrayList<Face> faces=new java.util.ArrayList<>(30);
        // Connector seated flush on the switch face, open rear face (hidden inside the port).
        double l=x-.022,r=x+.022,b=y-.018,t=y+.018,z=.115,Z=PANEL_Z;
        faces.add(f(PLUG,240,p(l,b,z),p(r,b,z),p(r,t,z),p(l,t,z)));
        faces.add(f(PLUG,180,p(l,b,Z),p(l,b,z),p(l,t,z),p(l,t,Z)));
        faces.add(f(PLUG,205,p(r,b,z),p(r,b,Z),p(r,t,Z),p(r,t,z)));
        faces.add(f(PLUG,255,p(l,t,z),p(r,t,z),p(r,t,Z),p(l,t,Z)));
        faces.add(f(PLUG,160,p(l,b,Z),p(r,b,Z),p(r,b,z),p(l,b,z)));
        // One continuous swept rectangular mesh, not overlapping cuboids. The lead first
        // projects outward, then turns vertically, then recedes through the 0.024-high gap.
        // Last two rings are narrower so the lead fits without cutting through a neighbor.
        double[][] points={{y,.115},{y,.094},{y+s*.019,.075},{edge-s*.017,.075},{gap,.094},{gap,.225}};
        double[] radius={.009,.011,.011,.010,.007,.007};
        double[][][] rings=new double[points.length][4][];
        for(int i=0;i<points.length;i++){
            double dy,dz;
            if(i==0){dy=0;dz=-1;}
            else if(i==points.length-1){dy=0;dz=1;}
            else{
                double ay=points[i][0]-points[i-1][0],az=points[i][1]-points[i-1][1];
                double by=points[i+1][0]-points[i][0],bz=points[i+1][1]-points[i][1];
                double al=Math.hypot(ay,az),bl=Math.hypot(by,bz);
                dy=ay/al+by/bl;dz=az/al+bz/bl;
            }
            double len=Math.hypot(dy,dz),ny=-dz/len,nz=dy/len,w=radius[i];
            rings[i][0]=p(x-w,points[i][0]-ny*w,points[i][1]-nz*w);
            rings[i][1]=p(x+w,points[i][0]-ny*w,points[i][1]-nz*w);
            rings[i][2]=p(x+w,points[i][0]+ny*w,points[i][1]+nz*w);
            rings[i][3]=p(x-w,points[i][0]+ny*w,points[i][1]+nz*w);
        }
        for(int i=0;i<rings.length-1;i++)for(int j=0;j<4;j++){
            int k=(j+1)%4;
            // Light the upper/front-facing facet and darken side facets for obvious depth.
            double zavg=(rings[i][j][2]+rings[i][k][2]+rings[i+1][j][2]+rings[i+1][k][2])*.25;
            double center=(points[i][1]+points[i+1][1])*.5;
            int shade=j==1?205:j==3?165:zavg<center?255:190;
            faces.add(f(CABLE,shade,rings[i][j],rings[i][k],rings[i+1][k],rings[i+1][j]));
        }
        // No front cap (mates with plug); far cap is hidden in the rack interior.
        double[][] end=rings[rings.length-1];faces.add(f(CABLE,145,end[0],end[1],end[2],end[3]));
        return faces.toArray(new Face[0]);
    }
}
