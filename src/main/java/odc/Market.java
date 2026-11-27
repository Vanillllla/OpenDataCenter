package odc;
import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public final class Market {
    static final class Entry {
        String item; int count,priceMilli;
        Entry(String item,int count,int price){this.item=item;this.count=count;this.priceMilli=price;}
        ItemStack stack(){try{Item i=ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));if(i==null||count<1||count>64||priceMilli<1)return ItemStack.EMPTY;ItemStack s=new ItemStack(i,count);return count>s.getMaxStackSize()?ItemStack.EMPTY:s;}catch(RuntimeException e){return ItemStack.EMPTY;}}
    }
    static Entry[] defaults(){return new Entry[]{new Entry("minecraft:iron_ingot",16,5000),new Entry("minecraft:gold_ingot",8,10000),new Entry("minecraft:diamond",1,40000),new Entry("odc:precision_chip",1,25000)};}
    static Entry[] load(File folder){File file=new File(folder,"odc-market.json");Gson g=new GsonBuilder().setPrettyPrinting().create();try{if(!file.exists()){Files.write(file.toPath(),g.toJson(defaults()).getBytes(StandardCharsets.UTF_8));return defaults();}try(Reader r=Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)){Entry[] es=g.fromJson(r,Entry[].class);if(es!=null&&es.length<=4){for(Entry e:es)if(e==null||e.item==null||e.priceMilli<=0||e.count<1||e.count>64)throw new IOException("Invalid item");return es;}}}catch(Exception e){System.err.println("[ODC] Market configuration invalid: "+e);}return defaults();}
    public static final class Ledger extends WorldSavedData {
        private NBTTagCompound balances=new NBTTagCompound();
        public Ledger(){super("odc_wallets");} public Ledger(String name){super(name);}
        static Ledger get(World w){net.minecraft.world.storage.MapStorage s=w.getMapStorage();Ledger l=(Ledger)s.getOrLoadData(Ledger.class,"odc_wallets");if(l==null){l=new Ledger();s.setData("odc_wallets",l);}return l;}
        int balance(UUID id){return id==null?0:Math.max(0,balances.getInteger(id.toString()));}
        void credit(UUID id,int amount){if(id==null||amount<=0)return;balances.setInteger(id.toString(),(int)Math.min(Integer.MAX_VALUE,(long)balance(id)+amount));markDirty();}
        boolean debit(UUID id,int amount){if(amount<=0||balance(id)<amount)return false;balances.setInteger(id.toString(),balance(id)-amount);markDirty();return true;}
        @Override public void readFromNBT(NBTTagCompound n){balances=n.getCompoundTag("Balances");}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n.setTag("Balances",balances);return n;}
    }
    static boolean buy(EntityPlayer p,int index){if(index<0||index>=ODC.market.length)return false;Entry e=ODC.market[index];ItemStack s=e.stack();if(s.isEmpty())return false;Ledger l=Ledger.get(p.world);int room=0;for(ItemStack old:p.inventory.mainInventory){if(old.isEmpty())room+=s.getMaxStackSize();else if(ItemStack.areItemsEqual(old,s)&&ItemStack.areItemStackTagsEqual(old,s))room+=Math.max(0,Math.min(old.getMaxStackSize(),p.inventory.getInventoryStackLimit())-old.getCount());if(room>=s.getCount())break;}if(room<s.getCount()||!l.debit(p.getUniqueID(),e.priceMilli))return false;
        // Deliver exactly once: remainder is dropped at buyer's feet if inventory is full.
        p.inventory.addItemStackToInventory(s);if(!s.isEmpty())p.dropItem(s,false);p.inventory.markDirty();return true;
    }
}
