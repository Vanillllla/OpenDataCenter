package odc;
import java.util.Arrays;
/** A patch cable is represented by one occupied port. Operations are atomic on the server thread. */
final class PortMap {
    static final int REJECT=0,CONNECTED=1,DISCONNECTED=2;
    private PortMap(){}
    static int portOf(int[] ports,int bay){if(bay<0||bay>8)return -1;for(int i=0;i<ports.length;i++)if(ports[i]==bay+1)return i;return -1;}
    static boolean connected(int[] ports,int limit,int bay){int port=portOf(ports,bay);return port>=0&&port<limit;}
    static int toggle(int[] ports,int limit,int port,int bay,boolean cable){if(port<0||port>=limit||port>=ports.length||bay<0||bay>8)return REJECT;
        if(ports[port]==bay+1){ports[port]=0;return DISCONNECTED;}
        if(!cable||ports[port]!=0||portOf(ports,bay)>=0)return REJECT;ports[port]=bay+1;return CONNECTED;}
    static int detach(int[] ports,int bay){int p=portOf(ports,bay);if(p<0)return 0;ports[p]=0;return 1;}
    static int clear(int[] ports){int n=0;for(int v:ports)if(v!=0)n++;Arrays.fill(ports,0);return n;}
}
