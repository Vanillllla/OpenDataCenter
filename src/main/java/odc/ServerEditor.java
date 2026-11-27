package odc;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** A live, exclusive view of the actual server item installed in a rack.
 * Components are persisted on every change; closing the GUI never returns or drops them.
 */
final class ServerEditor extends InventoryBasic {
    private final Tiles.Rack rack;
    private final int bay;
    private final ItemStack server;
    private boolean active;
    ServerEditor(Tiles.Rack rack,int bay){
        super("Server hardware",false,ServerSpec.COUNT);
        this.rack=rack;this.bay=bay;this.server=rack.getStackInSlot(bay);
        NonNullList<ItemStack> parts=Hardware.parts(server);
        for(int i=0;i<parts.size();i++)super.setInventorySlotContents(i,parts.get(i).copy());
        active=rack.beginEdit(bay,this);
    }
    boolean valid(){return active&&rack.editors[bay]==this&&rack.getWorld()!=null&&rack.getWorld().getTileEntity(rack.getPos())==rack&&rack.getStackInSlot(bay)==server;}
    void close(){if(active){active=false;rack.endEdit(bay,this);}}
    @Override public int getInventoryStackLimit(){return 1;}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return valid()&&Hardware.accepts(slot,stack)&&ServerSpec.fits(Hardware.units(server),Hardware.partTier(0,getStackInSlot(0)),slot);}
    @Override public ItemStack removeStackFromSlot(int index){ItemStack removed=super.removeStackFromSlot(index);if(!removed.isEmpty())markDirty();return removed;}
    @Override public void clear(){super.clear();markDirty();}
    @Override public void markDirty(){super.markDirty();if(!valid())return;
        NonNullList<ItemStack> parts=Hardware.emptyParts();for(int i=0;i<parts.size();i++)parts.set(i,getStackInSlot(i).copy());
        NonNullList<ItemStack> before=Hardware.parts(server);boolean changed=false;for(int i=0;i<parts.size();i++)if(!ItemStack.areItemStacksEqual(before.get(i),parts.get(i))){changed=true;break;}
        if(changed){Hardware.writeParts(server,parts);rack.partsChanged(bay);}
    }
}
