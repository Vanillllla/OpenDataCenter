package odc;

import java.util.ArrayList;
import java.util.List;

/** Registry of physical models. IDs in assembled server specifications index this table. */
final class Part {
    enum Kind { EMPTY, BOARD, CPU, RAM, HDD, SSD, NVME, PCIE, GPU, NIC, PSU, FAN, CPU_COOLER, GPU_COOLER }
    static final List<Part> models = new ArrayList<>();
    final int index, tier;
    final Kind kind;
    final String name, id;
    int socket, pci, ddr, watts, sockets, ramSlots, pciSlots, m2Slots, sataSlots;
    int cores, mhz, gb, speed, chips, width, mbps, efficiency;
    // Zero criticalC means not subject to electronic thermal failure.
    int criticalC, coolingWatts;
    double conductance, resistance, heatCapacity;
    ChassisSpec.Cooling cooling;
    boolean electronic(){return criticalC>0;}
    boolean liquidCooling(){return cooling==ChassisSpec.Cooling.LIQUID;}

    private Part(Kind kind, String name, int tier) {
        this.kind = kind;
        this.name = name;
        this.tier = tier;
        this.id = (kind.name() + "_" + name).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        index = models.size();
        models.add(this);
    }

    static Part at(int index) {
        return models.get(index > 0 && index < models.size() ? index : 0);
    }

    String socketName() {
        return socket == 6 ? "SP6" : socket == 20113 ? "LGA 2011-3" : "LGA " + socket;
    }

    int onboardNicMbps() { return kind==Kind.BOARD && (tier==3 || sockets==2) ? 1000 : 0; }

    String[] details() {
        String info;
        switch (kind) {
            case BOARD: info = socketName()+"  DDR"+ddr+"  PCIe "+pci+".0\n"+sockets+" CPU   "+ramSlots+" DIMM   "+pciSlots+" PCIe\n"+m2Slots+" M.2   "+sataSlots+" SATA"; break;
            case CPU: info = socketName()+"  DDR"+ddr+"\n"+cores+" cores   "+mhz/1000.0+" GHz"; break;
            case RAM: info = "DDR"+ddr+"   "+gb+" GB   "+mhz+" MHz"; break;
            case GPU: info = chips+" GPU × "+gb+" GB VRAM   "+mhz+" MHz\nPCIe "+pci+".0   "+width+" slots"; break;
            case NIC: info = mbps/1000.0+" Gbit/s   PCIe "+pci+".0"; break;
            case PSU: info = watts+" W | "+efficiency+"%"; break;
            case FAN: info=conductance+" W/K";break;
            case CPU_COOLER: case GPU_COOLER: info=cooling+" | "+coolingWatts+" W thermal | "+resistance+" K/W";break;
            default: info = (kind==Kind.PCIE?"PCIe NVMe":kind==Kind.NVME?"M.2 NVMe":"SATA")+"   "+gb+" GB   "+speed+" MB/s";
        }
        return ("T"+tier+"  |  "+kind+"\n"+info+(kind==Kind.PSU?"":"\n"+watts+" W")).split("\n");
    }

    private static void board(String name, int tier, int socket, int pci, int ddr, int watts, int sockets, int ramSlots, int pciSlots, int m2Slots, int sataSlots) {
        Part p = new Part(Kind.BOARD, name, tier);
        p.socket = socket;
        p.pci = pci;
        p.ddr = ddr;
        p.watts = watts;
        p.sockets = sockets;
        p.ramSlots = ramSlots;
        p.pciSlots = pciSlots;
        p.m2Slots = m2Slots;
        p.sataSlots = sataSlots;
    }

    private static void cpu(String name, int tier, int socket, int ddr, int cores, int mhz, int watts) {
        Part p = new Part(Kind.CPU, name, tier);
        p.socket = socket;
        p.ddr = ddr;
        p.cores = cores;
        p.mhz = mhz;
        p.watts = watts;
    }

    private static void ram(String name, int tier, int ddr, int gb, int mhz, int watts) {
        Part p = new Part(Kind.RAM, name, tier);
        p.ddr = ddr;
        p.gb = gb;
        p.mhz = mhz;
        p.watts = watts;
    }

