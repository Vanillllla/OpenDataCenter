package odc;

/** Verifies the same vertices consumed by RackRenderer, without Forge. */
public final class PatchCordMeshTest {
    private static int checks;
    private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-9;}
    public static void main(String[] args){
        int variants=0;
        for(int capacity:new int[]{4,9})for(int bay=0;bay<capacity;bay++)for(int count:new int[]{4,8})for(int port=0;port<count;port++)for(boolean up:new boolean[]{false,true}){
            variants++;double pitch=capacity==9?1.875/9:.875/4,b=.0625+bay*pitch+.012,t=.0625+(bay+1)*pitch-.012;
            double cx=PatchCordMesh.portX(port,count),cy=PatchCordMesh.portY(b,t),gap=up?t+.012:b-.012;
            PatchCordMesh.Face[] mesh=PatchCordMesh.get(capacity,bay,count,port,up);
            check(mesh==PatchCordMesh.get(capacity,bay,count,port,up),"cached mesh");
            check(mesh.length==26,"face budget");
            int plugs=0,cables=0;
            for(PatchCordMesh.Face f:mesh){
                if(f.material==PatchCordMesh.PLUG)plugs++;else cables++;
                for(double[] p:f.vertices){
                    check(p[2]>PatchCordMesh.GLASS_Z+.015,"glass clearance");
                    check(p[0]>=cx-.023&&p[0]<=cx+.023,"port footprint");
                    check(p[1]>=b-.023&&p[1]<=t+.023,"no intrusion into next module");
                    if(f.material==PatchCordMesh.CABLE&&p[2]>PatchCordMesh.PANEL_Z)check(Math.abs(p[1]-gap)<=.0071,"rear section stays in gap");
                }
                double[] a=f.vertices[0],d=f.vertices[1],e=f.vertices[2];double ux=d[0]-a[0],uy=d[1]-a[1],uz=d[2]-a[2],vx=e[0]-a[0],vy=e[1]-a[1],vz=e[2]-a[2];
                check(Math.hypot(Math.hypot(uy*vz-uz*vy,uz*vx-ux*vz),ux*vy-uy*vx)>1e-8,"nondegenerate face");
            }
            check(plugs==5&&cables==21,"connector and swept mesh");
            double sx=0,sy=0;for(double[] p:mesh[0].vertices){sx+=p[0];sy+=p[1];check(near(p[2],.115),"plug face");}
            check(near(sx/4,cx)&&near(sy/4,cy),"plug center equals port center");
            for(double[] p:mesh[25].vertices){check(near(p[2],.225),"hidden far end");check(Math.abs(p[1]-gap)<=.0071,"end in gap");}
            // Shared ring edge between successive cable segments, no seams or internal caps.
            for(int i=0;i<4;i++)for(int j=0;j<4;j++){
                PatchCordMesh.Face f=mesh[5+i*4+j],g=mesh[5+(i+1)*4+j];
                check(f.vertices[2]==g.vertices[1]&&f.vertices[3]==g.vertices[0],"continuous rings");
            }
        }
        System.out.println("PASS: "+checks+" patch mesh checks; "+variants+" rack/port/direction combinations; 26 quads per connected port.");
    }
}
