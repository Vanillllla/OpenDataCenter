package odc;
/** Central switch catalog. Total capacity is shared by all server ports and uplinks. */
final class SwitchLimits {
    private SwitchLimits(){}
    private static final String[] IDS={"","switch_basic","switch_pro","switch_2p_100","switch_2p_500","switch_2p_1000","switch_2p_10000","switch_4p_1000","switch_4p_10000","switch_8p_10000","switch_8p_50000"};
    private static final int[][] SPEC={{0,0,0,0},{4,200,100,8},{8,1000,1000,20},{2,100,100,5},{2,500,500,5},{2,1000,1000,5},{2,10000,10000,5},{4,1000,1000,8},{4,10000,10000,8},{8,10000,10000,20},{8,50000,50000,20}};
    static int count(){return IDS.length-1;}
    static boolean valid(int tier){return tier>0&&tier<IDS.length;}
    static String id(int tier){return valid(tier)?IDS[tier]:"";}
    static int tier(String model){if(model==null)return 0;for(int i=1;i<IDS.length;i++)if(("odc:"+IDS[i]).equals(model))return i;return 0;}
    static int ports(int tier){return valid(tier)?SPEC[tier][0]:0;}
    static int totalMbps(int tier){return valid(tier)?SPEC[tier][1]:0;}
    static int portMbps(int tier){return valid(tier)?SPEC[tier][2]:0;}
    static int power(int tier){return valid(tier)?SPEC[tier][3]:0;}
    static String shortName(int tier){return ports(tier)+"P / "+totalMbps(tier)+"M";}
    static String panel(int tier){int n=ports(tier);return n==2?"switch_panel_2p":n==4?"switch_panel_1":"switch_panel_2";}
}
