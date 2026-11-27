package odc;

import java.util.ArrayList;
import java.util.List;

/** Registry of physical models. IDs in assembled server specifications index this table. */
final class Part {
    enum Kind { EMPTY, BOARD, CPU, RAM, HDD, SSD, NVME, PCIE, GPU, NIC, PSU }
    static final List<Part> models = new ArrayList<>();
    final int index, tier;
    final Kind kind;
    final String name, id;
    int socket, pci, ddr, watts, sockets, ramSlots, pciSlots, m2Slots, sataSlots;
    int cores, mhz, gb, speed, chips, width, mbps, fe;

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

    String[] details() {
        String info;
        switch (kind) {
            case BOARD: info = socketName()+"  DDR"+ddr+"  PCIe "+pci+".0\n"+sockets+" CPU   "+ramSlots+" DIMM   "+pciSlots+" PCIe\n"+m2Slots+" M.2   "+sataSlots+" SATA"; break;
            case CPU: info = socketName()+"  DDR"+ddr+"\n"+cores+" cores   "+mhz/1000.0+" GHz"; break;
            case RAM: info = "DDR"+ddr+"   "+gb+" GB   "+mhz+" MHz"; break;
            case GPU: info = chips+" GPU × "+gb+" GB VRAM   "+mhz+" MHz\nPCIe "+pci+".0   "+width+" slots"; break;
            case NIC: info = mbps/1000.0+" Gbit/s   PCIe "+pci+".0"; break;
            case PSU: info = watts+" W   "+fe+" FE/t"; break;
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

    private static void psu(String name, int tier, int watts, int fe) {
        Part p = new Part(Kind.PSU, name, tier);
        p.watts = watts;
        p.fe = fe;
    }

    static {
        new Part(Kind.EMPTY, "", 0);
        board("ASBrick H81-S", 1, 1150, 3, 3, 15, 1, 2, 2, 1, 2);
        board("SuperMacro X79", 1, 2011, 3, 3, 30, 1, 4, 3, 1, 4);
        board("ASBrick X99 Pro", 2, 20113, 4, 4, 40, 1, 4, 4, 1, 6);
        board("SuperMacro X612 Dual", 2, 20113, 4, 4, 65, 2, 8, 5, 2, 8);
        board("ASBrick Rack EP2C612 WS", 2, 20113, 3, 4, 75, 2, 8, 7, 1, 10);
        board("ASUSpect Z890 Extreme", 3, 1851, 5, 5, 55, 1, 4, 5, 2, 6);
        board("GigaBite SP6 Titan", 3, 6, 5, 5, 75, 1, 8, 6, 2, 10);
        board("SuperMacro SP6 Enterprise Dual", 3, 6, 5, 5, 100, 2, 8, 7, 2, 12);
        cpu("InTroll Celoron G1820", 1, 1150, 3, 2, 2400, 35);
        cpu("InTroll CoreX 4770", 1, 1150, 3, 4, 3400, 65);
        cpu("InTroll Zeon 2670 v2", 1, 2011, 3, 10, 2500, 95);
        cpu("InTroll Zeon 2630 v4", 2, 20113, 4, 10, 2200, 85);
        cpu("InTroll Zeon 2680 v4", 2, 20113, 4, 14, 3100, 110);
        cpu("InTroll Zeon 2696 v4", 2, 20113, 4, 44, 2460, 120);
        cpu("InTroll Core UltraX 990", 3, 1851, 5, 24, 5200, 180);
        cpu("AMDead Epyx 9654", 3, 6, 5, 64, 3200, 280);
        cpu("AMDead Epyx 9996 Titan", 3, 6, 5, 128, 3800, 350);
        ram("KingStoned Value 4", 1, 3, 4, 1333, 5);
        ram("KingStoned Value 8", 1, 3, 8, 1600, 7);
        ram("Susung Server 16", 1, 3, 16, 1866, 10);
        ram("Susung Server 16 Pro", 2, 4, 16, 2400, 10);
        ram("KingStoned Fury 32", 2, 4, 32, 3200, 12);
        ram("Susung Enterprise 64", 2, 4, 64, 3200, 15);
        ram("CorsError Vengeance 32", 3, 5, 32, 4800, 12);
        ram("KingStoned Fury 64", 3, 5, 64, 5600, 18);
        ram("Susung Enterprise 128", 3, 5, 128, 6400, 25);
        drive(Kind.HDD, "SeaBait Basic 256", 1, 256, 100, 15);
        drive(Kind.HDD, "SeaBait Barracuda 1", 1, 1024, 150, 20);
        drive(Kind.HDD, "Eastern Digital Red 4", 2, 4096, 220, 25);
        drive(Kind.HDD, "SeaBait IronWolf 8", 2, 8192, 280, 30);
        drive(Kind.HDD, "SeaBait Exos 12", 3, 12288, 350, 35);
        drive(Kind.HDD, "SeaBait Exos Titan 16", 3, 16384, 500, 40);
        drive(Kind.SSD, "KingStoned A400", 1, 128, 200, 10);
        drive(Kind.SSD, "KingStoned A500", 1, 512, 350, 12);
        drive(Kind.SSD, "Susung 860 EVO", 2, 1024, 450, 14);
        drive(Kind.SSD, "Susung 870 PRO", 2, 2048, 500, 15);
        drive(Kind.SSD, "Susung Enterprise 4", 3, 4096, 520, 18);
        drive(Kind.SSD, "Susung Enterprise 8", 3, 8192, 550, 20);
        drive(Kind.NVME, "KingStoned NV1000", 1, 512, 6000, 10);
        drive(Kind.NVME, "KingStoned NV2000", 1, 1024, 6200, 12);
        drive(Kind.NVME, "Susung 980 EVO", 2, 1024, 6500, 15);
        drive(Kind.NVME, "Susung 990 PRO", 2, 2048, 7000, 18);
        drive(Kind.NVME, "Susung 1100 PRO", 3, 2048, 7500, 20);
        drive(Kind.NVME, "Susung 1200 Titan", 3, 4096, 8000, 25);
        drive(Kind.PCIE, "InTroll Optan P100", 1, 512, 6000, 10);
        drive(Kind.PCIE, "InTroll Optan P200", 1, 1024, 6300, 13);
        drive(Kind.PCIE, "InTroll Optan P4800", 2, 1024, 6700, 16);
        drive(Kind.PCIE, "InTroll Optan P5800", 2, 2048, 7200, 19);
        drive(Kind.PCIE, "InTroll Optan P9800", 3, 2048, 7800, 22);
        drive(Kind.PCIE, "InTroll Optan P9900 Titan", 3, 4096, 8000, 25);
        gpu("NoVideo GTX 750", 1, 1, 2, 1100, 3, 55, 1);
        gpu("NoVideo GTX 1060", 1, 1, 6, 1500, 3, 120, 2);
        gpu("NoVideo Teslo P4", 1, 1, 8, 1200, 3, 75, 1);
        gpu("NoVideo Teslo M10", 1, 4, 8, 1033, 3, 225, 2);
        gpu("NoVideo RTX 3080", 2, 1, 10, 1750, 4, 280, 3);
        gpu("NoVideo Teslo T4", 2, 1, 16, 1600, 4, 100, 1);
        gpu("NoVideo RTX A6000", 2, 1, 48, 1800, 4, 300, 2);
        gpu("NoVideo Teslo M20", 2, 4, 16, 1500, 4, 300, 2);
        gpu("NoVideo RTX 6090", 3, 1, 32, 2800, 5, 450, 4);
        gpu("NoVideo H200X", 3, 1, 96, 2000, 5, 400, 2);
        gpu("NoVideo B300 Titan", 3, 1, 192, 2400, 5, 600, 3);
        gpu("NoVideo Teslo M30", 3, 4, 32, 2200, 5, 500, 3);
        nic("FakeTek G1000", 1, 1000, 3, 10);
        nic("InTroll Ethernet X2500", 1, 2500, 3, 12);
        nic("InTroll Ethernet X550", 2, 10000, 4, 14);
        nic("Melonox Connect 25", 2, 25000, 4, 16);
        nic("Melonox Connect 40", 3, 40000, 5, 18);
        nic("Melonox Connect 100", 3, 100000, 5, 20);
        psu("AeroHot VX500", 1, 500, 1000);
        psu("AeroHot VX650", 1, 650, 1300);
        psu("CorsError RM750 Gold", 2, 750, 1350);
        psu("CorsError RM1000 Gold", 2, 1000, 1800);
        psu("SuperMacro 1200 Platinum", 3, 1200, 1920);
        psu("SuperMacro 1500 Titanium", 3, 1500, 2250);
    }
}