    private static void drive(Kind kind, String name, int tier, int gb, int speed, int watts) {
        Part p = new Part(kind, name, tier);
        p.gb = gb;
        p.speed = speed;
        p.watts = watts;
    }

    private static void gpu(String name, int tier, int chips, int gb, int mhz, int pci, int watts, int width) {
        Part p = new Part(Kind.GPU, name, tier);
        p.chips = chips;
        p.gb = gb;
        p.mhz = mhz;
        p.pci = pci;
        p.watts = watts;
        p.width = width;
    }

    private static void nic(String name, int tier, int mbps, int pci, int watts) {
        Part p = new Part(Kind.NIC, name, tier);
        p.mbps = mbps;
        p.pci = pci;
        p.watts = watts;
    }

    private static void psu(String name, int tier, int watts, int efficiency) {
        Part p = new Part(Kind.PSU, name, tier);
        p.watts = watts;
        if(efficiency<1||efficiency>100)throw new IllegalArgumentException("PSU efficiency");
        p.efficiency = efficiency;
    }

    private static void thermal(int critical,double capacity){
        Part p=models.get(models.size()-1);p.criticalC=critical;p.heatCapacity=capacity;
    }
    private static void fan(String name,int tier,int watts,double conductance){
        Part p=new Part(Kind.FAN,name,tier);p.watts=watts;p.conductance=conductance;
    }
    private static void cooler(Kind kind,String name,int tier,ChassisSpec.Cooling mode,int watts,int coolingWatts,double resistance){
        Part p=new Part(kind,name,tier);p.cooling=mode;p.watts=watts;p.coolingWatts=coolingWatts;p.resistance=resistance;
    }
    static {
        new Part(Kind.EMPTY, "", 0);
        board("ASBrick H81-S", 1, 1150, 3, 3, 15, 1, 2, 2, 1, 2); thermal(85,180);
        board("SuperMacro X79", 1, 2011, 3, 3, 30, 1, 4, 3, 1, 4); thermal(90,180);
        board("ASBrick X99 Pro", 2, 20113, 4, 4, 40, 1, 4, 4, 1, 6); thermal(88,180);
        board("SuperMacro X612 Dual", 2, 20113, 4, 4, 65, 2, 8, 5, 2, 8); thermal(95,180);
        board("ASBrick Rack EP2C612 WS", 2, 20113, 3, 4, 75, 2, 8, 7, 1, 10); thermal(95,180);
        board("ASUSpect Z890 Extreme", 3, 1851, 5, 5, 55, 1, 4, 5, 2, 6); thermal(90,180);
        board("GigaBite SP6 Titan", 3, 6, 5, 5, 75, 1, 8, 6, 2, 10); thermal(95,180);
        board("SuperMacro SP6 Enterprise Dual", 3, 6, 5, 5, 100, 2, 8, 7, 2, 12); thermal(100,180);
        cpu("InTroll Celoron G1820", 1, 1150, 3, 2, 2400, 35); thermal(100,45);
        cpu("InTroll CoreX 4770", 1, 1150, 3, 4, 3400, 65); thermal(100,45);
        cpu("InTroll Zeon 2670 v2", 1, 2011, 3, 10, 2500, 95); thermal(95,45);
        cpu("InTroll Zeon 2630 v4", 2, 20113, 4, 10, 2200, 85); thermal(95,45);
        cpu("InTroll Zeon 2680 v4", 2, 20113, 4, 14, 3100, 110); thermal(95,45);
        cpu("InTroll Zeon 2696 v4", 2, 20113, 4, 44, 2460, 120); thermal(100,45);
        cpu("InTroll Core UltraX 990", 3, 1851, 5, 24, 5200, 180); thermal(105,45);
        cpu("AMDead Epyx 9654", 3, 6, 5, 64, 3200, 280); thermal(95,45);
        cpu("AMDead Epyx 9996 Titan", 3, 6, 5, 128, 3800, 350); thermal(100,45);
        ram("KingStoned Value 4", 1, 3, 4, 1333, 5); thermal(85,30);
        ram("KingStoned Value 8", 1, 3, 8, 1600, 7); thermal(85,30);
        ram("Susung Server 16", 1, 3, 16, 1866, 10); thermal(90,30);
        ram("Susung Server 16 Pro", 2, 4, 16, 2400, 10); thermal(85,30);
        ram("KingStoned Fury 32", 2, 4, 32, 3200, 12); thermal(90,30);
        ram("Susung Enterprise 64", 2, 4, 64, 3200, 15); thermal(95,30);
        ram("CorsError Vengeance 32", 3, 5, 32, 4800, 12); thermal(85,30);
        ram("KingStoned Fury 64", 3, 5, 64, 5600, 18); thermal(90,30);
        ram("Susung Enterprise 128", 3, 5, 128, 6400, 25); thermal(95,30);
        drive(Kind.HDD, "SeaBait Basic 256", 1, 256, 100, 15); thermal(60,120);
        drive(Kind.HDD, "SeaBait Barracuda 1", 1, 1024, 150, 20); thermal(65,120);
        drive(Kind.HDD, "Eastern Digital Red 4", 2, 4096, 220, 25); thermal(65,120);
        drive(Kind.HDD, "SeaBait IronWolf 8", 2, 8192, 280, 30); thermal(70,120);
        drive(Kind.HDD, "SeaBait Exos 12", 3, 12288, 350, 35); thermal(65,120);
        drive(Kind.HDD, "SeaBait Exos Titan 16", 3, 16384, 500, 40); thermal(70,120);
        drive(Kind.SSD, "KingStoned A400", 1, 128, 200, 10); thermal(70,35);
        drive(Kind.SSD, "KingStoned A500", 1, 512, 350, 12); thermal(75,35);
        drive(Kind.SSD, "Susung 860 EVO", 2, 1024, 450, 14); thermal(80,35);
        drive(Kind.SSD, "Susung 870 PRO", 2, 2048, 500, 15); thermal(80,35);
        drive(Kind.SSD, "Susung Enterprise 4", 3, 4096, 520, 18); thermal(85,35);
        drive(Kind.SSD, "Susung Enterprise 8", 3, 8192, 550, 20); thermal(85,35);
        drive(Kind.NVME, "KingStoned NV1000", 1, 512, 6000, 10); thermal(75,20);
        drive(Kind.NVME, "KingStoned NV2000", 1, 1024, 6200, 12); thermal(80,20);
        drive(Kind.NVME, "Susung 980 EVO", 2, 1024, 6500, 15); thermal(85,20);
        drive(Kind.NVME, "Susung 990 PRO", 2, 2048, 7000, 18); thermal(85,20);
        drive(Kind.NVME, "Susung 1100 PRO", 3, 2048, 7500, 20); thermal(90,20);
        drive(Kind.NVME, "Susung 1200 Titan", 3, 4096, 8000, 25); thermal(90,20);
        drive(Kind.PCIE, "InTroll Optan P100", 1, 512, 6000, 10); thermal(80,45);
        drive(Kind.PCIE, "InTroll Optan P200", 1, 1024, 6300, 13); thermal(80,45);
        drive(Kind.PCIE, "InTroll Optan P4800", 2, 1024, 6700, 16); thermal(85,45);
        drive(Kind.PCIE, "InTroll Optan P5800", 2, 2048, 7200, 19); thermal(90,45);
        drive(Kind.PCIE, "InTroll Optan P9800", 3, 2048, 7800, 22); thermal(90,45);
        drive(Kind.PCIE, "InTroll Optan P9900 Titan", 3, 4096, 8000, 25); thermal(95,45);
        gpu("NoVideo GTX 750", 1, 1, 2, 1100, 3, 55, 1); thermal(95,140);
        gpu("NoVideo GTX 1060", 1, 1, 6, 1500, 3, 120, 2); thermal(95,140);
        gpu("NoVideo Teslo P4", 1, 1, 8, 1200, 3, 75, 1); thermal(90,140);
        gpu("NoVideo Teslo M10", 1, 4, 8, 1033, 3, 225, 2); thermal(90,140);
        gpu("NoVideo RTX 3080", 2, 1, 10, 1750, 4, 280, 3); thermal(95,140);
        gpu("NoVideo Teslo T4", 2, 1, 16, 1600, 4, 100, 1); thermal(95,140);
        gpu("NoVideo RTX A6000", 2, 1, 48, 1800, 4, 300, 2); thermal(95,140);
        gpu("NoVideo Teslo M20", 2, 4, 16, 1500, 4, 300, 2); thermal(90,140);
        gpu("NoVideo RTX 6090", 3, 1, 32, 2800, 5, 450, 4); thermal(100,140);
        gpu("NoVideo H200X", 3, 1, 96, 2000, 5, 400, 2); thermal(95,140);
        gpu("NoVideo B300 Titan", 3, 1, 192, 2400, 5, 600, 3); thermal(100,140);
        gpu("NoVideo Teslo M30", 3, 4, 32, 2200, 5, 500, 3); thermal(95,140);
        nic("FakeTek G1000", 1, 1000, 3, 10); thermal(85,40);
        nic("InTroll Ethernet X2500", 1, 2500, 3, 12); thermal(85,40);
        nic("InTroll Ethernet X550", 2, 10000, 4, 14); thermal(90,40);
        nic("Melonox Connect 25", 2, 25000, 4, 16); thermal(90,40);
        nic("Melonox Connect 40", 3, 40000, 5, 18); thermal(95,40);
        nic("Melonox Connect 100", 3, 100000, 5, 20); thermal(95,40);
        psu("AeroHot VX500", 1, 500, 80); thermal(80,150);
        psu("AeroHot VX650", 1, 650, 80); thermal(80,150);
        psu("CorsError RM750 Gold", 2, 750, 90); thermal(85,150);
        psu("CorsError RM1000 Gold", 2, 1000, 90); thermal(85,150);
        psu("SuperMacro 1200 Platinum", 3, 1200, 92); thermal(90,150);
        psu("SuperMacro 1500 Titanium", 3, 1500, 94); thermal(95,150);
        // Append only: existing model indices are used in persisted task reservations.
        fan("Breeze 40",1,3,2);
        fan("Breeze 60",2,6,4);
        fan("Breeze Turbo",3,12,7);
        cooler(Kind.CPU_COOLER, "Passive Mini",1,ChassisSpec.Cooling.PASSIVE,0,65,0.65);
        cooler(Kind.CPU_COOLER, "Passive Copper",2,ChassisSpec.Cooling.PASSIVE,0,125,0.36);
        cooler(Kind.CPU_COOLER, "Passive Titan",3,ChassisSpec.Cooling.PASSIVE,0,200,0.22);
        cooler(Kind.CPU_COOLER, "Flat 80",1,ChassisSpec.Cooling.FLAT,4,100,0.42);
        cooler(Kind.CPU_COOLER, "Flat 150",2,ChassisSpec.Cooling.FLAT,7,180,0.24);
        cooler(Kind.CPU_COOLER, "Flat Turbo",3,ChassisSpec.Cooling.FLAT,12,250,0.16);
        cooler(Kind.CPU_COOLER, "Tower 120",1,ChassisSpec.Cooling.TOWER,5,160,0.26);
        cooler(Kind.CPU_COOLER, "Tower Dual",2,ChassisSpec.Cooling.TOWER,9,260,0.15);
        cooler(Kind.CPU_COOLER, "Tower Titan",3,ChassisSpec.Cooling.TOWER,15,380,0.1);
        cooler(Kind.CPU_COOLER, "Hydro CPU 150",1,ChassisSpec.Cooling.LIQUID,0,150,0.2);
        cooler(Kind.CPU_COOLER, "Hydro CPU 300",2,ChassisSpec.Cooling.LIQUID,0,300,0.1);
        cooler(Kind.CPU_COOLER, "Hydro CPU 500",3,ChassisSpec.Cooling.LIQUID,0,500,0.06);
        cooler(Kind.GPU_COOLER, "Hydro GPU 200",1,ChassisSpec.Cooling.LIQUID,0,200,0.18);
        cooler(Kind.GPU_COOLER, "Hydro GPU 400",2,ChassisSpec.Cooling.LIQUID,0,400,0.09);
        cooler(Kind.GPU_COOLER, "Hydro GPU 800",3,ChassisSpec.Cooling.LIQUID,0,800,0.045);
    }
}
