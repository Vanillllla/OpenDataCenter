package odc;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.*;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Light DataOS shell with virtualized, vertically scrolling tables. Coordinates are logical pixels. */
final class DataOSScreen extends GuiContainer {
    private static final int W=474, ROW=20, TOP=48, HEADER=20, OVERVIEW_FIXED=268;
    private static final int FG=0xff26323d, MUTED=0xff65717e, BLUE=0xff245790, WHITE=0xfffafbfc, EDGE=0xff9faebb, ALT=0xffedf2f7;
    private static final int[] COL={0,25,81,127,196,273,345,401,450};
    private final PanelMenu menu;
    private int tab,scroll,requested=-1,viewHeight=220,h=278;private float scale=1;private int originX,originY;
    private UUID editing;private String groupText="";private boolean dragging;private int dragOffset;
    private final int[] tabScroll=new int[9];
    DataOSScreen(PanelMenu menu){super(menu);this.menu=menu;}
    private String tr(String en,String ru){return mc.getLanguageManager().getCurrentLanguage().getLanguageCode().equals("ru_ru")?ru:en;}
    private String[] tabs(){return new String[]{tr("Overview","Обзор"),tr("Servers","Серверы"),tr("Tasks","Задачи"),tr("Resources","Ресурсы"),tr("Network","Сеть"),tr("Power","Питание"),tr("Wallet","Кошелёк"),tr("Log","Журнал"),tr("Mining","Майнинг")};}
    @Override public void initGui(){super.initGui();layout();requested=-1;request();}
    private void layout(){scale=Math.min(1f,Math.min((width-12f)/W,(height-12f)/220f));scale=Math.max(.1f,scale);h=Math.min(420,Math.max(220,(int)((height-12)/scale)));viewHeight=h-TOP-12;originX=(int)((width-W*scale)/2);originY=(int)((height-h*scale)/2);}
    private NBTTagCompound data(){return menu.view;}
    private int count(){return data().getInteger("Tab")==tab?data().getInteger("Count"):0;}
    private int content(){return tab==0?OVERVIEW_FIXED+HEADER+count()*ROW:tab==1?count()*ROW:0;}
    private int visibleHeight(){return viewHeight-(tab==1?HEADER:0);}
    private int maxScroll(){return Math.max(0,content()-visibleHeight());}
    private void request(){int start=tab==0?Math.max(0,(scroll-OVERVIEW_FIXED-HEADER)/ROW):Math.max(0,scroll/ROW);if(start==requested)return;requested=start;send(0,0,null);}
    private void send(int action,int value,UUID id){RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),action,value,id));}
    private void setScroll(int value){scroll=Math.max(0,Math.min(maxScroll(),value));tabScroll[tab]=scroll;request();}
    private void changeTab(int next){if(next==tab)return;editing=null;tabScroll[tab]=scroll;tab=next;scroll=tabScroll[tab];requested=-1;request();}
    private int lx(int x){return (int)Math.floor((x-originX)/scale);}private int ly(int y){return (int)Math.floor((y-originY)/scale);}
    private void rect(int x,int y,int w,int h,int color){if(w>0&&h>0)Gui.drawRect(x,y,x+w,y+h,color);}
    private void text(String s,int x,int y,int max,int c){if(max>0)fontRenderer.drawString(fontRenderer.trimStringToWidth(s,max),x,y,c);}
    private void center(String s,int x,int y,int w,int c){s=fontRenderer.trimStringToWidth(s,Math.max(0,w-4));text(s,x+(w-fontRenderer.getStringWidth(s))/2,y,w,c);}
    private void button(String s,int x,int y,int w,int h,boolean selected,boolean hover){rect(x,y,w,h,selected?0xff527eae:0xff9aa9b8);rect(x+1,y+1,w-2,h-2,selected?0xffd8e9fa:hover?0xffedf5ff:0xffe7ebef);rect(x+1,y+1,w-2,1,0xffffffff);rect(x+1,y+h-2,w-2,1,0xffc0cbd5);center(s,x,y+(h-8)/2,w,FG);}
    private void scissor(int x,int y,int w,int h){double sx=mc.displayWidth/(double)width,sy=mc.displayHeight/(double)height;int l=(int)Math.ceil((originX+x*scale)*sx),r=(int)Math.floor((originX+(x+w)*scale)*sx),b=(int)Math.ceil(mc.displayHeight-(originY+(y+h)*scale)*sy),t=(int)Math.floor(mc.displayHeight-(originY+y*scale)*sy);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(l,b,Math.max(0,r-l),Math.max(0,t-b));}
    private NBTTagCompound row(int index){NBTTagCompound d=data();if(d.getInteger("Tab")!=tab)return null;int local=index-d.getInteger("Start");NBTTagList rows=d.getTagList("Rows",10);return local>=0&&local<rows.tagCount()?rows.getCompoundTagAt(local):null;}
    private static String num(long n){return Long.toString(n);}
    private static String money(long n){return String.format(Locale.ROOT,"%.3f",n/1000d);}
    private String pair(long a,long b){return num(a)+" / "+num(b);}
    private String ids(NBTTagCompound r){int[] a=r.getIntArray("Jobs");if(a.length==0)return "—";StringBuilder s=new StringBuilder();for(int id:a){if(s.length()>0)s.append(", ");s.append(id);}return s.toString();}
    private String[] headers(){return new String[]{"ID",tr("CPU cores","Ядра CPU"),"GPU","RAM, GB",tr("Disk, GB","Диск, ГБ"),tr("Task IDs","ID задач"),tr("Group","Группа"),tr("Power","Питание")};}
    @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();layout();if(data().getInteger("Tab")==tab&&scroll>maxScroll())setScroll(maxScroll());int x=lx(mx),y=ly(my);GlStateManager.pushMatrix();GlStateManager.translate(originX,originY,0);GlStateManager.scale(scale,scale,1);
        rect(0,0,W,h,0xff63788d);rect(1,1,W-2,h-2,0xffe7ebef);rect(3,3,W-6,h-6,0xfff2f4f6);rect(4,4,W-8,19,0xff3269a8);text("DataOS",11,10,100,0xffffffff);text(data().getBoolean("Online")?"ONLINE":"OFFLINE",W-62,10,56,0xffffffff);
        String[] names=tabs();for(int i=0;i<names.length;i++){int tx=5+i*51;button(names[i],tx,27,50,17,i==tab,x>=tx&&x<tx+50&&y>=27&&y<44);}
        rect(5,TOP,W-10,viewHeight,WHITE);rect(5,TOP,W-10,1,EDGE);rect(5,TOP+viewHeight-1,W-10,1,EDGE);
        if(tab==0){scissor(6,TOP+1,452,viewHeight-2);overview(TOP-scroll);GL11.glDisable(GL11.GL_SCISSOR_TEST);}
        else if(tab==1){serverHeader(TOP);scissor(6,TOP+HEADER,452,viewHeight-HEADER-1);int first=scroll/ROW;for(int i=first;i<count()&&TOP+HEADER+i*ROW-scroll<TOP+viewHeight;i++){int yy=TOP+HEADER+i*ROW-scroll;serverRow(i,yy,x,y);}GL11.glDisable(GL11.GL_SCISSOR_TEST);}
        else{text(tr("This tab will be redesigned in the next stage.","Вкладка будет переработана на следующем этапе."),14,TOP+15,430,MUTED);}
        if(tab==0||tab==1)scrollbar();GlStateManager.popMatrix();
        if(tab==1&&y>=TOP+HEADER&&y<TOP+viewHeight&&x>=6+COL[5]&&x<6+COL[6]){NBTTagCompound r=row((y-TOP-HEADER+scroll)/ROW);if(r!=null&&r.getIntArray("Jobs").length>0){List<String> tip=new ArrayList<>();tip.add(tr("Running task IDs","ID запущенных задач"));String a=ids(r);while(a.length()>0){int n=Math.min(a.length(),55);tip.add(a.substring(0,n));a=a.substring(n);}drawHoveringText(tip,mx,my);}}
    }
    private void section(String title,int y){rect(11,y,440,17,0xffdce7f1);text(title,16,y+5,430,BLUE);}
    private void overview(int y){NBTTagCompound d=data();section(tr("Infrastructure","Инфраструктура"),y+5);text(tr("Racks: ","Стойки: ")+d.getInteger("Racks")+"      "+tr("Servers: ","Серверы: ")+d.getInteger("Servers")+"      "+tr("Tasks: ","Задачи: ")+d.getInteger("Tasks"),16,y+28,430,FG);
        section(tr("Resource allocation","Загрузка ресурсов"),y+44);String[] labels={tr("Network load","Загрузка сети"),tr("CPU cores","Ядра CPU"),tr("GPUs","Видеокарты GPU"),"RAM (GB)","SSD M.2 (GB)","SSD SATA (GB)","HDD (GB)",tr("Total WAN (Mb/s)","Общая сеть (Мбит/с)"),tr("Power (FE/t)","Потребление (FE/t)")};
        for(int i=0;i<labels.length;i++){int yy=y+65+i*16;if(i%2==0)rect(11,yy-2,440,16,ALT);text(labels[i],16,yy+1,143,FG);int k=i==0||i==7?6:i==8?7:i-1;long u=d.getLong("U"+k),t=d.getLong("T"+k);if(i==8){text(num(u),166,yy+1,260,FG);continue;}if(i==0){long cap=Math.min(d.getLong("T6"),d.getLong("T7"));t=cap;}text(pair(u,t),166,yy+1,145,FG);int f=(int)(106*Math.min(1,u/(double)Math.max(1,t)));rect(330,yy+1,108,9,0xffd7dfe7);rect(331,yy+2,f,7,0xff5795c9);}
        section(tr("Income and balance","Доход и баланс"),y+214);text(tr("Per second","В секунду"),166,y+219,130,BLUE);text(tr("Balance","Баланс"),330,y+219,108,BLUE);for(int i=0;i<2;i++){int yy=y+235+i*14;text(TaskData.COINS[i],16,yy,100,FG);text(money(d.getLong("Income"+i))+" /s",166,yy,130,FG);text(money(d.getLong("Balance"+i)),330,yy,108,BLUE);}
        section(tr("Running and paused tasks","Запущенные и приостановленные задачи"),y+OVERVIEW_FIXED);int base=y+OVERVIEW_FIXED+HEADER;int first=Math.max(0,(TOP-base)/ROW);for(int i=first;i<count()&&base+i*ROW<TOP+viewHeight;i++){int yy=base+i*ROW;NBTTagCompound r=row(i);rect(11,yy,440,ROW-1,i%2==0?ALT:WHITE);if(r==null)continue;text("#"+r.getInteger("Id"),16,yy+6,38,FG);text(r.getString("Name"),60,yy+6,220,FG);text(r.getBoolean("Enabled")?(r.getInteger("Work")>0?tr("Running","Работает"):tr("Waiting","Ожидает")):tr("Paused","Пауза"),290,yy+6,150,MUTED);}
    }
    private void serverHeader(int y){rect(6,y,450,HEADER,0xffdce7f1);String[] names=headers();for(int i=0;i<names.length;i++){center(names[i],6+COL[i],y+6,COL[i+1]-COL[i],FG);if(i>0)rect(6+COL[i],y,1,HEADER,EDGE);}}
    private void serverRow(int i,int y,int mx,int my){rect(6,y,450,ROW-1,i%2==0?ALT:WHITE);NBTTagCompound r=row(i);if(r==null)return;String[] s={num(r.getInteger("Id")),pair(r.getInteger("CPU"),r.getInteger("Cores")),pair(r.getInteger("GPU"),r.getInteger("GPUs")),pair(r.getInteger("RAM"),r.getInteger("RAMTotal")),pair(r.getInteger("Disk"),r.getInteger("DiskTotal")),ids(r)};for(int k=0;k<s.length;k++)center(s[k],6+COL[k],y+6,COL[k+1]-COL[k],FG);
        boolean edit=editing!=null&&editing.equals(r.getUniqueId("Key"));String group=edit?groupText+"_":r.getInteger("Group")==0?"—":num(r.getInteger("Group"));button(group,6+COL[6]+2,y+2,COL[7]-COL[6]-4,16,edit,false);boolean on=r.getBoolean("Enabled");int bx=6+COL[7]+3;button(on?tr("OFF","ВЫКЛ"):tr("ON","ВКЛ"),bx+10,y+2,33,16,false,mx>=bx&&mx<bx+44&&my>=y&&my<y+ROW);circle(bx,y+7,r.getBoolean("Powered")?0xff42a852:0xff929ba2);}
    private void circle(int x,int y,int c){rect(x+2,y,3,7,c);rect(x,y+2,7,3,c);rect(x+1,y+1,5,5,c);}
    private int scrollTop(){return TOP+(tab==1?HEADER:0);}
    private int thumb(){return Math.max(12,(int)((long)visibleHeight()*visibleHeight()/Math.max(visibleHeight(),content())));}
    private int thumbY(){return scrollTop()+(maxScroll()==0?0:(int)((long)(visibleHeight()-thumb())*scroll/maxScroll()));}
    private void scrollbar(){int t=scrollTop();rect(459,t,8,visibleHeight(),0xffdce2e8);rect(460,thumbY(),6,thumb(),0xff8ca4bb);}
    @Override public void handleMouseInput() throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0&&(tab==0||tab==1)){int x=lx(Mouse.getEventX()*width/mc.displayWidth),y=ly(height-Mouse.getEventY()*height/mc.displayHeight-1);if(x>=5&&x<W-5&&y>=TOP&&y<TOP+viewHeight)setScroll(scroll-(wheel>0?1:-1)*ROW*3);}}
    @Override protected void mouseClicked(int mx,int my,int b)throws IOException{int x=lx(mx),y=ly(my);if(b!=0)return;if(y>=27&&y<44&&x>=5&&x<464){changeTab((x-5)/51);return;}if((tab==0||tab==1)&&x>=459&&x<=467&&y>=scrollTop()&&y<scrollTop()+visibleHeight()){dragging=true;dragOffset=y-thumbY();if(dragOffset<0||dragOffset>=thumb()){dragOffset=thumb()/2;drag(y);}return;}if(tab==1&&y>=TOP+HEADER&&y<TOP+viewHeight){NBTTagCompound r=row((y-TOP-HEADER+scroll)/ROW);if(r!=null&&r.getBoolean("Editable")){if(x>=6+COL[6]&&x<6+COL[7]){editing=r.getUniqueId("Key");groupText=r.getInteger("Group")==0?"":num(r.getInteger("Group"));return;}if(x>=6+COL[7]&&x<456){send(1,r.getBoolean("Enabled")?0:1,r.getUniqueId("Key"));return;}}}editing=null;}
    private void drag(int y){int room=visibleHeight()-thumb();if(room>0)setScroll((int)((long)(y-scrollTop()-dragOffset)*maxScroll()/room));}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(dragging)drag(ly(y));}
    @Override protected void mouseReleased(int x,int y,int state){dragging=false;}
    @Override protected void keyTyped(char c,int k)throws IOException{if(editing!=null){if(k==Keyboard.KEY_ESCAPE){editing=null;return;}if(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){try{send(2,groupText.isEmpty()?0:Integer.parseInt(groupText),editing);editing=null;}catch(NumberFormatException ignored){}return;}if(k==Keyboard.KEY_BACK){if(!groupText.isEmpty())groupText=groupText.substring(0,groupText.length()-1);return;}if(k==Keyboard.KEY_DELETE){groupText="";return;}if(c>='0'&&c<='9'&&groupText.length()<10){groupText+=c;return;}return;}super.keyTyped(c,k);}
    @Override protected void drawGuiContainerBackgroundLayer(float f,int x,int y){}
    @Override public boolean doesGuiPauseGame(){return false;}
}
