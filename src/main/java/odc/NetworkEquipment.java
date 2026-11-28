package odc;
/** Central nominal equipment catalogue. Speeds are decimal Mbps, not allocated WAN. */
public enum NetworkEquipment {
 RADIO("radio_station",PhysicalTopology.Type.RADIO,500,150),
 SATELLITE("satellite_station",PhysicalTopology.Type.SATELLITE,2000,500),
 TOWER("telecom_tower",PhysicalTopology.Type.TOWER,10000,1500),
 TERMINAL_1("network_terminal",PhysicalTopology.Type.TERMINAL,1000,20),
 TERMINAL_2("network_terminal_t2",PhysicalTopology.Type.TERMINAL,10000,50),
 TERMINAL_3("network_terminal_t3",PhysicalTopology.Type.TERMINAL,100000,100);
 public static final int DATAOS_WATTS=30;
 public final String id; public final PhysicalTopology.Type type; public final int mbps,watts;
 NetworkEquipment(String id,PhysicalTopology.Type type,int mbps,int watts){this.id=id;this.type=type;this.mbps=mbps;this.watts=watts;}
 public boolean terminal(){return type==PhysicalTopology.Type.TERMINAL;}
 public static NetworkEquipment byId(String id){for(NetworkEquipment e:values())if(e.id.equals(id)||("odc:"+e.id).equals(id))return e;return null;}
}
