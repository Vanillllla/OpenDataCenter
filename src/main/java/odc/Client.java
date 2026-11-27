package odc;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.Locale;

@Mod.EventBusSubscriber(modid=ODC.ID,value=Side.CLIENT)
public final class Client {
    @SubscribeEvent public static void models(ModelRegistryEvent e){for(Block b:Hardware.BLOCKS)model(Item.getItemFromBlock(b));for(Item i:Hardware.ITEMS)model(i);}
    private static void model(Item i){ModelLoader.setCustomModelResourceLocation(i,0,new ModelResourceLocation(i.getRegistryName(),"inventory"));}
    static String amount(int milli){return String.format(Locale.ROOT,"%.3f",milli/1000.0);}
    static String tr(String key,Object... args){return I18n.format("gui.odc."+key,args);}
    static String task(int n){return tr("task."+Math.max(0,Math.min(3,n)));}
    public static final class Screen extends GuiContainer {
        final Menus.Menu menu;
        final int TEXT=0xffe2e5e5,MUTED=0xffa5adb0,GREEN=0xff8cdda4,AMBER=0xffe5bf76,RED=0xffeb9888;
        Screen(Menus.Menu m){super(m);menu=m;xSize=Menus.W;ySize=Menus.H;}
        @Override public void initGui(){super.initGui();
            if(menu.type==Menus.ASSEMBLY)button(0,139,81,65,20,tr("assemble"));
            if(menu.type==Menus.SERVER)button(21,173,7,64,16,tr("back"));
            if(menu.type==Menus.RACK){button(9,161,26,77,18,tr("link"));for(int i=0;i<4;i++){button(i+1,53,47+i*23,67,20,task(0));button(30+i,122,47+i*23,43,20,tr("edit"));}}
            if(menu.type==Menus.CONTROL){button(20,173,7,64,16,tr("market"));for(int i=0;i<3;i++)button(i+1,181,76+i*23,56,20,tr("start"));}
            if(menu.type==Menus.MARKET){button(21,173,7,64,16,tr("back"));for(int i=0;i<4;i++)button(10+i,10+i*60,109,48,19,tr("buy"));}
        }
        void button(int id,int x,int y,int w,int h,String label){buttonList.add(new GuiButton(id,guiLeft+x,guiTop+y,w,h,label));}
        @Override protected void actionPerformed(GuiButton b){if(b.enabled&&mc.playerController!=null)mc.playerController.sendEnchantPacket(menu.windowId,b.id);}
        @Override public void updateScreen(){super.updateScreen();int[] d=menu.data;
            for(GuiButton b:buttonList){
                if(menu.type==Menus.RACK&&b.id>=1&&b.id<=4){int i=b.id-1;b.enabled=d[7+i]==i+1&&d[32+i]==0;b.displayString=task(d[11+i]);}
                if(menu.type==Menus.RACK&&b.id>=30&&b.id<34){int i=b.id-30;b.enabled=d[7+i]==i+1&&d[32+i]==0;}
                if(menu.type==Menus.ASSEMBLY&&b.id==0){int u=menu.chassis(),state=ServerSpec.state(u,Hardware.tiers(menu.parts()));b.enabled=u>0&&state!=ServerSpec.INVALID&&state!=ServerSpec.CAPACITY&&!menu.getSlot(menu.machineSlots-1).getHasStack();}
                if(menu.type==Menus.CONTROL&&b.id>=1&&b.id<=3){b.enabled=d[1]==1;b.displayString=tr((d[8]&(1<<(b.id-1)))!=0?"stop":"start");}
                if(menu.type==Menus.MARKET&&b.id>=10&&b.id<=13){int p=d[3+b.id-10];b.enabled=d[1]==1&&p>0&&d[0]>=p;b.displayString=p>0?amount(p):"--";}
            }
        }
        void panel(int x,int y,int w,int h,int color){Gui.drawRect(guiLeft+x,guiTop+y,guiLeft+x+w,guiTop+y+h,color);}
        void slot(int x,int y){panel(x-1,y-1,18,18,0xff687174);panel(x,y,16,16,0xff171c1e);}
        @Override protected void drawGuiContainerBackgroundLayer(float partialTicks,int mx,int my){
            panel(0,0,xSize,ySize,0xff111517);panel(1,1,xSize-2,ySize-2,0xff51575a);panel(3,3,xSize-6,ySize-6,0xff2d3335);
            panel(4,4,xSize-8,21,0xff202527);panel(4,24,xSize-8,1,0xff6d787b);panel(8,27,xSize-16,122,0xff22292b);panel(8,151,xSize-16,77,0xff303638);
            for(net.minecraft.inventory.Slot s:menu.inventorySlots)slot(s.xPos,s.yPos);
            if(menu.type==Menus.RACK){for(int i=0;i<4;i++){int start=menu.data[7+i]-1;if(start>=0&&start!=i)panel(14,49+i*23,16,16,0xff51595b);}}
            if(menu.type==Menus.SERVER||menu.type==Menus.ASSEMBLY){for(int i=0;i<ServerSpec.COUNT;i++){int index=i+(menu.type==Menus.ASSEMBLY?1:0);if(!menu.fits(i)&&!menu.getSlot(index).getHasStack()){int x=Menus.Menu.partX(i),y=Menus.Menu.partY(i);panel(x,y,16,16,0xff45494a);panel(x+3,y+7,10,2,0xff666d70);}}}
            if(menu.type==Menus.CONTROL){int[] d=menu.data;int cap=Math.min(d[5],d[6]);panel(12,65,224,5,0xff121719);if(cap>0)panel(12,65,(int)Math.min(224,(long)224*d[7]/cap),5,0xff67b68a);for(int i=0;i<3;i++)panel(10,75+i*23,228,21,i%2==0?0xff30383a:0xff283032);}
        }
        void label(String s,int x,int y,int col){fontRenderer.drawString(s,x,y,col);}
        void clipped(String s,int x,int y,int width,int col){label(fontRenderer.trimStringToWidth(s,width),x,y,col);}
        @Override protected void drawGuiContainerForegroundLayer(int mx,int my){
            String[] names={"rack","assembly","control","market","switch","station","hardware"};label(tr(names[menu.type]),10,11,TEXT);int[] d=menu.data;
            if(menu.type==Menus.RACK){
                String linked=d[3]==0?tr("unlinked"):d[3]==1?tr("link_lost"):tr("linked",d[4],d[5],d[6]);clipped(linked,12,31,145,d[3]==2?GREEN:MUTED);
                for(int i=0;i<4;i++){int start=d[7+i]-1;label((i+1)+"U",34,54+i*23,MUTED);if(start==i){int status=d[15+i];if(status==ComputeEngine.IDLE&&Hardware.state(menu.getSlot(i).getStack())!=ServerSpec.READY)status=ComputeEngine.INCOMPLETE;clipped(tr("status."+status),169,49+i*23,68,status==ComputeEngine.RUNNING?GREEN:status==ComputeEngine.THROTTLED?AMBER:MUTED);clipped(d[19+i]+"/"+d[23+i]+" Mb/s",169,59+i*23,68,TEXT);}else if(start>=0)label(tr("occupied",start+1),169,54+i*23,MUTED);}
                clipped(d[0]+" FE | "+d[1]+" FE/t | "+d[2]+" Mb/s",12,139,224,TEXT);
            }else if(menu.type==Menus.ASSEMBLY||menu.type==Menus.SERVER){
                boolean assembly=menu.type==Menus.ASSEMBLY;
                if(assembly)clipped(tr("chassis"),10,31,37,MUTED);else label(menu.chassis()+"U",14,47,TEXT);
                clipped(tr("board"),46,31,32,MUTED);label("CPU",78,31,MUTED);label("RAM",110,31,MUTED);clipped(tr("drives"),187,31,49,MUTED);
                label(tr("gpu_slots"),12,71,MUTED);if(assembly)clipped(tr("output"),210,71,32,MUTED);
                int[] p=Hardware.tiers(menu.parts());int state=ServerSpec.state(menu.chassis(),p);
                clipped(menu.chassis()==0?tr("choose_chassis"):tr("boot."+state),12,108,224,state==ServerSpec.READY?GREEN:AMBER);
                int vram=4*ServerSpec.gpuCount(p,1)+16*ServerSpec.gpuCount(p,2);
                clipped(ServerSpec.ramGB(p)+" GB RAM | "+ServerSpec.diskGB(p)+" GB Disk | "+vram+" GB VRAM",12,120,224,TEXT);
                clipped(tr(assembly?"assemble_help":"maintenance_help"),12,134,224,MUTED);
            }
            else if(menu.type==Menus.CONTROL){
                label(d[1]==1?tr("online"):tr("offline"),12,30,d[1]==1?GREEN:RED);clipped(tr("wallet",amount(d[0])),116,30,121,GREEN);
                clipped(tr("cluster",d[3],d[4])+" | "+d[27]+" FE/t",12,42,224,TEXT);
                clipped(tr("network",d[5],d[6],d[7]),12,54,224,MUTED);
                for(int i=0;i<3;i++){int base=9+i*6;int y=76+i*23;label(task(i+1),13,y,TEXT);label(d[base+1]+"/"+d[base],67,y,MUTED);String units=i==0?" H/s":i==1?" CU/s":" GB-eq";clipped(d[base+2]+units,100,y,78,GREEN);clipped(d[base+3]+"/"+d[base+5]+" Mb/s | "+amount(d[base+4])+" NTC/s",13,y+10,163,MUTED);}
                label(tr("assign_hint"),12,144,MUTED);
            }else if(menu.type==Menus.MARKET){label(tr("wallet",amount(d[0])),12,34,GREEN);label(d[1]==1?tr("market_hint"):tr("offline"),12,49,d[1]==1?MUTED:RED);label(tr("market_price"),12,64,TEXT);}
            else {label(d[0]==1?tr("online"):tr("offline"),12,34,d[0]==1?GREEN:RED);label(tr("capacity",d[0]==1?d[1]:0,d[1]),12,51,TEXT);label(d[2]+" FE | "+d[3]+" FE/t",12,68,MUTED);label(tr(menu.type==Menus.SWITCH?"switch_hint":"station_hint"),12,91,TEXT);label(tr("network_hint"),12,105,MUTED);}
        }
        @Override public void drawScreen(int x,int y,float f){drawDefaultBackground();super.drawScreen(x,y,f);renderHoveredToolTip(x,y);}
    }
}
