package odc;

/** Offline preview export. Uses the exact same mesh as the game renderer. */
public final class PatchCordPreview {
    public static void main(String[] args) {
        StringBuilder out=new StringBuilder("{");boolean first=true;
        for(int cap:new int[]{4,9})for(int bay=0;bay<cap;bay++)for(int count:new int[]{2,4,8})for(int port=0;port<count;port++)for(int up=0;up<2;up++){
            if(!first)out.append(',');first=false;
            out.append('"').append(cap).append('/').append(bay).append('/').append(count).append('/').append(port).append('/').append(up).append("\":[");
            PatchCordMesh.Face[] mesh=PatchCordMesh.get(cap,bay,count,port,up==1);
            for(int i=0;i<mesh.length;i++){
                if(i>0)out.append(',');PatchCordMesh.Face f=mesh[i];out.append("{\"material\":").append(f.material).append(",\"shade\":").append(f.shade).append(",\"vertices\":[");
                for(int j=0;j<4;j++){if(j>0)out.append(',');double[] p=f.vertices[j];out.append('[').append(p[0]*16).append(',').append(p[1]*16).append(',').append(p[2]*16).append(']');}
                out.append("]}");
            }
            out.append(']');
        }
        System.out.println(out.append('}'));
    }
}
