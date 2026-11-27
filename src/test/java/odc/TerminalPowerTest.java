package odc;
/** Regression checks for power gate and valid physical LAN ports. No Minecraft runtime required. */
public final class TerminalPowerTest {
    private static int checks;
    private static void check(boolean b){checks++;if(!b)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        for(boolean online:new boolean[]{false,true})for(boolean fed:new boolean[]{false,true})check(TerminalPower.operational(online,fed)==(online&&fed));
        for(int tier:new int[]{2,4,8})for(int bay=0;bay<9;bay++)for(int port=0;port<8;port++){
            int[] ports=new int[8];check(!PortMap.connected(ports,tier,bay));ports[port]=bay+1;
            check(PortMap.connected(ports,tier,bay)==(port<tier));check(!PortMap.connected(ports,0,bay));
            PortMap.detach(ports,bay);check(!PortMap.connected(ports,tier,bay));
        }
        check(!PortMap.connected(new int[8],8,-1));check(!PortMap.connected(new int[8],8,9));
        System.out.println("Terminal power and LAN visibility PASS: "+checks+" checks");
    }
}
