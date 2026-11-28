package odc;

/** Immutable-by-contract thermal parameters of a hardware configuration.
 * Cache with the hardware signature in the thermal engine. No temperature
 * state, world access or cooling simulation belongs in this class. */
final class ThermalProfile {
    final ChassisSpec chassis;
    private final int[] models;
    private final boolean[] compatibleCooling=new boolean[ServerSpec.COUNT-ServerSpec.CPU_COOLER];
    final double airConductance, airHeatCapacity, internalHeatCapacity;
    final int coolingElectricalWatts, internalLimitC, liquidRatedWatts;
    ThermalProfile(ChassisSpec chassis,int[] parts){
        this.chassis=chassis;
        models=ServerSpec.upgradeParts(parts).clone();
        if(models.length!=ServerSpec.COUNT)throw new IllegalArgumentException("hardware length");
        double air=2.0,inside=100;int electrical=0,limit=0,liquid=0;
        for(int slot=0;slot<models.length;slot++){
            Part p=part(slot);
            if(slot>=ServerSpec.CPU_COOLER&&p.kind!=Part.Kind.EMPTY&&ServerSpec.accepts(chassis,models,slot,p)){
                compatibleCooling[slot-ServerSpec.CPU_COOLER]=true;
                if(p.kind==Part.Kind.FAN)air+=p.conductance;
                electrical+=p.watts;if(p.liquidCooling())liquid+=p.coolingWatts;
            }
            if(p.electronic()&&p.kind!=Part.Kind.CPU&&p.kind!=Part.Kind.GPU){
                inside+=p.heatCapacity;limit=limit==0?p.criticalC:Math.min(limit,p.criticalC);
            }
        }
        airConductance=air;airHeatCapacity=40*(chassis==null?1:chassis.units);internalHeatCapacity=inside;
        coolingElectricalWatts=electrical;internalLimitC=limit;liquidRatedWatts=liquid;
    }
    Part part(int slot){return Part.at(models[slot]);}
    private boolean installedCooling(int slot){return slot>=ServerSpec.CPU_COOLER&&slot<ServerSpec.COUNT&&compatibleCooling[slot-ServerSpec.CPU_COOLER];}
    Part cooler(int componentSlot){int slot=ServerSpec.linkedCoolingSlot(componentSlot);return slot>=0&&installedCooling(slot)?part(slot):Part.at(0);}
    int criticalC(int slot){return part(slot).criticalC;}
    double heatCapacity(int slot){return part(slot).heatCapacity;}
    boolean liquid(int slot){return cooler(slot).liquidCooling();}
    int coolingRating(int slot){return cooler(slot).coolingWatts;}
    // Nominal resistance to the selected sink, before airflow/contour availability.
    double resistance(int slot){
        Part p=part(slot),c=cooler(slot);
        if(c.cooling!=null)return c.resistance;
        if(p.kind==Part.Kind.CPU)return 2.5; // no CPU cooler
        if(p.kind==Part.Kind.GPU)return .20; // GPU heat into chassis air
        return 0;
    }
    boolean requiresCaseAirflow(int slot){Part p=part(slot),c=cooler(slot);return p.kind==Part.Kind.GPU&&!c.liquidCooling()||p.kind==Part.Kind.CPU&&(c.cooling==null||c.cooling==ChassisSpec.Cooling.PASSIVE);}
    int nominalWatts(int slot){Part p=part(slot);return p.kind==Part.Kind.PSU?0:p.watts;}
}
