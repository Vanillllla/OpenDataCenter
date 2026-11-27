package odc;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import java.util.Locale;

/** Tabbed DataOS and individual hardware screens. No editable hardware slots here. */
final class PanelScreen extends GuiContainer {
    final PanelMenu menu;final int FG=0xff20242b,MUTED=0xff505965,BLUE=0xff174b91,GREEN=0xff216329,RED=0xffa02624;int renderedTab=-1,nameId=Integer.MIN_VALUE;net.minecraft.client.gui.GuiTextField nameField;
    static final String[] TABS={"Overview","Servers","Tasks","Resources","Network","Power","Wallet / shop","Log","Mining"};
    PanelScreen(PanelMenu m){super(m);menu=m;xSize=320;ySize=236;}
    String tr(String en,String ru){return mc.getLanguageManager().getCurrentLanguage().getLanguageCode().equals("ru_ru")?ru:en;}
    void button(int id,int x,int y,int w,int h,String label){buttonList.add(new PanelButton(id,guiLeft+x,guiTop+y,w,h,label));}
    private final class PanelButton extends GuiButton {
        final int px,py,pw,ph;
        PanelButton(int id,int x,int y,int w,int h,String s){super(id,x,y,w,h,s);px=x;py=y;pw=w;ph=h;}
        @Override public void drawButton(net.minecraft.client.Minecraft minecraft,int mx,int my,float partial){
            if(!visible)return;
            int[] d=menu.data;
            boolean over=enabled&&mx>=px&&mx<px+pw&&my>=py&&my<py+ph;
            boolean tab=menu.type==0&&id>=1&&id<=9;
            boolean chosen=tab&&d[0]==id-1||menu.type==2&&id>=10&&id<18&&d[11]==id-10;
            // Pixel-aligned Luna-inspired bevel; selected tabs retain contrast when disabled.
            int border=chosen?0xff255a9f:over?0xffa96b24:enabled?0xff6e8196:0xffaaa99f;
            int top=chosen?0xffe8f4ff:over?0xfffffbeb:enabled?0xfffefefe:0xffeae8df;
            int bottom=chosen?0xffa9c9f1:over?0xffe9d9a9:enabled?0xffd9e0e9:0xffdedbd1;
            Gui.drawRect(px,py,px+pw,py+ph,border);
            drawGradientRect(px+1,py+1,px+pw-1,py+ph-1,top,bottom);
            Gui.drawRect(px+1,py+1,px+pw-1,py+2,0xfffefefe);
            Gui.drawRect(px+1,py+2,px+2,py+ph-2,chosen?0xffd4e8ff:0xfff6f6f1);
            Gui.drawRect(px+2,py+ph-2,px+pw-1,py+ph-1,chosen?0xff79a5da:over?0xffd7a75c:0xffb8c1ce);
            if(tab&&chosen)Gui.drawRect(px+2,py+ph-3,px+pw-2,py+ph-1,0xff4486c8);
            int label=chosen?0xff143e74:enabled?FG:0xff77776f;
            int cx=px+pw/2;
            if(menu.type==2&&id>=10&&id<18){
                int i=id-10;
                Gui.drawRect(cx-8,py+3,cx+8,py+11,0xff707b87);
                Gui.drawRect(cx-7,py+4,cx+7,py+10,0xff252b32);
                for(int j=0;j<4;j++)Gui.drawRect(cx-5+j*3,py+4,cx-4+j*3,py+7,0xffc3ab70);
                Gui.drawRect(cx+5,py+2,cx+8,py+4,d[20+i]>0?(d[12]==1?0xff4cae43:0xff478dce):0xff858b88);
                centered(displayString,cx,py+12,pw-4,label);return;
            }
            if(menu.type==2&&id>=20&&id<29){
                Gui.drawRect(px+3,py+3,px+pw-3,py+9,0xff687681);
                Gui.drawRect(px+5,py+4,px+pw-8,py+8,0xff323d46);
                Gui.drawRect(px+pw-6,py+5,px+pw-4,py+7,d[50+id-20]>0?0xff4389cc:0xffa2a9a1);
                centered(displayString,cx,py+10,pw-4,label);return;
            }
            centered(displayString,cx,py+(ph-8)/2,pw-6,label);
        }
        private void centered(String label,int cx,int y,int max,int color){
            String s=fontRenderer.trimStringToWidth(label,Math.max(0,max));
            fontRenderer.drawString(s,cx-fontRenderer.getStringWidth(s)/2,y,color);
        }
    }
    void rect(int x,int y,int w,int h,int c){Gui.drawRect(guiLeft+x,guiTop+y,guiLeft+x+w,guiTop+y+h,c);}
    void text(String s,int x,int y,int c){fontRenderer.drawString(s,x,y,c);}
    void clip(String s,int x,int y,int w,int c){text(fontRenderer.trimStringToWidth(s,w),x,y,c);}
    void bar(int x,int y,int w,int value,int max){
        inset(x,y,w,8,0xfffdfdfb);
        int fill=(int)((w-2)*Math.min(1,Math.max(0,value)/(double)Math.max(1,max)));
        if(fill>0)gradient(x+1,y+1,fill,6,0xff85beef,0xff3976bc);
    }
    void gradient(int x,int y,int w,int h,int from,int to){drawGradientRect(guiLeft+x,guiTop+y,guiLeft+x+w,guiTop+y+h,from,to);}
    void inset(int x,int y,int w,int h,int fill){
        rect(x,y,w,h,0xff8994a0);rect(x+1,y+1,w-2,h-2,fill);
        rect(x+1,y+h-1,w-1,1,0xfffdfdfb);rect(x+w-1,y+1,1,h-1,0xfffdfdfb);
    }
    void title(String value){text(value,10,9,0xffffffff);}
    void configureField(){nameField.setEnableBackgroundDrawing(false);nameField.setTextColor(FG);}
    boolean fieldVisible(){return nameField!=null&&(menu.type==1||menu.data[50]>0);}

