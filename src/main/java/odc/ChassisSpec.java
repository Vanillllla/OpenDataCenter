package odc;

/** Stable item IDs and physical constraints independent of rack height. */
enum ChassisSpec {
    BASIC_1("chassis_1u",1,2,1,4,2,false),
    COMPACT_1("chassis_1u_compact",1,1,1,6,1,false),
    COMPUTE_1("chassis_1u_compute",1,2,1,8,2,true),
    BASIC_2("chassis_2u",2,4,2,4,2,true),
    COMPACT_2("chassis_2u_compact",2,3,1,6,1,false),
    COMPUTE_2("chassis_2u_compute",2,4,2,8,2,true),
    BASIC_4("chassis_4u",4,7,4,6,2,true),
    STORAGE_4("chassis_4u_storage",4,5,2,6,2,false),
    GPU_4("chassis_4u_gpu",4,7,4,8,2,true);
    enum Cooling { PASSIVE, FLAT, TOWER, LIQUID }
    final String id;
    final int units,pcieSlots,psuSlots,fanSlots,maxSockets;
    final boolean liquid;
    private final int coolingMask;
    ChassisSpec(String id,int units,int pcie,int psu,int fans,int sockets,boolean liquid){
        if((units!=1&&units!=2&&units!=4)||pcie<1||pcie>7||psu<1||psu>units||fans<1||fans>8||sockets<1||sockets>2)throw new IllegalArgumentException(id);
        this.id=id;this.units=units;pcieSlots=pcie;psuSlots=psu;fanSlots=fans;maxSockets=sockets;this.liquid=liquid;
        coolingMask=(1<<Cooling.PASSIVE.ordinal())|(1<<Cooling.FLAT.ordinal())|(units==4?1<<Cooling.TOWER.ordinal():0)|(liquid?1<<Cooling.LIQUID.ordinal():0);
    }
    boolean supports(Part board){return board.kind==Part.Kind.BOARD&&board.sockets<=maxSockets;}
    boolean supports(Cooling cooling){return cooling!=null&&(coolingMask&(1<<cooling.ordinal()))!=0;}
    static ChassisSpec legacy(int units){return units==1?BASIC_1:units==2?BASIC_2:units==4?BASIC_4:null;}
    static ChassisSpec byId(String id){for(ChassisSpec model:values())if(model.id.equals(id))return model;return null;}
}
