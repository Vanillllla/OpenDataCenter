package odc;
/** Single catalogue for rack equipment, rendering and the bandwidth graph. All speeds are Mb/s. */
final class SwitchLimits {
    private SwitchLimits(){}
    static final class Model {
        final String id; final int ports,total,port,power;
        Model(String id,int ports,int total,int port,int power){this.id=id;this.ports=ports;this.total=total;this.port=port;this.power=power;}
    }
    private static final Model[] MODELS={null,
        new Model("switch_basic",4,200,100,8),
        new Model("switch_pro",8,1000,1000,20),
        new Model("switch_2p_100",2,100,100,8),
        new Model("switch_2p_500",2,500,1000,8),
        new Model("switch_2p_1000",2,1000,1000,8),
        new Model("switch_2p_10000",2,10000,10000,8),
        new Model("switch_4p_1000",4,1000,1000,8),
        new Model("switch_4p_10000",4,10000,10000,8),
        new Model("switch_8p_10000",8,10000,10000,20),
        new Model("switch_8p_50000",8,50000,10000,20),
    };
    static int count(){return MODELS.length-1;}
    static Model model(int tier){return tier>0&&tier<MODELS.length?MODELS[tier]:null;}
    static int tier(String model){if(model==null)return 0;for(int i=1;i<MODELS.length;i++)if(("odc:"+MODELS[i].id).equals(model))return i;return 0;}
    static int ports(int tier){Model m=model(tier);return m==null?0:m.ports;}
    static int totalMbps(int tier){Model m=model(tier);return m==null?0:m.total;}
    static int portMbps(int tier){Model m=model(tier);return m==null?0:m.port;}
    static int power(int tier){Model m=model(tier);return m==null?0:m.power;}
    static String panel(int tier){int n=ports(tier);return n==2?"switch_panel_2port":n==4?"switch_panel_1":"switch_panel_2";}
    static String label(int tier){Model m=model(tier);return m==null?"-":m.ports+"P / "+m.total+" Mb/s";}
}
