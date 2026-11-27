package odc;
/** Pure validation/transition helper; inventory is debited by the caller only after validation. */
final class LanPorts {
 static boolean canConnect(int[] ports,int limit,int port,int bay,int tier){return port>=0&&port<limit&&port<ports.length&&bay>=0&&bay<9&&LogicalLinks.speed(tier)>0&&ports[port]==0&&PortMap.portOf(ports,bay)<0;}
 static int tier(int[] ports,int[] tiers,int port){return port>=0&&port<ports.length&&ports[port]>0?(LogicalLinks.speed(tiers[port])>0?tiers[port]:1):0;}
 static boolean canUpgrade(int[] ports,int[] tiers,int limit,int port,int next){return port>=0&&port<limit&&port<ports.length&&ports[port]>0&&LogicalLinks.speed(next)>LogicalLinks.speed(tier(ports,tiers,port));}
 static int remove(int[] ports,int[] tiers,int port){int old=tier(ports,tiers,port);if(old!=0){ports[port]=0;tiers[port]=0;}return old;}
}
