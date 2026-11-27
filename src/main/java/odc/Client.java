package odc;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.Locale;

@Mod.EventBusSubscriber(modid=ODC.ID,value=Side.CLIENT)
public final class Client {
    @SubscribeEvent public static void models(ModelRegistryEvent e){for(Block b:Hardware.BLOCKS)model(Item.getItemFromBlock(b));for(Item i:Hardware.ITEMS)model(i);ClientRegistry.bindTileEntitySpecialRenderer(Tiles.Rack.class,new RackRenderer());}
    @SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre e){for(String name:RackRenderer.SPRITES)e.getMap().registerSprite(new net.minecraft.util.ResourceLocation(ODC.ID,"blocks/"+name));}
    private static void model(Item i){ModelLoader.setCustomModelResourceLocation(i,0,new ModelResourceLocation(i.getRegistryName(),"inventory"));}
    static String amount(int milli){return String.format(Locale.ROOT,"%.3f",milli/1000.0);}
    static String tr(String key,Object... args){return I18n.format("gui.odc."+key,args);}
    public static final class Screen extends GuiContainer {
        final Menus.Menu menu;final int TEXT=0xffe2e5e5,MUTED=0xffa5adb0,GREEN=0xff8cdda4,AMBER=0xffe5bf76,RED=0xffeb9888;
        Screen(Menus.Menu m){super(m);menu=m;xSize=Menus.W;ySize=Menus.H;}
        @Override public void initGui(){super.initGui();int type=menu.type;
            if(type==Menus.RACK){for(int i=0;i<9;i++){int x=Menus.Menu.rackX(i),y=Menus.Menu.rackY(i);button(10+i,x+49,y-1,32,18,"On");button(30+i,x+83,y-1,43,18,"Link");}for(int i=0;i<8;i++)button(50+i,12+i*34,138,32,17,"P"+(i+1));}
            if(type==Menus.CONTROL){button(102,184,6,48,18,tr("refresh"));button(2,234,6,57,18,tr("market"));button(1,10,128,56,18,tr("start_all"));button(3,68,128,56,18,tr("stop_all"));button(100,192,128,23,18,"<");button(101,268,128,23,18,">");for(int i=0;i<5;i++){button(20+i,205,47+i*16,42,15,"Mine");button(30+i,249,47+i*16,41,15,"On");}}
            if(type==Menus.MARKET){button(2,234,6,57,18,tr("back"));for(int i=0;i<4;i++)button(10+i,10+i*68,99,64,18,tr("buy"));}
        }
        void button(int id,int x,int y,int w,int h,String label){buttonList.add(new GuiButton(id,guiLeft+x,guiTop+y,w,h,label));}
        @Override protected void actionPerformed(GuiButton b){if(b.enabled&&mc.playerController!=null)mc.playerController.sendEnchantPacket(menu.windowId,b.id);}
        @Override public void updateScreen(){super.updateScreen();int[] d=menu.data;for(GuiButton b:buttonList){int id=b.id;
            if(menu.type==Menus.RACK){if(id>=50){int i=id-50;b.visible=i<Hardware.ports(d[3]);b.enabled=b.visible;b.displayString=(d[5]==i?">":"")+(i+1)+":"+(d[40+i]>0?d[40+i]:"-");}else {int i=id>=30?id-30:id-10;ItemStack s=menu.getSlot(i).getStack();b.visible=i<d[0]&&Hardware.units(s)>0;b.enabled=b.visible;if(id>=30){b.displayString=d[20+i]>0?"P"+d[20+i]+" -":"+ P"+(d[5]+1);b.enabled=b.visible&&Hardware.nic(s)>0&&(d[20+i]>0||d[3]>0&&d[40+d[5]]==0);}else{b.displayString=(d[6]&(1<<i))!=0?tr("off_short"):tr("on_short");b.enabled=b.visible&&((d[6]&(1<<i))!=0||Hardware.state(s)==ServerSpec.READY);}}}
            if(menu.type==Menus.CONTROL){if(id>=20&&id<35){int i=id>=30?id-30:id-20,k=20+i*8;b.visible=d[k]==1;b.enabled=b.visible&&d[1]==1;b.displayString=id>=30?((d[k+5]&1)!=0?tr("off_short"):tr("on_short")):((d[k+5]&2)!=0?tr("stop_short"):tr("mine_short"));}else if(id==100)b.enabled=d[10]>0;else if(id==101)b.enabled=d[10]+1<d[11];else if(id==1||id==3)b.enabled=d[1]==1;}
            if(menu.type==Menus.MARKET&&id>=10&&id<=13){int price=d[3+id-10];b.enabled=d[1]==1&&price>0&&d[0]>=price;b.displayString=price>0?amount(price):"--";}}
        }
        void panel(int x,int y,int w,int h,int c){Gui.drawRect(guiLeft+x,guiTop+y,guiLeft+x+w,guiTop+y+h,c);}
        void slot(int x,int y){panel(x-1,y-1,18,18,0xff687174);panel(x,y,16,16,0xff171c1e);}
        @Override protected void drawGuiContainerBackgroundLayer(float f,int mx,int my){panel(0,0,xSize,ySize,0xff111719);panel(1,1,xSize-2,ySize-2,0xff717a7e);panel(3,3,xSize-6,ySize-6,0xff2c353a);panel(4,4,xSize-8,21,0xff1d282e);panel(5,27,xSize-10,129,0xff222c31);panel(5,158,xSize-10,78,0xff354047);
            for(net.minecraft.inventory.Slot s:menu.inventorySlots)if(s.isEnabled())slot(s.xPos,s.yPos);
            if(menu.type==Menus.RACK){for(int i=0;i<9;i++)if(i<menu.data[0]){int start=menu.data[10+i]-1;if(start>=0&&start!=i)panel(Menus.Menu.rackX(i),Menus.Menu.rackY(i),16,16,0xff526067);}}
            if(menu.type==Menus.WORKBENCH)for(int i=0;i<ServerSpec.COUNT;i++)if(!menu.fits(i)){int x=Menus.Menu.partX(i),y=Menus.Menu.partY(i);panel(x,y,16,16,0xff454d50);panel(x+3,y+7,10,2,0xff6b7478);}
            if(menu.type==Menus.CONTROL)for(int i=0;i<5;i++)panel(9,47+i*16,282,15,i%2==0?0xff303d44:0xff28353c);
        }
        void label(String s,int x,int y,int c){fontRenderer.drawString(s,x,y,c);}
        void clipped(String s,int x,int y,int width,int c){label(fontRenderer.trimStringToWidth(s,width),x,y,c);}
        @Override protected void drawGuiContainerForegroundLayer(int mx,int my){int[] d=menu.data;String name=menu.type==Menus.RACK?tr("rack")+" "+d[0]+"U":menu.type==Menus.WORKBENCH?tr("assembly"):menu.type==Menus.CONTROL?tr("control"):menu.type==Menus.MARKET?tr("market"):tr("station");label(name,10,11,TEXT);
            if(menu.type==Menus.RACK){label(d[1]+" FE | "+d[2]+" FE/t | "+tr("switch")+": "+(d[4]==1?"ON":"OFF"),12,27,MUTED);for(int i=0;i<d[0]&&i<9;i++){int x=Menus.Menu.rackX(i),y=Menus.Menu.rackY(i),start=d[10+i]-1;ItemStack s=menu.getSlot(i).getStack();label((i+1)+"U",x+19,y+3,MUTED);if(Hardware.units(s)>0)label((d[7]&(1<<i))!=0?"●":"·",x+39,y+3,(d[8]&(1<<i))!=0?GREEN:MUTED);else if(Hardware.switchTier(s)>0)clipped("SW T"+Hardware.switchTier(s),x+43,y+3,76,d[4]==1?GREEN:MUTED);else if(start>=0)clipped("↑ "+(start+1)+"U",x+45,y+3,70,MUTED);}if(d[3]==0)label(tr("no_switch"),12,141,AMBER);else clipped(tr("port_hint"),156,122,133,MUTED);}
            else if(menu.type==Menus.WORKBENCH){clipped(tr("chassis"),10,27,33,MUTED);clipped(tr("board"),44,27,35,MUTED);label("CPU",82,27,MUTED);label("NIC",118,27,MUTED);label("NVMe",183,27,MUTED);label("RAM",12,59,MUTED);label("GPU",183,59,MUTED);label("SATA",12,94,MUTED);label("PSU",183,94,MUTED);int[] p=Hardware.spec(menu.getSlot(0).getStack());int state=ServerSpec.state(menu.chassis(),p);clipped(menu.chassis()==0?tr("choose_chassis"):tr("boot."+state),12,125,276,state==0?GREEN:AMBER);clipped(ServerSpec.ramGB(p)+" GB RAM | "+ServerSpec.diskGB(p)+" GB | "+ServerSpec.psu(p)+" FE/t PSU | "+ServerSpec.nic(p)+" Mb/s",12,138,276,TEXT);}
            else if(menu.type==Menus.CONTROL){clipped((d[1]==1?"ONLINE":"OFFLINE")+" | "+amount(d[0])+" NTC | "+d[8]+" H/s | "+d[2]+" FE/t",12,27,276,d[1]==1?GREEN:RED);clipped("WAN "+d[5]+" | LAN "+d[6]+" | "+d[7]+" Mb/s | "+d[4]+" SRV",12,37,276,MUTED);for(int i=0;i<5;i++){int k=20+i*8;if(d[k]==0)continue;clipped(d[k+1]+","+d[k+2]+","+d[k+3]+" / U"+d[k+4],12,51+i*16,112,TEXT);clipped(tr("status."+d[k+6])+" "+d[k+7],125,51+i*16,77,MUTED);}label((d[10]+1)+"/"+Math.max(1,d[11]),219,133,TEXT);clipped(amount(d[9])+" NTC/s",130,133,61,GREEN);}
            else if(menu.type==Menus.MARKET){label(tr("wallet",amount(d[0])),12,35,GREEN);label(d[1]==1?tr("market_hint"):tr("offline"),12,49,MUTED);}
            else {label(d[0]==1?tr("online"):tr("offline"),12,35,d[0]==1?GREEN:RED);label(d[1]+" Mb/s WAN | "+d[3]+" FE/t",12,51,TEXT);label(d[2]+" FE",12,66,MUTED);}
        }
        @Override public void drawScreen(int x,int y,float f){drawDefaultBackground();super.drawScreen(x,y,f);renderHoveredToolTip(x,y);}
    }
}
