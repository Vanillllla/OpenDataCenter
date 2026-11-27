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
            if(menu.type==0&&d[0]==2&&id>=20&&id<24){int k=80+(id-20)*24;chosen=d[k]>0&&d[k+1]==d[13];}
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
    void build(){buttonList.clear();nameField=null;nameId=Integer.MIN_VALUE;int[] d=menu.data;renderedTab=d[0];if(menu.type==1){nameField=new net.minecraft.client.gui.GuiTextField(0,fontRenderer,guiLeft+12,guiTop+29,117,10);nameField.setMaxStringLength(32);configureField();button(92,136,24,71,19,tr("Rename","Имя"));button(1,210,24,97,19,tr("Power toggle","Питание"));return;}if(menu.type==2){for(int i=0;i<8;i++)button(10+i,15+i*37,46,32,22,"P"+(i+1));for(int i=0;i<9;i++)button(20+i,10+i*33,155,30,20,"U"+(i+1));button(30,104,193,112,20,tr("Disconnect","Отключить"));return;}
        String[] tabs=tr("en","ru").equals("ru")?new String[]{"Обзор","Серверы","Задачи","Ресурсы","Сеть","Питание","Кошелёк","Журнал","Майнинг"}:TABS;
        for(int i=0;i<9;i++)button(i+1,7+(i%5)*62,23+(i/5)*20,60,19,tabs[i]);
        int tab=d[0];if(tab==1||tab==2){button(90,9,212,26,18,"<");button(91,285,212,26,18,">");}
        if(tab==1){button(11,8,65,66,17,"Group");button(12,76,65,65,17,"All ON");button(13,143,65,65,17,"All OFF");button(14,210,65,101,17,"Assign group");for(int i=0;i<3;i++){int y=84+i*41,b=50+i*10;button(b,8,y+12,36,14,"ON");button(b+1,46,y+12,32,14,"G");button(b+2,80,y+12,46,14,"Link");button(b+3,128,y+12,52,14,"CPU");button(b+8,182,y+12,62,14,"RAM");button(b+9,246,y+12,66,14,"NET");for(int k=0;k<4;k++)button(b+4+k,177+k*34,y+27,32,13,"G"+(k+1));}}
        if(tab==2){button(11,8,65,203,18,"Type");button(12,215,65,97,18,tr("Create","Создать"));for(int i=0;i<4;i++)button(20+i,8,86+i*24,304,22,"Task");button(4,8,187,304,19,tr("Edit selected resources","Настроить выбранную задачу"));}
        if(tab==3){nameField=new net.minecraft.client.gui.GuiTextField(0,fontRenderer,guiLeft+12,guiTop+69,193,10);nameField.setMaxStringLength(32);configureField();button(92,214,65,98,17,tr("Rename","Имя"));button(30,8,84,74,18,"Start");button(31,84,84,74,18,"Auto");button(32,160,84,74,18,"Flexible");button(33,236,84,76,18,"Priority");for(int i=0;i<4;i++){int y=104+i*17;button(34+i*2,224,y,40,16,"-");button(35+i*2,272,y,40,16,"+");}button(44,224,172,40,16,"-");button(45,272,172,40,16,"+");button(42,8,196,92,18,"Group");button(43,220,196,92,18,tr("Delete","Удалить"));}
        if(tab==6){button(11,8,65,145,18,"Wallet");button(12,155,65,157,18,"Coin");button(13,8,121,98,18,"Corp -> me");button(14,111,121,98,18,"Me -> corp");button(15,214,121,98,18,"Exchange 1");for(int i=0;i<4;i++)button(20+i,8+(i%2)*153,164+(i/2)*27,150,24,"Buy");}}
    @Override protected void actionPerformed(GuiButton b){if(b.enabled&&b.id==92&&nameField!=null){RackInteraction.channel.sendToServer(new RackInteraction.Rename(menu.windowId,menu.type==1?menu.data[1]:menu.data[50],nameField.getText()));nameField.setFocused(false);return;}if(b.enabled&&mc.playerController!=null)mc.playerController.sendEnchantPacket(menu.windowId,b.id);}
    @Override public void updateScreen(){super.updateScreen();int[] d=menu.data;if(menu.type==0&&renderedTab!=d[0])build();if(nameField!=null){int id=menu.type==1?d[10]>0?d[1]:-1:d[50];if(id!=nameId&&!nameField.isFocused()){nameId=id;nameField.setText(PanelMenu.getText(d,menu.type==1?100:300,20));}nameField.updateCursorCounter();}for(GuiButton b:buttonList){int id=b.id;b.enabled=true;b.visible=true;if(menu.type==1){if(id==1)b.displayString=d[2]==1?tr("Turn OFF","Выключить"):tr("Turn ON","Включить");if(id==92)b.enabled=d[10]>0;continue;}if(menu.type==2){if(id>=10&&id<18){int i=id-10;b.visible=i<Hardware.ports(d[10]);b.enabled=b.visible;b.displayString=(i==d[11]?">":"")+"P"+(i+1);}else if(id>=20&&id<29){int i=id-20;b.visible=d[40+i]>0;b.enabled=b.visible&&d[50+i]==0&&d[20+d[11]]==0;b.displayString="U"+(i+1);}else if(id==30)b.enabled=d[20+d[11]]>0;continue;}
            int tab=d[0];if(id<=9){b.enabled=id!=tab+1;continue;}if(id==90){b.enabled=d[10]>0;continue;}if(id==91){b.enabled=d[10]+1<d[11];continue;}b.enabled=d[1]==1;
            if(tab==1){if(id==11)b.displayString="G: "+(d[14]==0?"all":d[14]);if(id==14)b.enabled&=d[50]>0;if(id>=50&&id<90){int row=(id-50)/10,op=(id-50)%10,k=80+row*24;b.visible=d[k]>0;b.enabled&=b.visible;if(op==0)b.displayString=d[k+3]==1?"OFF":"ON";if(op==1)b.displayString="G"+d[k+10];if(op==2){b.displayString=d[k+15]==1?"Unlink":"Link";b.enabled&=d[50]>0;}if(op==3){b.displayString=d[k+16]+"%";b.enabled&=d[k+15]>0;}if(op==8||op==9){int v=d[k+(op==8?21:22)];b.displayString=(op==8?"R ":"N ")+(v==Integer.MAX_VALUE?"all":v);b.enabled&=d[k+15]>0;}if(op>=4&&op<=7){b.displayString=((d[k+17]&(1<<(op-4)))!=0?"+":"-")+"G"+(op-3);b.enabled&=d[k+15]>0;}}}
            if(tab==2){if(id==11)b.displayString=TaskData.TYPES[Math.min(5,d[12])];if(id>=20&&id<24){int k=80+(id-20)*24;b.visible=d[k]>0;b.enabled&=b.visible;b.displayString=(d[k+1]==d[13]?"> ":"")+fontRenderer.trimStringToWidth("#"+d[k+1]+" "+PanelMenu.getText(d,180+(id-20)*24,24)+" | "+(d[k+3]==1?"ON":"OFF")+" | "+d[k+4],295);}}
            if(tab==3){b.visible=d[50]>0;b.enabled&=b.visible;if(id==30)b.displayString=d[52]==1?"Stop":"Start";if(id==31)b.displayString=d[53]==1?"Auto":"Manual";if(id==32)b.displayString=d[54]==1?"Strict":"Flexible";if(id==33)b.displayString="Priority "+d[55];if(id==42)b.displayString="Group "+d[60];if((id==34||id==35)&&(d[51]==TaskEngine.AI||d[51]==TaskEngine.INFERENCE)||(id==36||id==37)&&d[51]==TaskEngine.BANK)b.enabled=false;}
            if(tab==6){if(id==11)b.displayString=d[16]==1?"Personal":"Corporate";if(id==12)b.displayString=TaskData.COINS[d[15]];if(id==15)b.enabled&=d[15]!=0;if(id>=20&&id<24){int i=id-20;b.enabled&=d[38+i]>0;b.displayString=fontRenderer.trimStringToWidth(PanelMenu.getText(d,180+i*24,24)+" / "+amount(d[38+i]),143);}}}}
    static String amount(long v){return String.format(Locale.ROOT,"%.3f",v/1000.0);}
    @Override protected void drawGuiContainerBackgroundLayer(float f,int mx,int my){
        // Shared light shell, reminiscent of the Minecraft inventory and XP Luna.
        rect(0,0,xSize,ySize,0xff35485e);
        rect(1,1,xSize-2,ySize-2,0xff7e9fc3);
        rect(3,3,xSize-6,ySize-6,0xffece9d8);
        rect(3,3,xSize-6,1,0xfffdfcf6);
        gradient(4,4,xSize-8,16,0xff387ac8,0xff2156a0);
        rect(4,4,xSize-8,1,0xff8bb6eb);
        if(menu.type==0){
            int tab=menu.data[0];
            inset(5,64,310,145,0xfff9f8f2);
            if(tab==1)for(int i=0;i<3;i++){
                if(menu.data[80+i*24]==0)continue;
                inset(7,83+i*41,306,40,i%2==0?0xffeef2f7:0xfff7f8fa);
            }
            if(tab==0){
                inset(9,70,302,19,0xffe8eef6);
                inset(9,93,302,27,0xfffefefd);
                inset(9,123,302,55,0xfffefefd);
                rect(13,139,294,1,0xffdfe3e8);rect(13,158,294,1,0xffdfe3e8);
            }
            if(tab==3&&fieldVisible())inset(8,65,201,17,0xfffefefd);
            if(tab==4){inset(9,73,302,39,0xfffefefd);inset(9,114,302,38,0xfffefefd);}
            if(tab==7){for(int i=0;i<6;i++)rect(9,83+i*18,302,17,i%2==0?0xffe8eef6:0xfff9f8f2);}
            if(tab==8){for(int i=0;i<3;i++)inset(9,70+i*42,302,39,0xfffefefd);}
            rect(8,209,304,1,0xffb6bdc7);
        }else if(menu.type==1){
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
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){int[] d=menu.data;if(menu.type==1){title(tr("Server details","Сведения о сервере"));text("Server "+d[10]+"U / U"+(d[1]+1)+" | "+status(d[4])+" | "+d[5]+" FE/t",10,51,BLUE);int[] p=java.util.Arrays.copyOfRange(d,20,20+ServerSpec.COUNT);text("Board T"+p[0]+" / CPU T"+p[1]+(p[ServerSpec.CPU2]>0?" + T"+p[ServerSpec.CPU2]:""),10,64,FG);text("RAM "+ServerSpec.ramGB(p)+" GB / disk "+ServerSpec.diskGB(p)+" GB",10,80,FG);text("PSU "+ServerSpec.psu(p)+" FE/t / NIC "+ServerSpec.nic(p)+" Mb/s",10,96,FG);text("GPU: "+ServerSpec.gpuTotal(p)+" / VRAM "+ServerSpec.vram(p)+" GB",10,112,FG);text("CPU "+d[6]+"% / GPU mask "+Integer.toBinaryString(d[7]),10,135,MUTED);text("RAM used "+d[8]+" GB / network "+d[9]+" Mb/s",10,151,MUTED);text(tr("Components are read-only. Use workbench.","Компоненты меняются только на рабочем столе."),10,189,MUTED);return;}
        if(menu.type==2){title("Switch T"+d[10]+" / "+(d[12]==1?"ONLINE":"OFFLINE"));text(tr("Select port, then server","Выберите порт, затем сервер"),10,31,MUTED);text(tr("Rack units","Юниты стойки"),10,143,MUTED);return;}
        int tab=d[0];title("DataOS 0.4");text(d[1]==1?"ONLINE":"OFFLINE",252,9,d[1]==1?0xffe2ffd7:0xffffd8cf);
        if(tab==0){text("Racks: "+d[2]+"     Servers: "+d[3]+"     Tasks: "+d[9],12,76,FG);text("WAN: "+d[6]+" / "+d[4]+" Mb/s",12,96,FG);bar(12,109,296,d[6],d[4]);text("Power: "+d[7]+" FE/t",12,126,BLUE);text("Mining: "+d[8]+" H/s",12,145,FG);text("NTC: "+amount(PanelMenu.getLong(d,20)),12,165,GREEN);text(tr("Choose Tasks to create a job.","Создайте задачу во вкладке «Задачи»."),12,188,MUTED);}
        if(tab==1){for(int i=0;i<3;i++){int k=80+i*24;if(d[k]==0)continue;clip(PanelMenu.getText(d,180+i*24,24)+" U"+d[k+2]+" | CPU "+d[k+5]+"% | "+status(d[k+4]),9,85+i*41,301,FG);clip("GPU "+Integer.bitCount(d[k+6])+"/"+d[k+12]+" | "+d[k+9]+" FE/t | "+d[k+8]+" Mb/s",9,112+i*41,166,MUTED);}text((d[10]+1)+" / "+d[11]+"  Task #"+d[13],120,218,MUTED);}
        if(tab==2)text((d[10]+1)+" / "+d[11]+"  #"+d[13],132,218,MUTED);
        if(tab==3){if(d[50]==0){text(tr("Select a task first.","Сначала выберите задачу."),12,76,RED);return;}String[] labels={"CPU % (total)","GPU count","RAM GB","Network Mb/s"};for(int i=0;i<4;i++)text(labels[i]+": "+d[62+i]+" / "+d[56+i],10,108+i*17,FG);text("Disk GB: "+d[69]+" / "+d[68],10,176,FG);clip("#"+d[50]+" "+TaskData.TYPES[d[51]]+" | "+d[61]+(d[51]==4?" | "+d[66]+"/100000":"")+" | "+(d[67]==1?"FULL":"PARTIAL"),10,219,300,BLUE);}
        if(tab==4){text("WAN "+d[6]+" / "+d[4]+" Mb/s",12,80,FG);bar(12,94,296,d[6],d[4]);text("Switch fabric "+d[6]+" / "+d[5]+" Mb/s",12,117,FG);bar(12,131,296,d[6],d[5]);text("NIC / port constraints applied per server.",12,155,MUTED);}
        if(tab==5){text("Total "+d[7]+" FE/t",12,80,BLUE);text("PSU capacities are summed.",12,105,FG);text("Loss of power / overload: manual restart.",12,125,FG);text("Per-server usage: Servers tab.",12,145,MUTED);}
        if(tab==6){int off=d[16]==1?26:20;for(int i=0;i<3;i++)text(TaskData.COINS[i]+" "+amount(PanelMenu.getLong(d,off+i*2)),10+i*102,91,FG);text("Difficulty "+d[32+d[15]]+"% | Rate "+amount(d[35+d[15]])+" NTC",10,107,BLUE);text("Marketplace / NTC",10,148,MUTED);}
        if(tab==7){text("Recent events",12,70,FG);for(int i=0;i<6;i++)clip(PanelMenu.getText(d,180+i*32,32),12,87+i*18,294,MUTED);}
        if(tab==8){for(int i=0;i<3;i++){int y=76+i*42;text(TaskData.TYPES[i]+" | "+d[70+i]+" H/s",12,y,FG);text("Tasks "+d[73+i]+" | Difficulty "+d[32+i]+"% | "+amount(d[35+i])+" NTC",12,y+14,BLUE);}}}
    static String status(int n){return n==11?"PSU OVERLOAD":n==6?"NO POWER":n==7?"RUNNING":n==8?"PARTIAL":n==5?"NO NET":n==10?"INCOMPLETE":n==1?"NO CTRL":"IDLE";}
    @Override public void drawScreen(int x,int y,float f){drawDefaultBackground();super.drawScreen(x,y,f);if(fieldVisible())nameField.drawTextBox();}
    @Override protected void mouseClicked(int x,int y,int b)throws java.io.IOException{if(fieldVisible())nameField.mouseClicked(x,y,b);super.mouseClicked(x,y,b);}
    @Override protected void keyTyped(char c,int k)throws java.io.IOException{if(fieldVisible()&&nameField.isFocused()&&nameField.textboxKeyTyped(c,k))return;super.keyTyped(c,k);}
}