    @Override public void initGui(){super.initGui();build();}
    void build(){buttonList.clear();nameField=null;nameId=Integer.MIN_VALUE;int[] d=menu.data;renderedTab=d[0];if(menu.type==1){nameField=new net.minecraft.client.gui.GuiTextField(0,fontRenderer,guiLeft+12,guiTop+29,117,10);nameField.setMaxStringLength(32);configureField();button(92,136,24,71,19,tr("Rename","Имя"));button(1,210,24,97,19,tr("Power toggle","Питание"));button(4,10,208,185,18,tr("Announce maintenance","Объявить технические работы"));return;}if(menu.type==2){for(int i=0;i<8;i++)button(10+i,15+i*37,46,32,22,"P"+(i+1));for(int i=0;i<9;i++)button(20+i,10+i*33,155,30,20,"U"+(i+1));button(30,104,193,112,20,tr("Disconnect","Отключить"));return;}
    }
    @Override protected void actionPerformed(GuiButton b){if(b.enabled&&b.id==92&&nameField!=null){RackInteraction.channel.sendToServer(new RackInteraction.Rename(menu.windowId,menu.type==1?menu.data[1]:menu.data[50],nameField.getText()));nameField.setFocused(false);return;}if(b.enabled&&mc.playerController!=null)mc.playerController.sendEnchantPacket(menu.windowId,b.id);}
    @Override public void updateScreen(){super.updateScreen();int[] d=menu.data;if(menu.type==0&&renderedTab!=d[0])build();if(nameField!=null){int id=menu.type==1?d[10]>0?d[1]:-1:d[50];if(id!=nameId&&!nameField.isFocused()){nameId=id;nameField.setText(PanelMenu.getText(d,menu.type==1?100:300,20));}nameField.updateCursorCounter();}for(GuiButton b:buttonList){int id=b.id;b.enabled=true;b.visible=true;if(menu.type==1){if(id==1)b.displayString=d[2]==1?tr("Turn OFF","Выключить"):tr("Turn ON","Включить");if(id==1 && d[2]==1)b.enabled=d[12]<=0;
            if(id==4){b.visible=d[12]>=0;b.enabled=d[12]==Integer.MAX_VALUE;
                if(!b.enabled)b.displayString=d[12]==0?tr("Ready to stop","Можно выключать"):tr("Maintenance in ","До техработ ")+d[12]+" s";}
            if(id==92)b.enabled=d[10]>0;continue;}if(menu.type==2){if(id>=10&&id<18){int i=id-10;b.visible=i<Hardware.ports(d[10]);b.enabled=b.visible;b.displayString=(i==d[11]?">":"")+"P"+(i+1);}else if(id>=20&&id<29){int i=id-20;b.visible=d[40+i]>0;b.enabled=b.visible&&d[50+i]==0&&d[20+d[11]]==0;b.displayString="U"+(i+1);}else if(id==30)b.enabled=d[20+d[11]]>0;continue;}
        }}
    static String amount(long v){return String.format(Locale.ROOT,"%.3f",v/1000.0);}
    @Override protected void drawGuiContainerBackgroundLayer(float f,int mx,int my){
        // Shared light shell, reminiscent of the Minecraft inventory and XP Luna.
        rect(0,0,xSize,ySize,0xff35485e);
        rect(1,1,xSize-2,ySize-2,0xff7e9fc3);
        rect(3,3,xSize-6,ySize-6,0xffece9d8);
        rect(3,3,xSize-6,1,0xfffdfcf6);
        gradient(4,4,xSize-8,16,0xff387ac8,0xff2156a0);
        rect(4,4,xSize-8,1,0xff8bb6eb);
        if(menu.type==1){
            // Rename controls sit below their own title bar.
            inset(8,24,125,19,0xfffefefd);
            inset(7,47,306,129,0xfff9f8f2);
            rect(10,126,300,1,0xffc6ccd3);
        }else if(menu.type==2){
            inset(8,42,304,137,0xfff9f8f2);
            int[] d=menu.data;
            for(int i=0;i<8;i++){
                int b=d[20+i]-1;if(b<0||b>=9)continue;
                int x=31+i*37,xx=25+b*33,y=80+i*7;
                int color=d[11]==i?0xff205bb1:0xff5a9ad0;
                rect(x,68,2,y-68,color);rect(Math.min(x,xx),y,Math.abs(x-xx)+2,2,color);rect(xx,y,2,155-y,color);
            }
        }
    }
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){int[] d=menu.data;if(menu.type==1){title(tr("Server details","Сведения о сервере"));text("ID "+d[11]+" | "+d[10]+"U / U"+(d[1]+1)+" | "+status(d[4])+" | "+d[5]+" FE/t",10,51,BLUE);int[] p=java.util.Arrays.copyOfRange(d,20,20+ServerSpec.COUNT);text(Part.at(p[0]).name+" / "+Part.at(p[1]).name,10,64,FG);text("RAM "+ServerSpec.ramGB(p)+" GB / disk "+ServerSpec.diskGB(p)+" GB",10,80,FG);text("PSU "+ServerSpec.psu(p)+" W / NIC "+ServerSpec.nic(p)+" Mb/s",10,96,FG);text("GPU: "+ServerSpec.gpuTotal(p)+" / VRAM "+ServerSpec.vram(p)+" GB",10,112,FG);text("CPU cores "+d[6]+" / GPU mask "+Integer.toBinaryString(d[7]),10,135,MUTED);text("RAM used "+d[8]+" GB / network "+d[9]+" Mb/s",10,151,MUTED);text(tr("Components are read-only. Use workbench.","Компоненты меняются только на рабочем столе."),10,189,MUTED);return;}
        if(menu.type==2){title("Switch T"+d[10]+" / "+(d[12]==1?"ONLINE":"OFFLINE"));text(tr("Select port, then server","Выберите порт, затем сервер"),10,31,MUTED);text(tr("Rack units","Юниты стойки"),10,143,MUTED);return;}
    }
    static String status(int n){return n==11?"PSU OVERLOAD":n==6?"NO POWER":n==7?"RUNNING":n==8?"PARTIAL":n==5?"NO NET":n==10?"INCOMPLETE":n==1?"NO CTRL":"IDLE";}
    @Override public void drawScreen(int x,int y,float f){drawDefaultBackground();super.drawScreen(x,y,f);if(fieldVisible())nameField.drawTextBox();}
    @Override protected void mouseClicked(int x,int y,int b)throws java.io.IOException{if(fieldVisible())nameField.mouseClicked(x,y,b);super.mouseClicked(x,y,b);}
    @Override protected void keyTyped(char c,int k)throws java.io.IOException{if(fieldVisible()&&nameField.isFocused()&&nameField.textboxKeyTyped(c,k))return;super.keyTyped(c,k);}
}
