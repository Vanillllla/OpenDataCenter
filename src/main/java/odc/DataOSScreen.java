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
    private static final int[] COL={0,84,109,157,189,246,300,343,401,450};
    private final PanelMenu menu;
    private int tab,scroll,requested=-1,viewHeight=220,h=278;private float scale=1;private int originX,originY;
    private UUID editing,serverDetail;private String groupText="";private int poolPicker=-1,poolScroll,serverScroll;private boolean dragging;private int dragOffset;
    private final int[] tabScroll=new int[8];
    DataOSScreen(PanelMenu menu){super(menu);this.menu=menu;}
    private String tr(String en,String ru){return mc.getLanguageManager().getCurrentLanguage().getLanguageCode().equals("ru_ru")?ru:en;}
    private String[] tabs(){return new String[]{tr("Overview","Обзор"),tr("Servers","Серверы"),tr("Tasks","Задачи"),tr("Resources","Ресурсы"),tr("Network","Сеть"),tr("Power","Питание"),tr("Wallet","Кошелёк"),tr("Log","Журнал")};}
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
    private void changeTab(int next){if(next==tab)return;editing=null;serverDetail=null;poolPicker=-1;clearTaskContext();pending=false;tabScroll[tab]=scroll;tab=next;scroll=tabScroll[tab];requested=-1;request();}
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
    private String[] headers(){return new String[]{tr("Name","Название"),"ID","CPU","GPU","RAM",tr("Disk","Диск"),tr("Tasks","Задачи"),tr("Pool","Пул"),tr("Power","Питание")};}
    @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();layout();if(data().getInteger("Tab")==tab&&scroll>maxScroll())setScroll(maxScroll());int x=lx(mx),y=ly(my);GlStateManager.pushMatrix();GlStateManager.translate(originX,originY,0);GlStateManager.scale(scale,scale,1);
        rect(0,0,W,h,0xff63788d);rect(1,1,W-2,h-2,0xffe7ebef);rect(3,3,W-6,h-6,0xfff2f4f6);rect(4,4,W-8,19,0xff3269a8);text("DataOS",11,10,100,0xffffffff);text(data().getBoolean("Online")?"ONLINE":"OFFLINE",W-62,10,56,0xffffffff);
        String[] names=tabs();for(int i=0;i<names.length;i++){int tx=5+i*58;button(names[i],tx,27,57,17,i==tab,x>=tx&&x<tx+57&&y>=27&&y<44);}
        rect(5,TOP,W-10,viewHeight,WHITE);rect(5,TOP,W-10,1,EDGE);rect(5,TOP+viewHeight-1,W-10,1,EDGE);
        if(tab==0){scissor(6,TOP+1,452,viewHeight-2);overview(TOP-scroll);GL11.glDisable(GL11.GL_SCISSOR_TEST);}
        else if(tab==1){serverHeader(TOP);scissor(6,TOP+HEADER,452,viewHeight-HEADER-1);int first=scroll/ROW;for(int i=first;i<count()&&TOP+HEADER+i*ROW-scroll<TOP+viewHeight;i++){int yy=TOP+HEADER+i*ROW-scroll;serverRow(i,yy,x,y);}GL11.glDisable(GL11.GL_SCISSOR_TEST);}
        else if(tab==2 && data().getInteger("Tab")==2){taskPanel();}else if(tab==3&&data().getInteger("Tab")==3){resourcePanel();}else{text(tr("This tab will be redesigned in the next stage.","Вкладка будет переработана на следующем этапе."),14,TOP+15,430,MUTED);}
        if(tab==0||tab==1)scrollbar();if(tab==1&&serverDetail!=null)serverWindow();if(poolPicker>=0)poolWindow();GlStateManager.popMatrix();
        if(tab==1&&serverDetail==null&&y>=TOP+HEADER&&y<TOP+viewHeight&&x>=6+COL[6]&&x<6+COL[7]){NBTTagCompound r=row((y-TOP-HEADER+scroll)/ROW);if(r!=null&&r.getIntArray("Jobs").length>0){List<String> tip=new ArrayList<>();tip.add(tr("Running task IDs","ID запущенных задач"));String a=ids(r);while(a.length()>0){int n=Math.min(a.length(),55);tip.add(a.substring(0,n));a=a.substring(n);}drawHoveringText(tip,mx,my);}}
    }
    private void section(String title,int y){rect(11,y,440,17,0xffdce7f1);text(title,16,y+5,430,BLUE);}
    private void overview(int y){NBTTagCompound d=data();section(tr("Infrastructure","Инфраструктура"),y+5);text(tr("Racks: ","Стойки: ")+d.getInteger("Racks")+"      "+tr("Servers: ","Серверы: ")+d.getInteger("Servers")+"      "+tr("Tasks: ","Задачи: ")+d.getInteger("Tasks"),16,y+28,430,FG);
        section(tr("Resource allocation","Загрузка ресурсов"),y+44);String[] labels={tr("Network load","Загрузка сети"),tr("CPU cores","Ядра CPU"),tr("GPUs","Видеокарты GPU"),"RAM (GB)","SSD M.2 (GB)","SSD SATA (GB)","HDD (GB)",tr("Total WAN (Mb/s)","Общая сеть (Мбит/с)"),tr("Power (FE/t)","Потребление (FE/t)")};
        for(int i=0;i<labels.length;i++){int yy=y+65+i*16;if(i%2==0)rect(11,yy-2,440,16,ALT);text(labels[i],16,yy+1,143,FG);int k=i==0||i==7?6:i==8?7:i-1;long u=d.getLong("U"+k),t=d.getLong("T"+k);if(i==8){text(num(u),166,yy+1,260,FG);continue;}if(i==0){long cap=Math.min(d.getLong("T6"),d.getLong("T7"));t=cap;}text(pair(u,t),166,yy+1,145,FG);int f=(int)(106*Math.min(1,u/(double)Math.max(1,t)));rect(330,yy+1,108,9,0xffd7dfe7);rect(331,yy+2,f,7,0xff5795c9);}
        section(tr("Income and balance","Доход и баланс"),y+214);text(tr("Per second","В секунду"),166,y+219,130,BLUE);text(tr("Balance","Баланс"),330,y+219,108,BLUE);for(int i=0;i<2;i++){int yy=y+235+i*14;text(TaskData.COINS[i],16,yy,100,FG);text(money(d.getLong("Income"+i))+" /s",166,yy,130,FG);text(money(d.getLong("Balance"+i)),330,yy,108,BLUE);}
        section(tr("Running and paused tasks","Запущенные и приостановленные задачи"),y+OVERVIEW_FIXED);int base=y+OVERVIEW_FIXED+HEADER;int first=Math.max(0,(TOP-base)/ROW);for(int i=first;i<count()&&base+i*ROW<TOP+viewHeight;i++){int yy=base+i*ROW;NBTTagCompound r=row(i);rect(11,yy,440,ROW-1,i%2==0?ALT:WHITE);if(r==null)continue;text("#"+r.getInteger("Id"),16,yy+6,38,FG);text(title(r.getInteger("Type")),60,yy+6,220,FG);text(r.getBoolean("Enabled")?(r.getInteger("Work")>0?tr("Running","Работает"):tr("Waiting","Ожидает")):tr("Paused","Пауза"),290,yy+6,150,MUTED);}
    }
    private void serverHeader(int y){rect(6,y,450,HEADER,0xffdce7f1);String[] names=headers();for(int i=0;i<names.length;i++){center(names[i],6+COL[i],y+6,COL[i+1]-COL[i],FG);if(i>0)rect(6+COL[i],y,1,HEADER,EDGE);}}
    private void serverRow(int i,int y,int mx,int my){rect(6,y,450,ROW-1,i%2==0?ALT:WHITE);NBTTagCompound r=row(i);if(r==null)return;String[] s={r.getString("Name"),num(r.getInteger("Id")),pair(r.getInteger("CPU"),r.getInteger("Cores")),pair(r.getInteger("GPU"),r.getInteger("GPUs")),pair(r.getInteger("RAM"),r.getInteger("RAMTotal")),pair(r.getInteger("Disk"),r.getInteger("DiskTotal")),ids(r)};for(int k=0;k<s.length;k++)center(s[k],6+COL[k],y+6,COL[k+1]-COL[k],FG);
        boolean edit=editing!=null&&editing.equals(r.getUniqueId("Key"));String group=edit?groupText+"_":r.getString("Pool");button(group,6+COL[7]+2,y+2,COL[8]-COL[7]-4,16,edit,false);boolean on=r.getBoolean("Enabled");int bx=6+COL[8]+2;button(on?tr("OFF","ВЫКЛ"):tr("ON","ВКЛ"),bx+10,y+2,34,16,false,mx>=bx&&mx<bx+44&&my>=y&&my<y+ROW);circle(bx,y+7,r.getBoolean("Powered")?0xff42a852:0xff929ba2);}
    private NBTTagCompound detailServer(){if(serverDetail==null)return null;NBTTagList rows=data().getTagList("Rows",10);for(int i=0;i<rows.tagCount();i++){NBTTagCompound r=rows.getCompoundTagAt(i);if(serverDetail.equals(r.getUniqueId("Key")))return r;}return null;}
    private void loadBar(String label,long used,long total,String unit,int x,int y,int w){text(label,x,y,90,FG);text(pair(used,total)+" "+unit,x+92,y,122,FG);int bw=Math.max(8,w-220);rect(x+214,y,bw,9,0xffd7dfe7);rect(x+215,y+1,(int)((bw-2)*Math.min(1,Math.max(0,used)/(double)Math.max(1,total))),7,0xff5795c9);}
    private void serverWindow(){NBTTagCompound r=detailServer();if(r==null){serverDetail=null;return;}int x=30,y=TOP+3,w=414,hh=Math.min(196,viewHeight-6);rect(5,TOP,W-10,viewHeight,0x55000000);rect(x,y,w,hh,EDGE);rect(x+1,y+1,w-2,hh-2,WHITE);rect(x+2,y+2,w-4,16,BLUE);text(r.getString("Name")+"  #"+r.getInteger("Id"),x+8,y+6,w-45,0xffffffff);button("X",x+w-24,y+3,20,14,false,false);text(tr("Pool: ","Пул: ")+r.getString("Pool")+"    "+tr("Tasks: ","Задачи: ")+ids(r),x+8,y+22,w-16,FG);
        int[] a=r.getIntArray("DiskUsed"),b=r.getIntArray("DiskTypes");if(a.length!=3||b.length!=3){a=new int[3];b=new int[3];}
        List<String> labels=new ArrayList<>(Arrays.asList("CPU","GPU","RAM","HDD","SSD","M.2",tr("Network","Сеть")));List<Long> used=new ArrayList<>(Arrays.asList((long)r.getInteger("CPU"),(long)r.getInteger("GPU"),(long)r.getInteger("RAM"),(long)a[2],(long)a[1],(long)a[0],(long)r.getInteger("Net")));List<Long> total=new ArrayList<>(Arrays.asList((long)r.getInteger("Cores"),(long)r.getInteger("GPUs"),(long)r.getInteger("RAMTotal"),(long)b[2],(long)b[1],(long)b[0],(long)r.getInteger("NetTotal")));List<String> units=new ArrayList<>(Arrays.asList(tr("cores","ядер"),"","GB","GB","GB","GB","Mb/s"));
        int[] parts=r.getIntArray("Parts");if(parts.length==ServerSpec.COUNT){BitSet cores=BitSet.valueOf(r.getByteArray("CoreMask"));int offset=0;for(int slot:new int[]{ServerSpec.CPU,ServerSpec.CPU2}){Part part=Part.at(parts[slot]);if(part.cores==0)continue;labels.add("CPU "+(slot==ServerSpec.CPU?1:2)+" "+part.name);used.add((long)cores.get(offset,offset+part.cores).cardinality());total.add((long)part.cores);units.add(tr("cores","ядер"));offset+=part.cores;}int gpu=0;for(int slot=ServerSpec.PCIE;slot<ServerSpec.PSU;slot++){Part part=Part.at(parts[slot]);for(int k=0;k<part.chips;k++,gpu++){labels.add("GPU "+gpu+" "+part.name);used.add((r.getInteger("GpuMask")&(1<<gpu))!=0?1L:0L);total.add(1L);units.add(part.gb+" GB VRAM");}}}
        int visible=Math.max(1,(hh-56)/13);serverScroll=Math.max(0,Math.min(serverScroll,Math.max(0,labels.size()-visible)));for(int i=serverScroll;i<Math.min(labels.size(),serverScroll+visible);i++)loadBar(labels.get(i),used.get(i),total.get(i),units.get(i),x+8,y+36+(i-serverScroll)*13,w-16);text(tr("Power: ","Питание: ")+r.getInteger("Power")+" FE/t   "+tr("Wheel: components","Колесо: компоненты"),x+8,y+hh-13,w-16,FG);
    }

    private void resourcePanel(){NBTTagCompound d=data();button(tr("Pool: ","Пул: ")+(d.getString("PoolName").isEmpty()?tr("All","Все"):d.getString("PoolName"))+"  v",12,TOP+5,300,18,false,false);String[] labels={"CPU","GPU","RAM","M.2","SSD","HDD",tr("Network (shared)","Сеть (общая)")};String[] units={tr("cores","ядер"),"","GB","GB","GB","GB","Mb/s"};for(int i=0;i<7;i++)loadBar(labels[i],d.getLong("U"+i),d.getLong("T"+i),units[i],16,TOP+34+i*16,434);}
    private int poolCount(){return data().getTagList("Pools",10).tagCount()+(poolPicker==3?1:0);}
    private int poolVisible(){return Math.max(1,(viewHeight-45)/17);}
    private void poolWindow(){int x=72,y=TOP+4,w=330,hh=viewHeight-8;rect(5,TOP,W-10,viewHeight,0x55000000);rect(x,y,w,hh,EDGE);rect(x+1,y+1,w-2,hh-2,WHITE);rect(x+2,y+2,w-4,18,BLUE);text(tr("Select pool","Выберите пул"),x+8,y+7,w-40,0xffffffff);button("X",x+w-25,y+3,21,15,false,false);NBTTagList rows=data().getTagList("Pools",10);poolScroll=Math.max(0,Math.min(poolScroll,Math.max(0,poolCount()-poolVisible())));for(int i=poolScroll;i<Math.min(poolCount(),poolScroll+poolVisible());i++){boolean all=poolPicker==3&&i==0;String name=all?tr("All pools","Все пулы"):rows.getCompoundTagAt(i-(poolPicker==3?1:0)).getString("Name");button(name,x+8,y+24+(i-poolScroll)*17,w-16,16,false,false);}if(poolCount()==0)text(tr("Create a pool on a server first.","Сначала задайте пул серверу."),x+8,y+26,w-16,MUTED);}
    private void poolClick(int x,int y){int top=TOP+4;if(x>=377&&x<402&&y>=top&&y<top+20){poolPicker=-1;return;}if(x<80||x>=394||y<top+24)return;int i=poolScroll+(y-top-24)/17;if(i>=poolCount()||i>=poolScroll+poolVisible())return;int op=poolPicker;UUID key=op==3&&i==0?TaskConsole.NONE:data().getTagList("Pools",10).getCompoundTagAt(i-(op==3?1:0)).getUniqueId("Key");poolPicker=-1;if(op==3)send(3,0,key);else cmd(11,op==13?3:op,key,null);}
    private int taskScroll,confirm=-1,input;
    private boolean pending;private int sentRevision,seenRevision=Integer.MIN_VALUE,seenActions;
    private String entry="";
    private UUID host=TaskConsole.NONE;
    private String ui(String key){return net.minecraft.client.resources.I18n.format("tasks.odc."+key);}
    private final String[] sections={"active","permanent","offers","contracts","services","history"};
    private void clearTaskContext(){confirm=-1;input=0;entry="";host=TaskConsole.NONE;detailsOpen=false;detailScroll=0;editScroll=0;editDrag=null;numberKey=null;editorScrollbarDrag=false;}
    private void syncTaskContext(){
        int rev=data().getInteger("Revision"),actions=data().getInteger("Actions");
        if(pending&&rev!=sentRevision)pending=false;
        if(rev!=seenRevision||actions!=seenActions){confirm=-1;seenRevision=rev;seenActions=actions;}
        if(data().getBoolean("Assign")){if(!host.equals(TaskConsole.NONE)&&detail().getKeySet().isEmpty()){host=TaskConsole.NONE;input=0;}}
        else if(detail().getKeySet().isEmpty())clearTaskContext();
        if(!data().getBoolean("Online")){confirm=-1;input=0;}
    }
    private void cmd(int op,int value,UUID key,int[] limits){if(pending||!data().getBoolean("Online"))return;sentRevision=data().getInteger("Revision");pending=true;confirm=-1;RackInteraction.channel.sendToServer(new TaskCommand(menu.windowId,sentRevision,op,value,key,limits));}
    private boolean enabledAction(int i){
        if(pending||!data().getBoolean("Online"))return false;
        if(data().getBoolean("Assign"))return data().getBoolean("DraftAvailable")&&(i!=2||!detail().getKeySet().isEmpty())&&(i!=3||data().getBoolean("CanCommit"));
        return !detail().getKeySet().isEmpty()&&TaskActions.has(data().getInteger("Actions"),operation(i));
    }
    private int operation(int i){return i==0?(data().getInteger("Section")==2?2:3):i==1?7:i==2?(detail().getLong("Notice")==0?8:9):10;}
    private void taskButton(String label,int x,int y,int w,int hh,boolean selected,boolean enabled){button(label,x,y,w,hh,selected&&enabled,false);if(!enabled){rect(x+1,y+1,w-2,hh-2,0xffe1e4e7);center(label,x,y+(hh-8)/2,w,0xff929aa2);}}

    private int tierFilter,ruleFilter,sizeFilter,priceFilter,pauseFilter,timeFilter,sortMode,kindFilter,stateFilter,hostFilter;
    private List<String> hosts(){
        SortedSet<String> names=new TreeSet<>();NBTTagList all=data().getTagList("Rows",10);
        for(int i=0;i<all.tagCount();i++)for(String id:all.getCompoundTagAt(i).getString("Machines").split(" "))if(!id.isEmpty())names.add(id);
        return new ArrayList<>(names);
    }
    private String status(NBTTagCompound r){
        if(r.hasKey("Status"))return r.getString("Status");
        if(!r.hasKey("Job"))return "available";
        if(r.getLong("Notice")>0)return r.getLong("Notice")>data().getLong("Now")?"notice":"maintenance";
        if(r.getBoolean("Enabled"))return r.getLong("Rate")>0?"running":"waiting";
        if(r.getBoolean("Fault"))return "interrupted";
        return !r.hasKey("Start")&&Workload.contract(r.getInteger("Type"))?"accepted":"paused";
    }
    private final String[] states={"all","accepted","running","waiting","paused","notice","maintenance","interrupted"};
    private NBTTagList taskRows(){
        NBTTagList all=data().getTagList("Rows",10);int sec=data().getInteger("Section");
        if(data().getBoolean("Assign") || sec!=0 && sec!=2)return all;
        List<String> hosts=hosts();List<NBTTagCompound> rows=new ArrayList<>();
        for(int i=0;i<all.tagCount();i++){NBTTagCompound r=all.getCompoundTagAt(i);int t=r.getInteger("Type"),rule=r.getInteger("Rule");
            if(tierFilter>0 && Workload.ALL[t].tier!=tierFilter)continue;
            if(sec==0 && (kindFilter>0 && kindFilter!=(t<9?1:t<21?2:3) || stateFilter>0&&!states[stateFilter].equals(status(r)) || hostFilter>0&&hostFilter<=hosts.size()&&!r.getString("Machines").contains(hosts.get(hostFilter-1))))continue;
            if(sec==2 && (ruleFilter>0&&ruleFilter!=rule+1 || sizeFilter>0&&sizeFilter!=r.getInteger("Size")+1 || priceFilter>0&&(priceFilter==2)!=(r.getLong("Fee")>0) || pauseFilter>0&&(pauseFilter==2)!=ContractMarket.CONTINUOUS[rule] || timeFilter>0&&(timeFilter==2)!=ContractMarket.TIMED[rule]))continue;
            rows.add(r);
        }
        if(sec==2 && sortMode>0)rows.sort(Comparator.comparingDouble(r->sortMode==1?-r.getLong("Pay"):sortMode==2?r.getLong("Fee"):sortMode==3?r.getLong("Goal"):sortMode==4?Workload.ALL[r.getInteger("Type")].tier:-(r.getLong("Pay")-r.getLong("Fee"))*Workload.ALL[r.getInteger("Type")].x/r.getLong("Goal")));
        NBTTagList result=new NBTTagList();for(NBTTagCompound r:rows)result.appendTag(r);return result;
    }
    private String[] controls(int sec){
        String all=ui("all");
        if(sec==0)return new String[]{"T "+(tierFilter==0?all:tierFilter),ui("kind"+kindFilter),ui(states[stateFilter]),hostFilter>0&&hostFilter<=hosts().size()?hosts().get(hostFilter-1):ui("hosts")};
        return new String[]{"T "+(tierFilter==0?all:tierFilter),ruleFilter==0?ui("rules"):ui("rule"+(ruleFilter-1)),ui("size"+sizeFilter),ui("price"+priceFilter),ui("pause"+pauseFilter),ui("time"+timeFilter),ui("sort"+sortMode)};
    }
    private void filter(int sec,int i){
        if(i==0)tierFilter=(tierFilter+1)%4;
        else if(sec==0){if(i==1)kindFilter=(kindFilter+1)%4;if(i==2)stateFilter=(stateFilter+1)%states.length;if(i==3)hostFilter=(hostFilter+1)%(hosts().size()+1);}
        else if(i==1)ruleFilter=(ruleFilter+1)%9;else if(i==2)sizeFilter=(sizeFilter+1)%6;else if(i==3)priceFilter=(priceFilter+1)%3;else if(i==4)pauseFilter=(pauseFilter+1)%3;else if(i==5)timeFilter=(timeFilter+1)%3;else sortMode=(sortMode+1)%6;
        taskScroll=0;clearTaskContext();cmd(0,sec,TaskConsole.NONE,null);
    }
    private int rowTop(){int sec=data().getInteger("Section");return !data().getBoolean("Assign")&&(sec==0||sec==2)?86:68;}
    private int visibleRows(){return Math.max(1,(h-rowTop()-96)/16);}
    private boolean selected(NBTTagCompound r){
        if(data().getBoolean("Assign"))return !host.equals(TaskConsole.NONE)&&host.equals(rowKey(r));
        return TaskActions.matches(data().getUniqueId("Selected"),data().getInteger("SelectedType"),rowKey(r),r.getInteger("Type"));
    }
    private NBTTagCompound detail(){NBTTagList rows=taskRows();for(int i=0;i<rows.tagCount();i++){NBTTagCompound r=rows.getCompoundTagAt(i);if(selected(r))return r;}return new NBTTagCompound();}
    private UUID rowKey(NBTTagCompound r){return r.hasUniqueId("Key")?r.getUniqueId("Key"):TaskConsole.NONE;}
    private String title(int type){return net.minecraft.client.resources.I18n.format("task.odc."+type);}
    private boolean detailsOpen;
    private int detailScroll;
    private String decimal(double v){return String.format(Locale.ROOT,"%.3f",v);}
    private List<String> detailLines(){
        NBTTagCompound r=detail(),d=data();boolean draft=d.getBoolean("Assign");
        int t=d.getInteger("SelectedType");if(!draft && r.hasKey("Type"))t=r.getInteger("Type");
        List<String> lines=new ArrayList<>();if(t<0||t>=Workload.ALL.length)return lines;
        Workload w=Workload.ALL[t];long now=d.getLong("Now");
        lines.add(title(t)+"   T"+w.tier+"   "+ui(status(r)));
        lines.add("CPU "+w.cpu+"   GPU "+w.gpu+" × "+w.vram+" GB   RAM "+w.ram+" GB");
        lines.add("Disk "+w.disk+" GB   I/O "+w.io+" MB/s   NET "+w.net+" MB/s   X "+w.x);
        if(draft&&r.hasKey("FreeCPU")){
            lines.add(r.getString("Name")+"   "+(r.getBoolean("Online")?"ON":"OFF"));
            lines.add(ui("free")+" CPU "+r.getInteger("FreeCPU")+"/"+r.getInteger("CPU")+"   GPU "+r.getInteger("FreeGPU")+"/"+r.getInteger("GPU"));
            lines.add("VRAM [GPU=GB] "+r.getString("VRAM")+"   "+ui("units")+" "+r.getInteger("Fit"));
            lines.add("RAM "+r.getInteger("FreeRAM")+"/"+r.getInteger("RAM")+" GB   Disk "+r.getInteger("FreeDisk")+"/"+r.getInteger("Disk")+" GB");
            lines.add("I/O "+r.getInteger("FreeIO")+" MB/s   NET "+decimal(r.getInteger("FreeNet")/80.0)+" MB/s");
        }
        NBTTagCompound stats=draft?d:r;
        double rate=stats.getLong("Rate")/100000.0*d.getDouble("Factor");
        lines.add(ui("units")+" "+stats.getInteger("CountUnits")+"   CPU "+stats.getInteger("CPU")+"   GPU "+stats.getInteger("GPU")+"   RAM "+stats.getInteger("RAM")+" GB");
        lines.add(ui("rate")+" "+decimal(rate)+(t<9?" KriptoCoin / 30 s":t<21?" Score/s":" NCoin / 10 min"));
        if(t>=21){lines.add(decimal(rate*6)+" NCoin/h   "+ui("next")+" "+(600000-r.getLong("Elapsed"))/1000+" s");
            lines.add(ui("payments")+" "+decimal(r.getLong("Paid0")/1000.)+" / "+decimal(r.getLong("Paid1")/1000.)+" / "+decimal(r.getLong("Paid2")/1000.)+" / "+decimal(r.getLong("Paid3")/1000.));
            lines.add(ui("penalty")+" "+decimal(r.getLong("Penalty")/1000.)+" NCoin");}
        if(draft && d.hasKey("Contract"))r=d.getCompoundTag("Contract");
        if(r.hasKey("Goal")){
            lines.add(r.getString("Company")+"   "+ui("rule"+r.getInteger("Rule"))+"   "+ui("size"+(1+r.getInteger("Size"))));
            lines.add("Score "+r.getLong("Progress")+"/"+r.getLong("Goal")+"   "+decimal(100.0*r.getLong("Progress")/r.getLong("Goal"))+"%");
            lines.add(ui("fee")+" "+decimal(r.getLong("Fee")/1000.)+"   "+ui("pay")+" "+decimal(r.getLong("Pay")/1000.)+"   "+ui("profit")+" "+decimal((r.getLong("Pay")-r.getLong("Fee"))/1000.)+" NCoin");
            lines.add(ui("time0")+" "+(r.hasKey("Deadline")?Math.max(0,r.getLong("Deadline")-now):r.getLong("Duration"))/1000+" s   "+ui("early")+" "+r.getInteger("Bonus")+"%");
            lines.add(ui("downtime")+" "+r.getLong("Idle")/1000+"/"+r.getInteger("Grace")/1000+" s   "+ui(ContractMarket.CONTINUOUS[r.getInteger("Rule")]?"pause2":"pause1"));
            lines.add(ui("fixed")+" "+ui(r.getBoolean("Fixed")?"yes":"no")+"   "+ui("pinned")+" "+ui(r.getBoolean("Pinned")?"yes":"no")+"   "+ui("single")+" "+ui(r.getBoolean("Single")?"yes":"no"));
            if(!r.hasKey("Job"))lines.add(ui("expires")+" "+Math.max(0,r.getLong("Expires")-now)/1000+" s");
            double speed=rate>0?rate:d.getLong("Capacity")/100000.*d.getDouble("Factor");
            double eta=speed<=0?Double.POSITIVE_INFINITY:(r.getLong("Goal")-r.getLong("Progress"))/speed;
            lines.add("ETA "+(Double.isInfinite(eta)?"—":Math.ceil(eta)+" s"));
            if(speed<=0 || r.getLong("Duration")>0 && eta*1000>(r.hasKey("Deadline")?r.getLong("Deadline")-now:r.getLong("Duration")))lines.add(ui("warning"));
        }
        if(r.hasKey("PoolName"))lines.add(tr("Pool: ","Пул: ")+r.getString("PoolName"));
        if(r.hasKey("Machines"))lines.add(ui("hosts")+" "+r.getString("Machines"));
        NBTTagList bindings=r.getTagList("Bindings",10);for(int i=0;i<bindings.tagCount();i++){NBTTagCompound b=bindings.getCompoundTagAt(i);lines.add(b.getString("Host")+"  "+b.getString("Kind")+"  "+(b.getString("Kind").equals("NETWORK")?"NIC":Part.at(b.getInteger("Model")).name)+"  slot "+b.getInteger("Slot")+" / "+b.getInteger("Unit")+"  × "+b.getInteger("Amount")+(b.getInteger("IO")>0?" · "+b.getInteger("IO")+" MB/s":""));}
        if(r.hasKey("End"))lines.add(ui("elapsed")+" "+(r.getLong("End")-r.getLong("Start"))/1000+" s   "+ui("pay")+" "+decimal(r.getLong("Received")/1000.));
        return lines;
    }
    private int editScroll,dragValue,dragMax,numberMax;private UUID editDrag,numberKey;private String numberText="";private boolean editorScrollbarDrag;
    private final EnumSet<PoolResources.Kind> categories=EnumSet.of(PoolResources.Kind.CPU,PoolResources.Kind.GPU);
    private static final int EDIT_ROW=24;
    private static final class EditRow {
        final PoolResources.Kind kind;final NBTTagCompound data;final int physical; // -2 category, -1 family, >=0 physical index
        EditRow(PoolResources.Kind k,NBTTagCompound d,int p){kind=k;data=d;physical=p;}
    }
    private String resourceName(PoolResources.Kind k){return k==PoolResources.Kind.NETWORK?tr("Network","Сеть"):k==PoolResources.Kind.NVME?"M.2 / NVMe":k.name();}
    private String unitName(PoolResources.Kind k){return k==PoolResources.Kind.CPU?tr("cores","ядер"):k==PoolResources.Kind.GPU?tr("chips","чипов"):k==PoolResources.Kind.NETWORK?"Mb/s":"GB";}
    private List<EditRow> editorRows(){
        List<EditRow> out=new ArrayList<>();NBTTagList families=data().getTagList("Rows",10),physical=data().getTagList("Physical",10);UUID expanded=data().getUniqueId("Expanded");
        int start=data().getInteger("PhysicalStart"),count=data().getInteger("PhysicalCount");
        for(PoolResources.Kind k:PoolResources.Kind.values()){
            out.add(new EditRow(k,null,-2));if(!categories.contains(k))continue;
            for(int j=0;j<families.tagCount();j++){NBTTagCompound r=families.getCompoundTagAt(j);if(!k.name().equals(r.getString("Kind")))continue;out.add(new EditRow(k,r,-1));if(!expanded.equals(r.getUniqueId("Key")))continue;
                for(int i=0;i<count;i++)out.add(new EditRow(k,i>=start&&i<start+physical.tagCount()?physical.getCompoundTagAt(i-start):null,i));
            }
        }return out;
    }
    private int editorTop(){return TOP+76;}
    private int editorBottom(){return h-52;}
    private int editorMax(List<EditRow> rows){return Math.max(0,rows.size()*EDIT_ROW-Math.max(1,editorBottom()-editorTop()));}
    private String familyName(EditRow row){NBTTagCompound r=row.data;Part part=Part.at(r.getInteger("Model"));int speed=r.getInteger("Speed");switch(row.kind){case CPU:return part.name+" · "+speed+" MHz";case GPU:return part.name+" · "+speed+" MHz · "+r.getInteger("VRAM")+" GB VRAM";case RAM:return part.name+" · "+speed+" MHz";case NETWORK:return speed+" Mb/s NIC";default:return part.name+" · "+speed+" MB/s";}}
    private String draftError(String key){switch(key){case "":return tr("Ready to apply","Готово к запуску");case "empty":return tr("Select resources below","Выберите ресурсы ниже");case "offline":return tr("Network snapshot unavailable","Нет данных доступной сети");case "unavailable":return tr("Resource unavailable or reserved by another task","Ресурс недоступен или занят другой задачей");case "local requirements":return tr("Each selected server needs a complete work unit (incl. disk I/O and VRAM)","На каждом выбранном сервере нужна полная единица (в т.ч. I/O и VRAM)");case "single":return tr("Contract requires one server","Контракт требует один сервер");case "fixed":return tr("Contract has fixed resources","Контракт запрещает менять ресурсы");case "pinned":return tr("Contract fixes the server set","Контракт фиксирует набор серверов");case "retained":return tr("Retained data cannot be moved or reduced","Сохранённые данные нельзя переносить или уменьшать");default:return tr("Invalid selection","Недопустимый выбор");}}
    private void draftPanel(){
        NBTTagCompound d=data();int t=d.getInteger("SelectedType");if(t<0||t>=Workload.ALL.length)return;Workload w=Workload.ALL[t];int[] sum=d.getIntArray("Totals");if(sum.length!=7)sum=new int[7];
        rect(7,TOP+1,458,74,0xffedf2f7);text(title(t)+"  |  "+tr("Pool: ","Пул: ")+d.getString("DraftPoolName"),12,TOP+5,450,BLUE);
        text("CPU "+sum[0]+"   GPU "+sum[1]+"   RAM "+sum[2]+" GB   HDD "+sum[3]+" GB",12,TOP+17,448,FG);
        text("SSD "+sum[4]+" GB   M.2 "+sum[5]+" GB   NET "+sum[6]+" Mb/s",12,TOP+28,448,FG);
        double rate=d.getLong("Rate")/(double)TaskEngine.SCALE, income=rate*d.getDouble("Factor");text(tr("Units: ","Единиц: ")+d.getInteger("CountUnits")+"    Score "+decimal(rate)+"    "+decimal(income)+(t<9?" KriptoCoin/30 s":t<21?" Score/s":" NCoin/10 min"),12,TOP+39,448,BLUE);
        text(tr("Per unit: ","На единицу: ")+"CPU "+w.cpu+"   GPU "+w.gpu+" / "+w.vram+" GB   RAM "+w.ram+" GB",12,TOP+50,448,FG);
        text("Disk "+w.disk+" GB   I/O "+w.io+" MB/s   NET "+decimal(w.net*8)+" Mb/s",12,TOP+61,448,FG);
        List<EditRow> rows=editorRows();editScroll=Math.max(0,Math.min(editScroll,editorMax(rows)));int top=editorTop(),bottom=editorBottom();
        scissor(7,top,454,Math.max(1,bottom-top));int pageRequest=-1;int first=editScroll/EDIT_ROW;
        for(int i=first;i<rows.size()&&top+i*EDIT_ROW-editScroll<bottom;i++){
            EditRow row=rows.get(i);int y=top+i*EDIT_ROW-editScroll;
            if(row.physical==-2){rect(8,y+1,450,22,0xffdce7f1);text((categories.contains(row.kind)?"v  ":">  ")+resourceName(row.kind),13,y+7,275,BLUE);text(sum[row.kind.ordinal()]+" "+unitName(row.kind),348,y+7,105,BLUE);continue;}
            if(row.data==null){text(tr("Loading...","Загрузка..."),22,y+7,180,MUTED);if(pageRequest<0)pageRequest=Math.max(0,row.physical-16);continue;}
            NBTTagCompound r=row.data;rect(8,y+1,450,22,i%2==0?ALT:WHITE);int max=r.getInteger("Max"),amount=r.getInteger("Amount");UUID key=r.getUniqueId("Key");if(key.equals(editDrag))amount=dragValue;
            if(row.physical==-1){text((key.equals(d.getUniqueId("Expanded"))?"v ":"> ")+familyName(row),12,y+3,215,FG);text(tr("Choose model quantity / click to expand","Количество / раскрыть модель"),18,y+13,205,MUTED);}
            else{text(r.getString("Host"),22,y+3,205,FG);text((row.kind==PoolResources.Kind.NETWORK?"NIC":"slot "+r.getInteger("Slot"))+((row.kind==PoolResources.Kind.CPU||row.kind==PoolResources.Kind.GPU)?" / "+r.getInteger("Unit"):""),24,y+13,205,MUTED);}
            boolean enabled=d.getBoolean("DraftAvailable")&&!pending&&max>0;taskButton("-",229,y+4,17,16,false,enabled&&amount>0);taskButton("+",381,y+4,17,16,false,enabled&&amount<max);
            rect(250,y+10,127,5,0xffbac6d0);int pos=(int)(119L*amount/Math.max(1,max));rect(250,y+10,pos+4,5,0xff719dcb);rect(250+pos,y+6,8,13,enabled?BLUE:MUTED);text(key.equals(numberKey)?numberText+"_":amount+"/"+max,402,y+7,54,FG);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);int max=editorMax(rows),room=Math.max(1,bottom-top);rect(461,top,5,room,0xffdce2e8);int thumb=Math.max(8,(int)(room*(long)room/Math.max(room,rows.size()*EDIT_ROW)));rect(461,top+(max==0?0:(int)((room-thumb)*(long)editScroll/max)),5,thumb,0xff8ca4bb);
        if(pageRequest>=0&&!pending&&editDrag==null&&numberKey==null&&!d.getUniqueId("Expanded").equals(TaskConsole.NONE))cmd(14,pageRequest,d.getUniqueId("Expanded"),null);
        text(draftError(d.getString("Error")),12,h-49,450,d.getBoolean("CanCommit")?0xff35764a:0xff9e422e);text(d.getString("Diagnostic"),12,h-38,450,MUTED);
        taskButton(ui("back"),8,h-27,100,18,false,!pending);taskButton(tr("Clear","Сбросить"),114,h-27,100,18,false,!pending&&d.getBoolean("DraftAvailable"));taskButton(tr("Apply / start","Применить / запуск"),280,h-27,184,18,false,!pending&&d.getBoolean("CanCommit"));
    }
    private int sliderValue(int x,int max){return (int)Math.max(0,Math.min(max,Math.round((x-254)*max/119.0)));}
    private void draftClick(int x,int y){
        NBTTagCompound d=data();if(y>=h-27&&y<h-9){if(x>=8&&x<108)cmd(0,d.getInteger("Section"),TaskConsole.NONE,null);else if(x>=114&&x<214&&d.getBoolean("DraftAvailable"))cmd(15,0,TaskConsole.NONE,null);else if(x>=280&&x<464&&d.getBoolean("CanCommit"))cmd(6,0,d.getUniqueId("Selected"),null);return;}
        if(y>=editorTop()&&y<editorBottom()&&x>=460&&x<468){editorScrollbarDrag=true;editorDragScroll(y);return;}
        if(y<editorTop()||y>=editorBottom()||x<8||x>458)return;List<EditRow> rows=editorRows();int i=(y-editorTop()+editScroll)/EDIT_ROW;if(i<0||i>=rows.size())return;EditRow row=rows.get(i);
        if(row.physical==-2){if(!categories.add(row.kind))categories.remove(row.kind);return;}
        if(row.data==null)return;NBTTagCompound r=row.data;UUID key=r.getUniqueId("Key");if(x<225&&row.physical==-1){cmd(13,0,key.equals(d.getUniqueId("Expanded"))?TaskConsole.NONE:key,null);return;}
        if(!d.getBoolean("DraftAvailable"))return;int max=r.getInteger("Max"),amount=r.getInteger("Amount");if(x>=229&&x<246&&amount>0){cmd(12,amount-1,key,null);return;}if(x>=381&&x<398&&amount<max){cmd(12,amount+1,key,null);return;}
        if(x>=400&&x<458&&max>0){numberKey=key;numberMax=max;numberText=Integer.toString(amount);return;}
        if(x>=250&&x<377&&max>0){numberKey=null;editDrag=key;dragMax=max;dragValue=sliderValue(x,max);}
    }
    private void editorDragScroll(int y){int top=editorTop(),room=Math.max(1,editorBottom()-top);editScroll=(int)Math.max(0,Math.min(editorMax(editorRows()),(long)(y-top)*editorMax(editorRows())/room));}
    private void editorNumberKey(char c,int k){if(k==Keyboard.KEY_ESCAPE){numberKey=null;return;}if(k==Keyboard.KEY_BACK){if(!numberText.isEmpty())numberText=numberText.substring(0,numberText.length()-1);return;}if(k==Keyboard.KEY_DELETE){numberText="";return;}if(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){try{int n=Integer.parseInt(numberText);if(n>=0&&n<=numberMax){UUID key=numberKey;numberKey=null;cmd(12,n,key,null);}}catch(NumberFormatException ignored){}return;}if(c>='0'&&c<='9'&&numberText.length()<10)numberText+=c;}
    private void taskPanel(){
        syncTaskContext();boolean draft=data().getBoolean("Assign");if(draft){draftPanel();return;}int section=data().getInteger("Section");NBTTagList rows=taskRows();
        if(detailsOpen){
            List<String> lines=detailLines();int count=(h-91)/13;detailScroll=Math.max(0,Math.min(detailScroll,lines.size()-count));
            for(int i=detailScroll;i<Math.min(lines.size(),detailScroll+count);i++)text(lines.get(i),12,55+(i-detailScroll)*13,450,FG);
            button(ui("back"),8,h-27,100,18,false,false);return;
        }
        for(int i=0;i<6;i++)button(ui(sections[i]),6+i*77,49,75,17,i==section,false);
        if(rowTop()==86){String[] c=controls(section);int w=455/c.length;for(int i=0;i<c.length;i++)button(c[i],7+i*w,68,w-1,15,false,false);}
        taskScroll=Math.max(0,Math.min(taskScroll,rows.tagCount()-visibleRows()));
        for(int n=taskScroll;n<Math.min(rows.tagCount(),taskScroll+visibleRows());n++){
            NBTTagCompound r=rows.getCompoundTagAt(n);int y=rowTop()+(n-taskScroll)*16;
            String name=draft?(r.getBoolean("Chosen")?"[+] ":"[ ] ")+rowKey(r).toString().substring(0,8)+"   CPU "+r.getInteger("CPU")+"   GPU "+r.getInteger("GPU")+"   RAM "+r.getInteger("RAM")+" GB   Disk "+r.getInteger("Disk")+" GB":title(r.getInteger("Type"));
            if(r.hasKey("Company"))name+="   "+r.getString("Company")+"   "+ui("rule"+r.getInteger("Rule"))+"   "+r.getLong("Pay")/1000.0+" NCoin";
            if(r.hasKey("Job"))name+="   #"+r.getInteger("Job")+"   "+ui(status(r));
            rect(7,y,456,15,!pending&&selected(r)?0xffcde1fa:n%2==0?ALT:WHITE);text(name,10,y+3,448,FG);
        }
        taskButton(ui("details"),361,h-48,100,16,false,!pending&&data().getBoolean("Online")&&(draft?data().getBoolean("DraftAvailable"):!detail().getKeySet().isEmpty()));int t=data().getInteger("SelectedType"),y=h-90; NBTTagCompound r=detail();
        if(t>=0 && t<Workload.ALL.length&&(draft||!r.getKeySet().isEmpty())&&!pending){Workload w=Workload.ALL[t];text(title(t)+"   T"+w.tier+"   CPU "+w.cpu+"   GPU "+w.gpu+" × "+w.vram+" GB   RAM "+w.ram+" GB",10,y,450,BLUE);
            text("Disk "+w.disk+" GB   I/O "+w.io+" MB/s   NET "+w.net+" MB/s   X "+w.x,10,y+11,450,FG);}
        if(draft)text(input==0?ui("units")+" "+data().getInteger("CountUnits")+"    "+ui("rate")+" "+data().getLong("Rate")/100000.0:ui(input==1?"quantity":"limits")+" "+entry+"_",10,y+28,450,FG);
        else if(r.hasKey("Goal"))text("Score "+r.getLong("Progress")+" / "+r.getLong("Goal")+"   "+ui("fee")+" "+r.getLong("Fee")/1000.0+"   "+ui("pay")+" "+r.getLong("Pay")/1000.0,10,y+24,450,FG);
        if(r.hasKey("Status"))text(ui(r.getString("Status"))+"   "+ui("pay")+" "+r.getLong("Received")/1000.0+"   "+ui("penalty")+" "+r.getLong("Penalty")/1000.0,10,y+38,452,FG);
        if(r.getLong("Notice")>0)text(ui("maintenance")+" "+Math.max(0,(r.getLong("Notice")-data().getLong("Now"))/1000)+" s",10,y+38,450,FG);
        if(r.hasKey("Goal") && r.getLong("Goal")>0){int fill=(int)Math.min(200,200.0*r.getLong("Progress")/r.getLong("Goal"));rect(10,h-36,200,3,EDGE);rect(10,h-36,fill,3,BLUE);}
        if(section==2 && r.getLong("Fee")>data().getLong("Balance"))text(ui("noCash"),10,h-39,205,0xffa02f23);
        if(confirm==2&&section==2)text(ui("fee")+" "+decimal(r.getLong("Fee")/1000.)+" NCoin",215,h-38,140,0xffa02f23);
        String[] labels=draft?new String[]{"quantity","strict","manual","start"}:new String[]{section==2?"accept":"assign","pause",r.getLong("Notice")==0?"maintenance":"undo","cancel"};
        for(int i=0;i<4;i++)taskButton(ui(!draft&&confirm==operation(i)&&enabledAction(i)?"confirm":labels[i]),8+i*114,h-27,112,18,draft&&i==1&&data().getBoolean("Strict"),enabledAction(i));
    }
    private void taskClick(int x,int y){
        syncTaskContext();
        if(detailsOpen){if(y>=h-27&&y<h-9&&x>=8&&x<108)detailsOpen=false;return;}
        if(pending||!data().getBoolean("Online"))return;
        boolean draft=data().getBoolean("Assign");
        if(draft){draftClick(x,y);return;}
        if(x>=361&&x<461&&y>=h-48&&y<h-32){if(draft?data().getBoolean("DraftAvailable"):!detail().getKeySet().isEmpty()){detailsOpen=true;detailScroll=0;}return;}
        if(y>=49&&y<66&&x>=6&&x<468){clearTaskContext();taskScroll=0;cmd(0,(x-6)/77,TaskConsole.NONE,null);return;}
        if(rowTop()==86&&y>=68&&y<84&&x>=7&&x<462){int sec=data().getInteger("Section");filter(sec,Math.min(controls(sec).length-1,(x-7)/(455/controls(sec).length)));return;}
        if(x>=7&&x<463&&y>=rowTop()&&y<rowTop()+visibleRows()*16){
            int index=taskScroll+(y-rowTop())/16;NBTTagList rows=taskRows();clearTaskContext();
            if(index>=rows.tagCount()){cmd(0,data().getInteger("Section"),TaskConsole.NONE,null);return;}
            NBTTagCompound r=rows.getCompoundTagAt(index);
            if(draft){host=rowKey(r);if(data().getBoolean("DraftAvailable"))cmd(4,r.getBoolean("Chosen")?0:1,host,null);}
            else cmd(1,r.getInteger("Type"),rowKey(r),null);return;
        }
        if(y<h-27||y>=h-9||x<8||x>=464||(x-8)%114>=112)return;
        int i=(x-8)/114;if(!enabledAction(i))return;UUID key=data().getUniqueId("Selected");
        if(draft){
            if(i==0||i==2){entry="";input=i==0?1:2;return;}
            if(i==1){int[] a=new int[6];a[0]=data().getBoolean("Strict")?0:1;cmd(5,data().getInteger("Units"),key,a);}else cmd(6,0,key,null);
        }else{int op=operation(i);if(op!=3&&confirm!=op){confirm=op;return;}confirm=-1;if(op==2||op==3&&!detail().hasKey("Job")){poolPicker=op==3?13:2;poolScroll=0;return;}cmd(op,0,key,null);}
    }
    private void taskKey(char c,int k){
        syncTaskContext();if(input==0||pending||!enabledAction(input==1?0:2)){input=0;return;}
        if(k==Keyboard.KEY_ESCAPE){input=0;return;}
        if(k==Keyboard.KEY_BACK && !entry.isEmpty())entry=entry.substring(0,entry.length()-1);
        if(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){try{int[] a=new int[14];
            if(input==1){a[0]=data().getBoolean("Strict")?1:0;cmd(5,Integer.parseInt(entry),data().getUniqueId("Selected"),a);}
            else{String[] v=entry.split(",");if(v.length!=6)return;for(int i=1;i<6;i++)a[i]=Integer.parseInt(v[i]);
                    if(v[0].contains("-")||v[0].contains("+")){
                        BitSet bits=new BitSet();
                        for(String range:v[0].split("\\+")){String[] bounds=range.split("-");int lo=Integer.parseInt(bounds[0]),hi=Integer.parseInt(bounds[bounds.length-1]);if(lo<0||hi>255||lo>hi)return;bits.set(lo,hi+1);}
                        a[0]=bits.cardinality();for(int core=bits.nextSetBit(0);core>=0;core=bits.nextSetBit(core+1))a[6+core/32]|=1<<(core%32);
                    }else a[0]=Integer.parseInt(v[0]);cmd(4,2,host,a);}input=0;
        }catch(NumberFormatException ignored){}return;}if((Character.isDigit(c)||c==','||c=='+'||c=='-')&&entry.length()<70)entry+=c;
    }
    private void circle(int x,int y,int c){rect(x+2,y,3,7,c);rect(x,y+2,7,3,c);rect(x+1,y+1,5,5,c);}
    private int scrollTop(){return TOP+(tab==1?HEADER:0);}
    private int thumb(){return Math.max(12,(int)((long)visibleHeight()*visibleHeight()/Math.max(visibleHeight(),content())));}
    private int thumbY(){return scrollTop()+(maxScroll()==0?0:(int)((long)(visibleHeight()-thumb())*scroll/maxScroll()));}
    private void scrollbar(){int t=scrollTop();rect(459,t,8,visibleHeight(),0xffdce2e8);rect(460,thumbY(),6,thumb(),0xff8ca4bb);}
    @Override public void handleMouseInput() throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(poolPicker>=0){if(wheel!=0)poolScroll=Math.max(0,Math.min(Math.max(0,poolCount()-poolVisible()),poolScroll-Integer.signum(wheel)*3));return;}if(serverDetail!=null){if(wheel!=0)serverScroll=Math.max(0,serverScroll-Integer.signum(wheel)*3);return;}if(tab==2 && wheel!=0){if(data().getBoolean("Assign")){editScroll=Math.max(0,Math.min(editorMax(editorRows()),editScroll-Integer.signum(wheel)*EDIT_ROW*3));return;}if(detailsOpen)detailScroll+=wheel>0?-3:3;else taskScroll+=wheel>0?-3:3;return;}if(tab==2){taskScroll-=Integer.signum(wheel)*2;return;}if(wheel!=0&&(tab==0||tab==1)){int x=lx(Mouse.getEventX()*width/mc.displayWidth),y=ly(height-Mouse.getEventY()*height/mc.displayHeight-1);if(x>=5&&x<W-5&&y>=TOP&&y<TOP+viewHeight)setScroll(scroll-(wheel>0?1:-1)*ROW*3);}}
    @Override protected void mouseClicked(int mx,int my,int b)throws IOException{int x=lx(mx),y=ly(my);if(b!=0)return;
        if(poolPicker>=0){poolClick(x,y);return;}if(serverDetail!=null){if(x>=420&&x<444&&y>=TOP+3&&y<TOP+21)serverDetail=null;return;}
        if(y>=27&&y<44&&x>=5&&x<469){changeTab((x-5)/58);return;}
        if(tab==3&&data().getInteger("Tab")==3&&x>=12&&x<312&&y>=TOP+5&&y<TOP+23){poolPicker=3;poolScroll=0;return;}
        if(tab==2&&data().getInteger("Tab")==2){taskClick(x,y);return;}
        if((tab==0||tab==1)&&x>=459&&x<=467&&y>=scrollTop()&&y<scrollTop()+visibleHeight()){dragging=true;dragOffset=y-thumbY();if(dragOffset<0||dragOffset>=thumb()){dragOffset=thumb()/2;drag(y);}return;}
        if(tab==1&&x>=6&&x<456&&y>=TOP+HEADER&&y<TOP+viewHeight){NBTTagCompound r=row((y-TOP-HEADER+scroll)/ROW);if(r!=null){if(x>=6+COL[8]){if(r.getBoolean("Editable"))send(1,r.getBoolean("Enabled")?0:1,r.getUniqueId("Key"));return;}if(x>=6+COL[7]&&r.getBoolean("Editable")){editing=r.getUniqueId("Key");groupText=r.getString("Pool");return;}editing=null;serverDetail=r.getUniqueId("Key");serverScroll=0;return;}}editing=null;}
    private void drag(int y){int room=visibleHeight()-thumb();if(room>0)setScroll((int)((long)(y-scrollTop()-dragOffset)*maxScroll()/room));}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(editorScrollbarDrag){editorDragScroll(ly(y));return;}if(editDrag!=null){dragValue=sliderValue(lx(x),dragMax);return;}if(dragging)drag(ly(y));}
    @Override protected void mouseReleased(int x,int y,int state){dragging=false;editorScrollbarDrag=false;if(editDrag!=null){UUID key=editDrag;int value=dragValue;editDrag=null;if(tab==2&&data().getBoolean("Assign"))cmd(12,value,key,null);}}
    @Override protected void keyTyped(char c,int k)throws IOException{if(poolPicker>=0){if(k==Keyboard.KEY_ESCAPE)poolPicker=-1;return;}if(serverDetail!=null){if(k==Keyboard.KEY_ESCAPE)serverDetail=null;return;}if(tab==2&&data().getBoolean("Assign")&&numberKey!=null){editorNumberKey(c,k);return;}if(tab==2&&data().getBoolean("Assign")&&k==Keyboard.KEY_ESCAPE){cmd(0,data().getInteger("Section"),TaskConsole.NONE,null);return;}if(tab==2&&input!=0){taskKey(c,k);return;}if(editing!=null){if(k==Keyboard.KEY_ESCAPE){editing=null;return;}if(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),2,0,editing,groupText));editing=null;return;}if(k==Keyboard.KEY_BACK){if(!groupText.isEmpty())groupText=groupText.substring(0,groupText.length()-1);return;}if(k==Keyboard.KEY_DELETE){groupText="";return;}if(!Character.isISOControl(c)&&c!=167&&groupText.length()<32)groupText+=c;return;}super.keyTyped(c,k);}
    @Override protected void drawGuiContainerBackgroundLayer(float f,int x,int y){}
    @Override public boolean doesGuiPauseGame(){return false;}
}
