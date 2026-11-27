package odc;
/** Existing switch specifications shared by rack hardware and bandwidth topology. */
final class SwitchLimits {
    private SwitchLimits(){}
    static int tier(String model){return "odc:switch_basic".equals(model)?1:"odc:switch_pro".equals(model)?2:0;}
    static int ports(int tier){return tier==1?4:tier==2?8:0;}
    static int totalMbps(int tier){return tier==1?200:tier==2?2000:0;}
    static int portMbps(int tier){return tier==1?100:tier==2?1000:0;}
}
