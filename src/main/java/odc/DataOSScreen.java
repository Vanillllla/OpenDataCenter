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
    private static final int W=474, ROW=20, TOP=48, HEADER=20, OVERVIEW_FIXED=463, SERVER_TOP=TOP+22, RESOURCE_FIXED=225, WALLET_FIXED=112, POWER_FIXED=286, POWER_ROW=30;
    private static final int FG=0xff26323d, MUTED=0xff65717e, BLUE=0xff245790, WHITE=0xfffafbfc, EDGE=0xff9faebb, ALT=0xffedf2f7;
    private static final int[] COL={0,84,109,157,189,246,300,343,401,450};
    private final PanelMenu menu;private boolean wasPowered;
    private int tab,scroll,requested=-1,viewHeight=220,h=278;private float scale=1;private int originX,originY;
    private UUID editing,serverDetail;private int editAction=2,focus;private String serverSearch="",serverPool="",taskSearch="";private int powerFilter,serverSort=1;private boolean serverDescending;
    private DataOSWidgets widgets;private NBTTagCompound rendered;private boolean listDrag;private int listDragOffset;private int popup=-1,popupScroll,popupSection,popupFilter;private List<String> popupItems=Collections.emptyList();
    private final DataOSUiState.History<NavState> navigation=new DataOSUiState.History<>();
    private static final class NavState {int tab,scroll,taskScroll,detailScroll,serverScroll,section,type,taskSort;boolean taskDescending;UUID server,task,pool,event;boolean details,oldest,serverDesc;String search,host,eventSearch,eventCategory,eventLevel,serverSearch,serverPool;int powerFilter,serverSort;int[] filters;}
    private String eventSearch="",eventCategory="",eventLevel="";private boolean eventOldest;private UUID eventDetail;private static final int LOG_TOP=TOP+22;
    private UUID expectedTask;private boolean taskDraftOpen;private int modalDrag,drawnScroll,drawnTaskScroll,drawnDetailScroll,drawnServerScroll;private String hostKey="";private String groupText="";private int poolPicker=-1,poolScroll,serverScroll;private boolean dragging;private int dragOffset;
    private final int[] tabScroll=new int[8];
    DataOSScreen(PanelMenu menu){super(menu);this.menu=menu;}
    private String tr(String en,String ru){return mc.getLanguageManager().getCurrentLanguage().getLanguageCode().equals("ru_ru")?ru:en;}
    private String[] tabs(){return new String[]{tr("Overview","Обзор"),tr("Servers","Серверы"),tr("Tasks","Задачи"),tr("Resources","Ресурсы"),tr("Power","Питание"),tr("Wallet","Кошелёк"),tr("Log","Журнал")};}
    @Override public void initGui(){super.initGui();layout();requested=-1;request();}
    private void layout(){scale=Math.min(1f,Math.min((width-12f)/W,(height-12f)/264f));scale=Math.max(.1f,scale);h=Math.min(420,Math.max(264,(int)((height-12)/scale)));viewHeight=h-TOP-12;originX=(int)((width-W*scale)/2);originY=(int)((height-h*scale)/2);}
    private NBTTagCompound data(){return rendered==null?menu.view:rendered;}
    private DataOSUiState.Query query(){return new DataOSUiState.Query(serverSearch,serverPool,powerFilter,serverSort,serverDescending);}
    private boolean ready(){return data().hasKey("Tab")&&data().getInteger("Tab")==tab&&(tab!=1||query().encode().equals(data().getString("Query")))&&(tab!=7||eventQuery().encode().equals(data().getString("Query")));}
    private EventJournal.Query eventQuery(){return new EventJournal.Query(eventSearch,eventCategory,eventLevel,eventOldest);}
    private void requestNow(){requested=-1;request();}
    private int count(){return ready()?data().getInteger("Count"):0;}
    private int content(){return tab==0?OVERVIEW_FIXED+HEADER+count()*ROW:(tab==1||tab==7)?count()*ROW:tab==3?RESOURCE_FIXED+count()*ROW:tab==6?WALLET_FIXED+count()*ROW:tab==5?POWER_FIXED+Math.max(1,count())*POWER_ROW:0;}
    private int visibleHeight(){return viewHeight-((tab==1||tab==7)?HEADER+22:0);}
    private int maxScroll(){return Math.max(0,content()-visibleHeight());}
    private void request(){if(!menu.terminalPowered())return;int start=tab==0?Math.max(0,(scroll-OVERVIEW_FIXED-HEADER)/ROW):tab==3?Math.max(0,(scroll-RESOURCE_FIXED)/ROW):tab==6?Math.max(0,(scroll-WALLET_FIXED)/ROW):tab==5?Math.max(0,(scroll-POWER_FIXED)/POWER_ROW):Math.max(0,scroll/ROW);if(start==requested)return;requested=start;RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),0,0,null,tab==1?query().encode():tab==7?eventQuery().encode():""));}
    private void send(int action,int value,UUID id){RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),action,value,id));}
    private void setScroll(int value){scroll=Math.max(0,Math.min(maxScroll(),value));tabScroll[tab]=scroll;request();}
    private void changeTab(int next){if(next==tab||pending)return;focus=0;poolPicker=-1;popup=-1;confirm=-1;tabScroll[tab]=scroll;tab=next;scroll=tabScroll[tab];requestNow();}
    private void localTab(int next){tabScroll[tab]=scroll;tab=next;scroll=tabScroll[tab];focus=0;poolPicker=-1;popup=-1;confirm=-1;requestNow();}
    private NavState capture(){NavState n=new NavState();n.tab=tab;n.scroll=scroll;n.event=eventDetail;n.eventSearch=eventSearch;n.eventCategory=eventCategory;n.eventLevel=eventLevel;n.oldest=eventOldest;n.serverSearch=serverSearch;n.serverPool=serverPool;n.powerFilter=powerFilter;n.serverSort=serverSort;n.serverDesc=serverDescending;n.server=serverDetail;n.serverScroll=serverScroll;n.taskScroll=taskScroll;n.detailScroll=detailScroll;n.details=detailsOpen;n.taskSort=taskSort;n.taskDescending=taskDescending;n.search=taskSearch;n.host=hostKey;n.filters=new int[]{tierFilter,ruleFilter,sizeFilter,priceFilter,pauseFilter,timeFilter,sortMode,kindFilter,stateFilter};n.section=data().getInteger("Section");n.type=data().getInteger("SelectedType");n.task=data().getUniqueId("Selected");n.pool=data().hasUniqueId("Pool")?data().getUniqueId("Pool"):TaskConsole.NONE;return n;}
    private void clearFilters(){taskSort=0;taskDescending=false;taskSearch="";hostKey="";tierFilter=ruleFilter=sizeFilter=priceFilter=pauseFilter=timeFilter=sortMode=kindFilter=stateFilter=0;}
    private void navServer(UUID id){if(id==null||TaskConsole.NONE.equals(id)||pending||!data().getBoolean("Online"))return;navigation.push(capture());serverDetail=id;serverScroll=0;send(9,0,id);localTab(1);}
    private void navTask(UUID id){if(taskDraftOpen||id==null||TaskConsole.NONE.equals(id)||pending||!data().getBoolean("Online"))return;navigation.push(capture());clearFilters();clearTaskContext();expectedTask=id;detailsOpen=true;taskScroll=0;send(6,0,id);localTab(2);}
    private void navPool(UUID id){if(id==null||pending||!data().getBoolean("Online"))return;navigation.push(capture());send(8,0,id);tabScroll[3]=0;localTab(3);scroll=0;requestNow();}
    private void navEvent(UUID id){if(id==null||TaskConsole.NONE.equals(id)||pending)return;navigation.push(capture());eventSearch=eventCategory=eventLevel="";eventOldest=false;eventDetail=id;send(11,0,id);tabScroll[7]=0;localTab(7);}
    private void navOverview(int dest,boolean errors){if(pending)return;navigation.push(capture());if(dest==1){serverSearch=serverPool="";powerFilter=0;serverDetail=null;}
        if(dest==2&&!taskDraftOpen){clearFilters();clearTaskContext();detailsOpen=false;taskScroll=0;if(data().getBoolean("Online"))RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),7,0,TaskConsole.NONE,"-1"));}
        if(dest==3)send(8,0,TaskConsole.NONE);
        if(dest==7){eventSearch=eventCategory="";eventLevel=errors?"ERRORS":"";eventOldest=false;eventDetail=null;}
        tabScroll[dest]=0;localTab(dest);if(dest==1)send(5,0,TaskConsole.NONE);if(dest==7)send(10,0,TaskConsole.NONE);
    }
    private void goBack(){if(pending||taskDraftOpen)return;NavState n=navigation.pop();if(n==null)return;focus=0;editing=null;poolPicker=popup=-1;confirm=-1;eventDetail=n.event;eventSearch=n.eventSearch;eventCategory=n.eventCategory;eventLevel=n.eventLevel;eventOldest=n.oldest;serverSearch=n.serverSearch;serverPool=n.serverPool;powerFilter=n.powerFilter;serverSort=n.serverSort;serverDescending=n.serverDesc;serverDetail=n.server;serverScroll=n.serverScroll;taskScroll=n.taskScroll;detailScroll=n.detailScroll;detailsOpen=n.details;taskSort=n.taskSort;taskDescending=n.taskDescending;taskSearch=n.search;hostKey=n.host;int[] f=n.filters;tierFilter=f[0];ruleFilter=f[1];sizeFilter=f[2];priceFilter=f[3];pauseFilter=f[4];timeFilter=f[5];sortMode=f[6];kindFilter=f[7];stateFilter=f[8];expectedTask=null;
        if(n.tab==2){expectedTask=n.task;RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),7,n.section,n.task,Integer.toString(n.type)));}
        else if(n.tab==1&&n.server!=null)send(9,0,n.server);else if(n.tab==3)send(8,0,n.pool);
        localTab(n.tab);scroll=n.scroll;requestNow();if(n.tab==1&&n.server==null)send(5,0,TaskConsole.NONE);if(n.tab==7)send(10,0,n.event==null?TaskConsole.NONE:n.event);
    }
    private int lx(int x){return (int)Math.floor((x-originX)/scale);}private int ly(int y){return (int)Math.floor((y-originY)/scale);}
    private void rect(int x,int y,int w,int h,int color){DataOSWidgets.rect(x,y,w,h,color);}
    private void text(String s,int x,int y,int max,int c){widgets.text(s,x,y,max,c);}
    private void center(String s,int x,int y,int w,int c){widgets.center(s,x,y,w,c);}
    private void button(String s,int x,int y,int w,int h,boolean selected,boolean hover){widgets.button(s,x,y,w,h,selected,true);}
    private void scissor(int x,int y,int w,int h){double sx=mc.displayWidth/(double)width,sy=mc.displayHeight/(double)height;int l=(int)Math.ceil((originX+x*scale)*sx),r=(int)Math.floor((originX+(x+w)*scale)*sx),b=(int)Math.ceil(mc.displayHeight-(originY+(y+h)*scale)*sy),t=(int)Math.floor(mc.displayHeight-(originY+y*scale)*sy);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(l,b,Math.max(0,r-l),Math.max(0,t-b));}
    private NBTTagCompound row(int index){NBTTagCompound d=data();if(!ready())return null;int local=index-d.getInteger("Start");NBTTagList rows=d.getTagList("Rows",10);return local>=0&&local<rows.tagCount()?rows.getCompoundTagAt(local):null;}
    private static String num(long n){return Long.toString(n);}
    private static String money(long n){return String.format(Locale.ROOT,"%.3f",n/1000d);}
    private String pair(long a,long b){return num(a)+" / "+num(b);}
    private String ids(NBTTagCompound r){int[] a=r.getIntArray("Jobs");if(a.length==0)return "—";StringBuilder s=new StringBuilder();for(int id:a){if(s.length()>0)s.append(", ");s.append(id);}return s.toString();}
    private String[] headers(){return new String[]{tr("Name","Название"),"ID","CPU","GPU","RAM",tr("Disk","Диск"),tr("Tasks","Задачи"),tr("Pool","Пул"),tr("Power","Питание")};}
    @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();layout();if(!menu.terminalPowered()){wasPowered=false;dragging=listDrag=editorScrollbarDrag=false;modalDrag=0;editDrag=null;confirm=-1;focus=0;input=0;popup=poolPicker=-1;DataOSWidgets.powerOff(fontRenderer,originX,originY,Math.round(W*scale),Math.round(h*scale));return;}if(!wasPowered){wasPowered=true;requestNow();}rendered=menu.view;if(tab==1&&serverDetail!=null&&rendered.getInteger("Tab")==1&&rendered.hasUniqueId("DetailRequest")&&serverDetail.equals(rendered.getUniqueId("DetailRequest"))&&!rendered.hasKey("ServerDetail")){serverDetail=null;editing=null;}if(ready()&&scroll>maxScroll())setScroll(maxScroll());int x=lx(mx),y=ly(my);widgets=new DataOSWidgets(fontRenderer,x,y);GlStateManager.pushMatrix();GlStateManager.translate(originX,originY,0);GlStateManager.scale(scale,scale,1);
        rect(0,0,W,h,0xff63788d);rect(1,1,W-2,h-2,0xffe7ebef);rect(3,3,W-6,h-6,0xfff2f4f6);rect(4,4,W-8,19,0xff3269a8);text("DataOS",11,10,100,0xffffffff);if(navigation.available())button(tr("< Back","< Назад"),92,6,74,15,false,false);text(data().getBoolean("Online")?"ONLINE":"OFFLINE",W-62,10,56,0xffffffff);
        String[] names=tabs();for(int i=0;i<names.length;i++){int tx=DataOSUiState.TAB_LEFT+i*DataOSUiState.TAB_STEP;button(names[i],tx,27,DataOSUiState.TAB_STEP-1,17,DataOSUiState.tabId(i)==tab,DataOSUiState.tabAt(x,y)==DataOSUiState.tabId(i));}
        rect(5,TOP,W-10,viewHeight,WHITE);rect(5,TOP,W-10,1,EDGE);rect(5,TOP+viewHeight-1,W-10,1,EDGE);
        if(tab==0&&ready()){scissor(6,TOP+1,452,viewHeight-2);overview(TOP-scroll);GL11.glDisable(GL11.GL_SCISSOR_TEST);}
        else if(tab==1){serverControls();serverHeader(SERVER_TOP);scissor(6,SERVER_TOP+HEADER,452,viewHeight-HEADER-23);int first=scroll/ROW;for(int i=first;i<count()&&SERVER_TOP+HEADER+i*ROW-scroll<TOP+viewHeight;i++){int yy=SERVER_TOP+HEADER+i*ROW-scroll;serverRow(i,yy,x,y);}GL11.glDisable(GL11.GL_SCISSOR_TEST);if(!ready())text(tr("Loading...","Загрузка..."),12,SERVER_TOP+HEADER+8,420,MUTED);}
        else if(tab==5&&ready()){scissor(6,TOP+1,452,viewHeight-2);powerPanel();GL11.glDisable(GL11.GL_SCISSOR_TEST);}else if(tab==6&&ready()){scissor(6,TOP+1,452,viewHeight-2);walletPanel(TOP-scroll);GL11.glDisable(GL11.GL_SCISSOR_TEST);}else if(tab==7){logPanel();}else if(tab==2 && data().getInteger("Tab")==2){taskPanel();}else if(tab==3&&data().getInteger("Tab")==3){resourcePanel();}else{text(tr("Loading...","Загрузка..."),14,TOP+15,430,MUTED);}
        if(tab==0||tab==1||tab==3||tab==5||tab==6||tab==7)scrollbar();if(tab==7&&eventDetail!=null)eventWindow();if(tab==1&&serverDetail!=null)serverWindow();if(poolPicker>=0)poolWindow();if(popup>=0)drawPopup();if(tab==2&&confirm>=0&&!pending&&!data().getBoolean("Assign"))confirmWindow();GlStateManager.popMatrix();drawnScroll=scroll;drawnTaskScroll=taskScroll;drawnDetailScroll=detailScroll;drawnServerScroll=serverScroll;
        if(tab==1&&serverDetail==null&&popup<0&&poolPicker<0&&y>=SERVER_TOP+HEADER&&y<TOP+viewHeight&&x>=6&&x<456){NBTTagCompound r=row((y-SERVER_TOP-HEADER+scroll)/ROW);if(r!=null){List<String> tip=new ArrayList<>();if(x<6+COL[1])tip.add(r.getString("Name"));else if(x>=6+COL[7]&&x<6+COL[8])tip.add(r.getString("Pool"));else if(x>=6+COL[6]&&x<6+COL[7]&&r.getIntArray("Jobs").length>0){tip.add(tr("Running task IDs","ID запущенных задач"));String a=ids(r);while(a.length()>0){int n=Math.min(a.length(),55);tip.add(a.substring(0,n));a=a.substring(n);}}else if(x>=6+COL[8])tip.add(r.getBoolean("Editable")?powerLabel(r.getBoolean("Powered")?1:r.getBoolean("Enabled")?3:2):tr("Read-only","Только просмотр"));if(!tip.isEmpty())drawHoveringText(tip,mx,my);}}
        if((tab==0||tab==3)&&ready()&&poolPicker<0&&popup<0&&x>=11&&x<451&&y>=TOP&&y<TOP+viewHeight){int local=y-TOP+scroll-(tab==0?115:45);if(local>=0&&local<7*17){int k=local/17;NBTTagCompound d=data();List<String> tip=Arrays.asList(resourceLabels()[k],tr("Total: ","Всего: ")+d.getLong("T"+k),tr("Reserved: ","Зарезервировано: ")+d.getLong("U"+k),tr("Reserved on unavailable hosts: ","Резервы на недоступных серверах: ")+d.getLong("H"+k),tr("Free: ","Свободно: ")+d.getLong("F"+k),tr("Unavailable (unreserved): ","Недоступно (без резерва): ")+d.getLong("D"+k));drawHoveringText(tip,mx,my);}}
        if(tab==6&&ready()&&x>=11&&x<451&&y>=TOP&&y<TOP+viewHeight&&y-TOP+scroll>=WALLET_FIXED){NBTTagCompound e=row((y-TOP+scroll-WALLET_FIXED)/ROW);if(e!=null){List<String> tip=new ArrayList<>();tip.add(eventMessage(e));if(e.hasKey("JobType"))tip.add(e.getString("Label")+" / "+title(e.getInteger("JobType")));if(e.hasKey("Company"))tip.add(e.getString("Company"));if("debt".equals(e.getString("Code")))tip.add(tr("Debt incurred; not a wallet debit.","Начислен долг, не списание с кошелька."));if(e.getString("Message").contains("Debt repaid"))tip.add(tr("Earnings used to repay debt, not credited.","Доход направлен на погашение долга, не зачислен."));drawHoveringText(tip,mx,my);}}
        if(taskDraftOpen&&tab!=2&&DataOSUiState.tabAt(x,y)==2)drawHoveringText(Collections.singletonList(tr("Resource draft preserved; finish or cancel it before opening another task.","Черновик ресурсов сохранён; завершите или отмените его перед переходом к другой задаче.")),mx,my);
    }
    private String eventTime(long sec){return String.format(Locale.ROOT,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    private int eventColor(String l){switch(l){case "SUCCESS":return 0xff337745;case "WARNING":return 0xff906020;case "ERROR":return 0xffb13e38;case "CRITICAL":return 0xff941d26;default:return BLUE;}}
    private String eventMessage(NBTTagCompound r){String s=r.getString("Message");int coin=r.getInteger("Currency");if(coin>=0&&coin<TaskData.COINS.length)s+="  "+(r.getLong("Amount")>0?"+":"")+money(r.getLong("Amount"))+" "+TaskData.COINS[coin];return s;}
    private void logPanel(){widgets.field(eventSearch,tr("Search events...","Поиск событий..."),7,TOP+3,148,16,focus==3);button((eventCategory.isEmpty()?tr("All categories","Все категории"):eventCategory)+" v",158,TOP+3,116,16,false,false);button((eventLevel.isEmpty()?tr("All levels","Все уровни"):eventLevel)+" v",277,TOP+3,98,16,false,false);button(eventOldest?"Time ^":"Time v",378,TOP+3,56,16,false,false);button("X",437,TOP+3,19,16,false,false);
        widgets.header(6,LOG_TOP,450,HEADER);String[] head={tr("Time","Время"),tr("Level","Уровень"),tr("Category","Категория"),tr("Source","Источник"),tr("Description","Описание"),"x"};int[] cols={0,58,116,180,255,426,450};for(int i=0;i<head.length;i++)text(head[i],9+cols[i],LOG_TOP+6,cols[i+1]-cols[i]-5,FG);
        if(!ready()){text(tr("Loading...","Загрузка..."),12,LOG_TOP+HEADER+8,420,MUTED);return;}if(count()==0){text(tr("No matching events","Нет подходящих событий"),12,LOG_TOP+HEADER+8,420,MUTED);return;}
        scissor(6,LOG_TOP+HEADER,452,visibleHeight());for(int i=scroll/ROW;i<count()&&LOG_TOP+HEADER+i*ROW-scroll<TOP+viewHeight;i++){int yy=LOG_TOP+HEADER+i*ROW-scroll;NBTTagCompound e=row(i);if(e==null)continue;widgets.row(i,6,yy,450,ROW-1,eventDetail!=null&&eventDetail.equals(e.getUniqueId("Key")));String l=e.getString("Level");String[] vals={eventTime(e.getLong("Last")),l,e.getString("Category"),e.getString("Label"),eventMessage(e),Integer.toString(e.getInteger("Repeats"))};for(int j=0;j<vals.length;j++)text(vals[j],9+cols[j],yy+6,cols[j+1]-cols[j]-5,j==1?eventColor(l):FG);}GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    private void logClick(int x,int y){if(DataOSUiState.hit(x,y,7,TOP+3,148,16)){focus=3;return;}focus=0;if(DataOSUiState.hit(x,y,158,TOP+3,116,16)||DataOSUiState.hit(x,y,277,TOP+3,98,16)){popup=x<275?2:3;popupScroll=0;popupItems=new ArrayList<>();popupItems.add(tr("All","Все"));if(popup==2)for(EventJournal.Category c:EventJournal.Category.values())popupItems.add(c.name());else{for(EventJournal.Level l:EventJournal.Level.values())popupItems.add(l.name());popupItems.add("ERRORS");}return;}if(DataOSUiState.hit(x,y,378,TOP+3,56,16)||DataOSUiState.hit(x,y,6,LOG_TOP,58,HEADER)){eventOldest=!eventOldest;queryChanged();return;}if(DataOSUiState.hit(x,y,437,TOP+3,19,16)){eventSearch=eventCategory=eventLevel="";eventOldest=false;queryChanged();return;}if(ready()&&scroll==drawnScroll&&DataOSUiState.hit(x,y,6,LOG_TOP+HEADER,450,visibleHeight())){NBTTagCompound e=row((y-LOG_TOP-HEADER+scroll)/ROW);if(e!=null){eventDetail=e.getUniqueId("Key");send(10,0,eventDetail);}}}
    private void closeEvent(){eventDetail=null;send(10,0,TaskConsole.NONE);}
    private NBTTagCompound currentEvent(){NBTTagCompound e=data().getCompoundTag("EventDetail");return eventDetail!=null&&eventDetail.equals(e.getUniqueId("Key"))?e:null;}
    private void eventWindow(){int x=30,y=TOP+3,w=414,hh=Math.min(190,viewHeight-6);rect(5,TOP,W-10,viewHeight,0x55000000);widgets.modal(x,y,w,hh,tr("Event details","Подробности события"));button("X",x+w-24,y+3,20,14,false,false);NBTTagCompound e=currentEvent();if(e==null){text(tr("Loading or event expired","Загрузка или запись удалена"),x+8,y+27,w-16,MUTED);return;}text(e.getString("Level")+" / "+e.getString("Category")+" / "+e.getString("Code"),x+8,y+25,w-16,eventColor(e.getString("Level")));text(tr("First: ","Первое: ")+eventTime(e.getLong("First"))+"    "+tr("Last: ","Последнее: ")+eventTime(e.getLong("Last"))+"    x"+e.getInteger("Repeats"),x+8,y+38,w-16,FG);text(tr("Source: ","Источник: ")+e.getString("Target")+" / "+e.getString("Label"),x+8,y+51,w-16,FG);if(e.hasUniqueId("Source"))text(e.getUniqueId("Source").toString(),x+8,y+64,w-16,MUTED);text("Event: "+e.getUniqueId("Key"),x+8,y+77,w-16,MUTED);fontRenderer.drawSplitString(eventMessage(e),x+8,y+91,w-16,FG);widgets.button(tr("Open source","Открыть источник"),x+8,y+hh-24,w-16,18,false,e.getBoolean("Link")&&data().getBoolean("Online")&&!(taskDraftOpen&&"TASK".equals(e.getString("Target"))));}
    private void eventWindowClick(int x,int y){int top=TOP+3,hh=Math.min(190,viewHeight-6);if(DataOSUiState.hit(x,y,420,top+3,24,15)){closeEvent();return;}NBTTagCompound e=currentEvent();if(e!=null&&e.getBoolean("Link")&&DataOSUiState.hit(x,y,38,top+hh-24,398,18)){if("SERVER".equals(e.getString("Target")))navServer(e.getUniqueId("Source"));else if("TASK".equals(e.getString("Target")))navTask(e.getUniqueId("Source"));}}
    private void section(String title,int y){rect(11,y,440,17,0xffdce7f1);text(title,16,y+5,430,BLUE);}
    private static final int RESERVED_COLOR=0xff5795c9,FREE_COLOR=0xff94bd89,UNAVAILABLE_COLOR=0xff9aa3ad;
    private String[] resourceLabels(){return new String[]{tr("CPU cores","Ядра CPU"),tr("GPU chips","GPU-чипы"),"RAM (GB)","M.2/NVMe (GB)","SATA SSD (GB)","HDD (GB)",tr("NIC (Mb/s)","NIC (Мбит/с)")};}
    private void resourceBar(long u,long f,long unavailable,long t,int x,int y,int w){int inner=w-2;rect(x,y,w,10,EDGE);rect(x+1,y+1,inner,8,0xffd7dfe7);if(t<=0)return;int a=(int)(inner*u/(double)t),b=(int)(inner*(u+f)/(double)t);a=DataOSUiState.clamp(a,0,inner);b=DataOSUiState.clamp(b,a,inner);rect(x+1,y+1,a,8,RESERVED_COLOR);rect(x+1+a,y+1,b-a,8,FREE_COLOR);rect(x+1+b,y+1,inner-b,8,UNAVAILABLE_COLOR);}
    private void statsTable(NBTTagCompound d,int y){String[] labels=resourceLabels();widgets.header(11,y,440,17);String[] cols={tr("Resource","Ресурс"),tr("Total","Всего"),tr("Held","Занято"),tr("Free","Свободно"),tr("Unavailable","Недоступно")};int[] xx={16,133,191,249,305};int[] ww={114,54,54,56,72};for(int i=0;i<cols.length;i++)text(cols[i],xx[i],y+5,ww[i],BLUE);for(int i=0;i<7;i++){int yy=y+17+i*17;widgets.row(i,11,yy,440,16,false);long t=d.getLong("T"+i),u=d.getLong("U"+i),f=d.getLong("F"+i),z=d.getLong("D"+i);text(labels[i],16,yy+4,114,FG);text(num(t),133,yy+4,54,FG);text(num(u),191,yy+4,54,BLUE);text(num(f),249,yy+4,56,0xff397846);text(num(z),305,yy+4,72,MUTED);resourceBar(u,f,z,t,380,yy+3,65);}}
    private void statsLegend(int y){text(tr("Held includes offline reservations and retained data.","Занято: резервы, включая отключённые серверы и данные."),16,y,430,MUTED);text(tr("NIC free bandwidth shares switch/WAN limits between pools.","Свободная сеть ограничена общими WAN и коммутаторами."),16,y+12,430,MUTED);}
    private void stat(String label,int n,int x,int y,int color){text(label,x,y,99,MUTED);String value=num(n);text(value,x+134-fontRenderer.getStringWidth(value),y,134,color);}
    private void financeSummary(NBTTagCompound d,int y){widgets.header(11,y,440,17);text(tr("Currency","Валюта"),16,y+5,120,BLUE);text(tr("Balance","Баланс"),143,y+5,135,BLUE);text(tr("Gross estimate / h","Прогноз / ч"),282,y+5,164,BLUE);for(int i=0;i<2;i++){int yy=y+17+i*17;widgets.row(i,11,yy,440,16,false);text(TaskData.COINS[i],16,yy+4,120,FG);text(money(d.getLong("Balance"+i)),143,yy+4,135,FG);text(money(d.getLong("Hourly"+i)),282,yy+4,164,BLUE);}}
    private void overview(int y){NBTTagCompound d=data();section(tr("Statistics","Статистика"),y+5);
        int[] xs={12,159,306};
        for(int x:xs)rect(x,y+25,141,45,0xffedf2f7);
        text(tr("Servers","Серверы"),xs[0]+5,y+28,130,BLUE);
        stat(tr("Total","Всего"),d.getInteger("Servers"),xs[0]+4,y+40,FG);
        stat(tr("Powered","Включено"),d.getInteger("Powered"),xs[0]+4,y+51,0xff397846);
        text(tr("Tasks","Задачи"),xs[1]+5,y+28,130,BLUE);
        stat(tr("Total","Всего"),d.getInteger("Tasks"),xs[1]+4,y+40,FG);
        stat(tr("Running","Работает"),d.getInteger("Running"),xs[1]+4,y+51,0xff397846);
        text(tr("Waiting","Ожидает")+": "+Math.max(0,d.getInteger("Active")-d.getInteger("Running"))+" | "+tr("Paused","Пауза")+": "+d.getInteger("Paused"),xs[1]+4,y+62,134,MUTED);
        text(tr("Errors","Ошибки"),xs[2]+5,y+28,130,BLUE);
        stat(tr("Total","Всего"),d.getInteger("Errors")+d.getInteger("Critical"),xs[2]+4,y+40,0xffa13b35);
        stat(tr("Critical","Критические"),d.getInteger("Critical"),xs[2]+4,y+51,0xffa13b35);
        section(tr("Resource allocation","Загрузка ресурсов"),y+78);statsTable(d,y+98);statsLegend(y+236);text(tr("WAN active: ","WAN занято: ")+pair(d.getLong("Traffic"),d.getLong("WAN"))+" Mb/s",16,y+263,178,FG);resourceBar(Math.min(d.getLong("Traffic"),d.getLong("WAN")),Math.max(0,d.getLong("WAN")-d.getLong("Traffic")),0,d.getLong("WAN"),196,y+262,49);text(tr("Power: ","Питание: ")+num(d.getLong("PowerDraw"))+" W",255,y+263,190,FG);
        section(tr("Income and balance","Доход и баланс"),y+279);financeSummary(d,y+299);
        section(tr("Latest events","Последние события"),y+354);button(tr("Open log >","Журнал >"),348,y+354,100,17,false,false);NBTTagList recent=d.getTagList("Recent",10);for(int i=0;i<recent.tagCount();i++){NBTTagCompound e=recent.getCompoundTagAt(i);int yy=y+374+i*17;widgets.row(i,11,yy,440,16,false);text(eventTime(e.getLong("Last")),16,yy+4,58,MUTED);text(e.getString("Level"),80,yy+4,65,eventColor(e.getString("Level")));text(eventMessage(e)+"  >",148,yy+4,295,FG);}if(recent.tagCount()==0)text(tr("No events","Нет событий"),16,y+379,430,MUTED);
        section(tr("Active and paused tasks","Активные и приостановленные задачи"),y+OVERVIEW_FIXED);int base=y+OVERVIEW_FIXED+HEADER;int first=Math.max(0,(TOP-base)/ROW);for(int i=first;i<count()&&base+i*ROW<TOP+viewHeight;i++){int yy=base+i*ROW;NBTTagCompound r=row(i);widgets.row(i,11,yy,440,ROW-1,false);if(r==null)continue;text("#"+r.getInteger("Id"),16,yy+6,38,FG);text(title(r.getInteger("Type")),60,yy+6,220,BLUE);text(r.getBoolean("Enabled")?(r.getInteger("Work")>0?tr("Running","Работает"):tr("Waiting","Ожидает")):tr("Paused","Пауза"),290,yy+6,150,MUTED);}
    }
    private void walletPanel(int y){NBTTagCompound d=data();section(tr("Current wallet (read-only)","Текущий кошелёк (просмотр)"),y+5);financeSummary(d,y+25);text(tr("Recurring gross forecast; history is limited to retained events.","Валовой прогноз регулярного дохода; история ограничена журналом."),16,y+80,430,MUTED);section(tr("Recent payments, fees and penalties","Последние выплаты, комиссии и штрафы"),y+93);int base=y+WALLET_FIXED;for(int i=Math.max(0,(TOP-base)/ROW);i<count()&&base+i*ROW<TOP+viewHeight;i++){int yy=base+i*ROW;NBTTagCompound e=row(i);widgets.row(i,11,yy,440,ROW-1,false);if(e==null)continue;long amount=e.getLong("Amount");int coin=e.getInteger("Currency");String code=e.getString("Code");boolean debt="debt".equals(code)||e.getString("Message").contains("Debt repaid");text(eventTime(e.getLong("Last")),16,yy+6,58,MUTED);String message=e.getString("Message");if(e.hasKey("JobType"))message=message+" / "+title(e.getInteger("JobType"));text(message+"  >",80,yy+6,218,BLUE);String a=coin>=0&&coin<TaskData.COINS.length?(amount>0?"+":"")+money(amount)+" "+TaskData.COINS[coin]:tr("Unavailable","Нет данных");text(a,302,yy+6,144,debt?MUTED:amount<0?0xffa13b35:0xff397846);}if(count()==0)text(tr("No financial events retained","Нет финансовых событий в журнале"),16,base+5,430,MUTED);}
    private String watts(long n){
        if(n<10000)return n+" W";
        if(n<1000000)return String.format(Locale.ROOT,"%.2f kW",n/1000d);
        if(n<1000000000)return String.format(Locale.ROOT,"%.2f MW",n/1000000d);
        return String.format(Locale.ROOT,"%.2f GW",n/1000000000d);
    }
    private String gridState(int state){return state==GridProtection.TRIPPED?tr("TRIPPED","АВАРИЯ"):state==GridProtection.OFF?tr("OFF","ВЫКЛ"):tr("ON","ВКЛ");}
    private void powerMetric(String label,String value,int x,int y,int color){
        rect(x,y,143,34,EDGE);rect(x+1,y+1,141,32,WHITE);rect(x+1,y+1,141,12,0xffdce7f1);
        text(label,x+5,y+3,133,BLUE);center(value,x+3,y+19,137,color);
    }
    private void powerPanel(){
        NBTTagCompound d=data();int y=TOP-scroll,count=d.getInteger("GridCount"),index=d.getInteger("GridIndex");
        if(count==0){section(tr("Electrical grids","Энергосети"),y+5);text(tr("No verified electrical grids","Нет обнаруженных энергосетей"),16,y+30,430,MUTED);return;}
        widgets.button("<",11,y+5,24,18,false,index>0);widgets.button(">",427,y+5,24,18,false,index+1<count);
        center(tr("Grid ","Сеть ")+(index+1)+" / "+count+"  |  "+d.getString("GridPos"),39,y+10,384,BLUE);
        boolean bypass=d.getBoolean("Bypass"),sample=d.getBoolean("Sample"),known=sample&&d.getBoolean("Complete")&&!bypass;
        String missing="—";long available=d.getLong("Available"),draw=d.getLong("Draw"),free=d.getLong("Free");
        powerMetric(tr("Confirmed supply","Подтвержд. питание"),known?watts(available):missing,11,y+30,BLUE);
        powerMetric(tr("Actual draw","Потребление"),sample?watts(draw):missing,159,y+30,0xff397846);
        powerMetric(tr("Free confirmed","Свободно (подтв.)"),known?watts(free):missing,307,y+30,FG);
        int state=d.getInteger("State");int statusColor=state==GridProtection.TRIPPED?0xffb13e38:state==GridProtection.OFF?MUTED:0xff397846;
        text(gridState(state),13,y+72,76,statusColor);
        text(tr("Installed rating: ","Номинал источников: ")+watts(d.getLong("Rated")),92,y+72,356,FG);
        String status=bypass?tr("Power requirement disabled; no supply limit","Проверка питания отключена; лимита нет"):!d.getBoolean("Complete")?tr("Partial grid: totals are not verified","Часть сети выгружена: итог не подтверждён"):!sample?tr("Waiting for simulation sample","Ожидание данных симуляции"):state==GridProtection.TRIPPED?tr("Insufficient power — reset at a source","Нехватка питания — сброс на источнике"):state==GridProtection.OFF?tr("Grid switched off","Энергосеть выключена"):!d.getBoolean("Funded")?tr("Waiting for external energy","Ожидание внешней энергии"):tr("External supply is confirmed for received energy only","Внешняя мощность — только фактически полученная");
        text(status,13,y+85,436,state==GridProtection.TRIPPED?statusColor:MUTED);
        section(tr("Power history — last 10 min","История питания — последние 10 мин"),y+100);
        drawPowerGraph(d,11,y+120,440,75);
        rect(14,y+200,9,3,0xff397846);text(tr("Consumption","Потребление"),27,y+197,145,FG);
        rect(181,y+200,9,3,BLUE);text(tr("Confirmed supply","Подтвержд. питание"),194,y+197,248,FG);
        section(tr("Consumption by equipment","Потребление по оборудованию"),y+213);
        String[] labels={tr("Servers","Серверы"),tr("Switches","Коммутаторы"),tr("Network terminals","Сетевые терминалы"),tr("Antennas / towers","Антенны / вышки"),"DataOS",tr("Other / unclassified","Прочее / не уточнено")};
        for(int i=0;i<labels.length;i++){
            int x=14+(i/3)*220,yy=y+234+(i%3)*12;
            text(labels[i],x,yy,144,MUTED);text(sample?watts(d.getLong("C"+i)):missing,x+145,yy,71,FG);
        }
        section(tr("Sources — rating / confirmed","Источники — номинал / подтверждено"),y+270);
        int start=y+POWER_FIXED;
        if(count()==0)text(tr("No connected sources","Нет подключённых источников"),16,start+8,430,MUTED);
        for(int i=Math.max(0,(TOP-start)/POWER_ROW);i<count()&&start+i*POWER_ROW<TOP+viewHeight;i++){
            int yy=start+i*POWER_ROW;NBTTagCompound r=row(i);widgets.row(i,11,yy,440,POWER_ROW-1,false);if(r==null)continue;
            boolean external=r.getBoolean("External");String name=external?tr("Transformer T","Трансформатор T")+r.getInteger("Tier"):tr("Creative battery","Творческая батарея");
            text(name+(r.getBoolean("Virtual")?tr(" (saved)"," (снимок)"):""),16,yy+4,192,FG);
            text(r.getString("Pos"),211,yy+4,134,MUTED);
            text(gridState(r.getInteger("State")),350,yy+4,96,r.getInteger("State")==GridProtection.TRIPPED?0xffb13e38:FG);
            text(watts(r.getLong("Rated"))+" / "+(sample?watts(r.getLong("Received")):missing),16,yy+17,192,BLUE);
            text(external?"HE "+r.getLong("HE")+" / FE "+r.getLong("FE"):tr("Infinite creative source","Бесконечный источник"),211,yy+17,234,MUTED);
        }
    }
    private void drawPowerGraph(NBTTagCompound d,int x,int y,int w,int h){
        NBTTagList points=d.getTagList("History",10);long max=1;
        for(int i=0;i<points.tagCount();i++){NBTTagCompound p=points.getCompoundTagAt(i);if(p.getBoolean("Known"))max=Math.max(max,Math.max(p.getLong("Available"),p.getLong("Draw")));}
        rect(x,y,w,h,EDGE);rect(x+1,y+1,w-2,h-2,0xfff4f7fa);
        int left=x+62,right=x+w-5,top=y+5,bottom=y+h-13;
        for(int i=0;i<=3;i++){int yy=top+(bottom-top)*i/3;rect(left,yy,right-left+1,1,0xffdbe4ec);text(watts(max*(3-i)/3),x+4,Math.min(yy,bottom-6),55,MUTED);}
        text("-10m",left,bottom+4,40,MUTED);text("0",right-8,bottom+4,10,MUTED);
        if(points.tagCount()==0){center(tr("Collecting history...","Накопление истории..."),left,top+18,right-left,MUTED);return;}
        net.minecraft.client.renderer.Tessellator tess=net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder buf=tess.getBuffer();
        GlStateManager.disableTexture2D();
        for(int series=0;series<2;series++){
            String key=series==0?"Available":"Draw";int r=series==0?36:57,g=series==0?87:120,b=series==0?144:70;
            buf.begin(GL11.GL_LINES,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            int px=-1,py=0,age=-1;
            for(int i=0;i<points.tagCount();i++){
                NBTTagCompound p=points.getCompoundTagAt(i);int a=p.getInteger("Age");
                if(!p.getBoolean("Known")||a<0||a>PowerStats.WINDOW){px=-1;continue;}
                int xx=right-(int)((right-left)*(long)a/PowerStats.WINDOW),yy=bottom-(int)((bottom-top)*(p.getLong(key)/(double)max));
                if(px>=0&&age-a<=PowerStats.INTERVAL*2){buf.pos(px,py,0).color(r,g,b,255).endVertex();buf.pos(xx,yy,0).color(r,g,b,255).endVertex();}
                else{buf.pos(xx,yy,0).color(r,g,b,255).endVertex();buf.pos(Math.min(right,xx+1),yy,0).color(r,g,b,255).endVertex();}
                px=xx;py=yy;age=a;
            }
            tess.draw();
        }
        GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
    }
    private void powerClick(int x,int y){
        if(!ready()||y<TOP||y>=TOP+viewHeight||!data().hasUniqueId("GridKey"))return;
        int local=y-TOP+scroll,index=data().getInteger("GridIndex"),count=data().getInteger("GridCount");
        if(local>=5&&local<23&&((x>=11&&x<35&&index>0)||(x>=427&&x<451&&index+1<count))){
            send(12,x<35?-1:1,data().getUniqueId("GridKey"));scroll=0;tabScroll[5]=0;requestNow();
        }
    }
    private void overviewClick(int x,int y){if(!ready()||scroll!=drawnScroll||!DataOSUiState.hit(x,y,11,TOP,440,viewHeight))return;int local=y-TOP+scroll;if(local>=354&&local<371&&x>=348){navOverview(7,false);return;}if(local>=374&&local<459){int i=(local-374)/17;NBTTagList recent=data().getTagList("Recent",10);if(i<recent.tagCount())navEvent(recent.getCompoundTagAt(i).getUniqueId("Key"));return;}if(local>=OVERVIEW_FIXED+HEADER){NBTTagCompound r=row((local-OVERVIEW_FIXED-HEADER)/ROW);if(r!=null)navTask(r.getUniqueId("Key"));}}
    private String powerLabel(int n){return new String[]{tr("All power states","Все состояния"),tr("Powered","Работает"),tr("Off","Выключен"),tr("Waiting for power","Нет питания")}[n];}
    private void serverControls(){widgets.field(serverSearch,tr("Name / ID...","Название / ID..."),7,TOP+3,174,16,focus==1);button((serverPool.isEmpty()?tr("All pools","Все пулы"):serverPool)+" v",184,TOP+3,133,16,false,false);button(powerLabel(powerFilter)+" v",320,TOP+3,112,16,false,false);button("X",435,TOP+3,21,16,false,false);}
    private void queryChanged(){scroll=0;requestNow();}
    private void serverHeader(int y){widgets.header(6,y,450,HEADER);String[] names=headers();for(int i=0;i<names.length;i++){center(names[i]+(serverSort==i?(serverDescending?" v":" ^"):""),6+COL[i],y+6,COL[i+1]-COL[i],FG);if(i>0)rect(6+COL[i],y,1,HEADER,EDGE);}}
    private void serverRow(int i,int y,int mx,int my){widgets.row(i,6,y,450,ROW-1,false);NBTTagCompound r=row(i);if(r==null)return;String[] s={r.getString("Name"),num(r.getInteger("Id")),pair(r.getInteger("CPU"),r.getInteger("Cores")),pair(r.getInteger("GPU"),r.getInteger("GPUs")),pair(r.getInteger("RAM"),r.getInteger("RAMTotal")),pair(r.getInteger("Disk"),r.getInteger("DiskTotal")),ids(r)};for(int k=0;k<s.length;k++)center(s[k],6+COL[k],y+6,COL[k+1]-COL[k],FG);
        boolean edit=editing!=null&&editing.equals(r.getUniqueId("Key"));String group=edit?groupText+"_":r.getString("Pool");button(group,6+COL[7]+2,y+2,COL[8]-COL[7]-4,16,edit,false);boolean on=r.getBoolean("Enabled");int bx=6+COL[8]+2;widgets.button(on?tr("OFF","ВЫКЛ"):tr("ON","ВКЛ"),bx+10,y+2,34,16,false,r.getBoolean("Editable")&&data().getBoolean("Online"));circle(bx,y+7,r.getInteger("ThermalFault")!=0?0xffb13e38:r.getBoolean("Powered")?0xff42a852:0xff929ba2);}
    private NBTTagCompound detailServer(){if(serverDetail==null||data().getInteger("Tab")!=1)return null;NBTTagCompound r=data().getCompoundTag("ServerDetail");return serverDetail.equals(r.getUniqueId("Key"))?r:null;}
    private void commitServerEdit(){if(editing==null)return;RackInteraction.channel.sendToServer(new DataOSPackets.Request(menu.windowId,tab,Math.max(0,requested),editAction,0,editing,groupText));editing=null;}
    private void closeServer(){serverDetail=null;editing=null;send(5,0,TaskConsole.NONE);}
    private void openServer(UUID id){serverDetail=id;serverScroll=0;send(5,0,id);}
    private int serverWindowHeight(){return Math.min(230,viewHeight-6);}
    private final List<UUID> serverLineJobs=new ArrayList<>();private int serverLineCount,serverLineVisible;
    private void loadBar(String label,long used,long total,String unit,int x,int y,int w){text(label,x,y,90,FG);text(pair(used,total)+" "+unit,x+92,y,122,FG);int bw=Math.max(8,w-220);rect(x+214,y,bw,9,0xffd7dfe7);rect(x+215,y+1,(int)((bw-2)*Math.min(1,Math.max(0,used)/(double)Math.max(1,total))),7,0xff5795c9);}
    private void serverWindow(){NBTTagCompound r=detailServer();int x=30,y=TOP+3,w=414,hh=serverWindowHeight();rect(5,TOP,W-10,viewHeight,0x55000000);widgets.modal(x,y,w,hh,r==null?tr("Server","Сервер"):r.getString("Name")+"  #"+r.getInteger("Id"));button("X",x+w-24,y+3,20,14,false,false);if(r==null){text(tr("Loading or server unavailable","Загрузка или сервер недоступен"),x+8,y+27,w-16,MUTED);return;}
        widgets.button(tr("Rename","Имя"),x+8,y+22,65,16,false,r.getBoolean("Editable"));widgets.button(tr("Pool","Пул"),x+76,y+22,58,16,false,r.getBoolean("Editable"));button(tr("Pool: ","Пул: ")+r.getString("Pool")+" >",x+138,y+22,w-146,16,false,false);
        if(editing!=null&&editing.equals(serverDetail)){widgets.field(groupText,editAction==4?tr("Server name","Название сервера"):tr("Pool name","Название пула"),x+8,y+40,w-80,16,true);button("Enter",x+w-68,y+40,60,16,false,false);}else text(tr("Tasks: ","Задачи: ")+ids(r),x+8,y+44,w-16,FG);
        int[] a=r.getIntArray("DiskUsed"),b=r.getIntArray("DiskTypes");if(a.length!=3||b.length!=3){a=new int[3];b=new int[3];}
        List<String> labels=new ArrayList<>(Arrays.asList("CPU","GPU","RAM","HDD","SSD","M.2",tr("Network","Сеть")));List<Long> used=new ArrayList<>(Arrays.asList((long)r.getInteger("CPU"),(long)r.getInteger("GPU"),(long)r.getInteger("RAM"),(long)a[2],(long)a[1],(long)a[0],(long)r.getInteger("Net")));List<Long> total=new ArrayList<>(Arrays.asList((long)r.getInteger("Cores"),(long)r.getInteger("GPUs"),(long)r.getInteger("RAMTotal"),(long)b[2],(long)b[1],(long)b[0],(long)r.getInteger("NetTotal")));List<String> units=new ArrayList<>(Arrays.asList(tr("cores","ядер"),"","GB","GB","GB","GB","Mb/s"));
        int[] parts=r.getIntArray("Parts");if(parts.length==ServerSpec.COUNT){BitSet cores=BitSet.valueOf(r.getByteArray("CoreMask"));int offset=0;for(int slot:new int[]{ServerSpec.CPU,ServerSpec.CPU2}){Part part=Part.at(parts[slot]);if(part.cores==0)continue;labels.add("CPU "+(slot==ServerSpec.CPU?1:2)+" "+part.name);used.add((long)cores.get(offset,offset+part.cores).cardinality());total.add((long)part.cores);units.add(tr("cores","ядер"));offset+=part.cores;}int gpu=0;for(int slot=ServerSpec.PCIE;slot<ServerSpec.PSU;slot++){Part part=Part.at(parts[slot]);for(int k=0;k<part.chips;k++,gpu++){labels.add("GPU "+gpu+" "+part.name);used.add((r.getInteger("GpuMask")&(1<<gpu))!=0?1L:0L);total.add(1L);units.add(part.gb+" GB VRAM");}}}
        serverLineJobs.clear();for(int i=0;i<labels.size();i++)serverLineJobs.add(null);NBTTagList links=r.getTagList("JobLinks",10);for(int i=0;i<links.tagCount();i++){NBTTagCompound j=links.getCompoundTagAt(i);labels.add(tr("Task #","Задача #")+j.getInteger("Id")+" — "+title(j.getInteger("Type")));serverLineJobs.add(j.getUniqueId("Key"));}
        int visible=Math.max(1,(hh-75)/13);serverLineCount=labels.size();serverLineVisible=visible;serverScroll=DataOSUiState.clamp(serverScroll,0,Math.max(0,labels.size()-visible));for(int i=serverScroll;i<Math.min(labels.size(),serverScroll+visible);i++){int yy=y+60+(i-serverScroll)*13;if(serverLineJobs.get(i)!=null){widgets.row(i,x+8,yy-2,w-24,13,false);text(labels.get(i)+"  >",x+10,yy,w-30,BLUE);}else if(i<7){int k=i==3?5:i==4?4:i==5?3:i;long u=r.getLong("U"+k),f=r.getLong("F"+k),z=r.getLong("D"+k),t=r.getLong("T"+k);text(labels.get(i),x+8,yy,58,FG);text(pair(u,t)+" "+units.get(i),x+68,yy,104,FG);text(tr("Free ","Своб. ")+f+" / "+tr("Off ","Нед. ")+z,x+174,yy,135,MUTED);resourceBar(u,f,z,t,x+310,yy,76);}else loadBar(labels.get(i),used.get(i),total.get(i),units.get(i),x+8,yy,w-24);}widgets.scroll(x+w-10,y+59,6,new DataOSUiState.Scroll(visible*13,labels.size()*13),serverScroll*13);text(tr("Grid input: ","Из энергосети: ")+r.getInteger("Power")+" W | PSU "+r.getInteger("PsuWatts")+" W",x+8,y+hh-13,w-16,FG);
    }
    private void serverWindowClick(int x,int y){int top=TOP+3;if(DataOSUiState.hit(x,y,420,top+3,24,15)){closeServer();return;}NBTTagCompound r=detailServer();if(r==null)return;if(editing!=null&&DataOSUiState.hit(x,y,376,top+40,60,16)){commitServerEdit();return;}if(DataOSUiState.hit(x,y,38,top+22,65,16)&&r.getBoolean("Editable")){editing=serverDetail;editAction=4;groupText=r.getString("Name");return;}if(DataOSUiState.hit(x,y,106,top+22,58,16)&&r.getBoolean("Editable")){editing=serverDetail;editAction=2;groupText=r.getString("Pool");return;}if(DataOSUiState.hit(x,y,168,top+22,268,16)){navPool(r.getUniqueId("PoolKey"));return;}if(DataOSUiState.hit(x,y,434,top+59,8,serverLineVisible*13)){modalDrag=1;dragModal(y);return;}if(serverScroll==drawnServerScroll&&DataOSUiState.hit(x,y,38,top+60,392,serverLineVisible*13)){int i=serverScroll+(y-top-60)/13;if(i<serverLineJobs.size()&&serverLineJobs.get(i)!=null)navTask(serverLineJobs.get(i));}}
    private void resourcePanel(){NBTTagCompound d=data();scissor(6,TOP+1,452,viewHeight-2);int y=TOP-scroll;button(tr("Pool: ","Пул: ")+(d.getString("PoolName").isEmpty()?tr("All","Все"):d.getString("PoolName"))+"  v",12,y+5,300,18,false,false);statsTable(d,y+28);statsLegend(y+166);text(tr("Shared WAN: ","Общий WAN: ")+num(d.getLong("WAN"))+" Mb/s",16,y+191,230,FG);text(tr("DC power: ","Питание ДЦ: ")+num(d.getLong("PowerDraw"))+" W",255,y+191,190,FG);section(tr("Servers and tasks in pool","Серверы и задачи пула"),y+208);int base=y+RESOURCE_FIXED;for(int i=Math.max(0,(TOP-base)/ROW);i<count()&&base+i*ROW<TOP+viewHeight;i++){NBTTagCompound r=row(i);int yy=base+i*ROW;widgets.row(i,11,yy,440,ROW-1,false);if(r==null)continue;text((r.getBoolean("ServerLink")?tr("Server ","Сервер "):tr("Task ","Задача "))+"#"+r.getInteger("Id")+"   "+(r.getBoolean("ServerLink")?r.getString("Name"):title(r.getInteger("Type")))+"  >",16,yy+6,430,BLUE);}GL11.glDisable(GL11.GL_SCISSOR_TEST);}
    private int poolCount(){return data().getTagList("Pools",10).tagCount()+(poolPicker==3||poolPicker==4?1:0);}
    private int poolVisible(){return Math.max(1,(viewHeight-45)/17);}
    private void poolWindow(){int x=72,y=TOP+4,w=330,hh=viewHeight-8;rect(5,TOP,W-10,viewHeight,0x55000000);widgets.modal(x,y,w,hh,tr("Select pool","Выберите пул"));button("X",x+w-25,y+3,21,15,false,false);NBTTagList rows=data().getTagList("Pools",10);poolScroll=Math.max(0,Math.min(poolScroll,Math.max(0,poolCount()-poolVisible())));for(int i=poolScroll;i<Math.min(poolCount(),poolScroll+poolVisible());i++){boolean all=(poolPicker==3||poolPicker==4)&&i==0;String name=all?tr("All pools","Все пулы"):rows.getCompoundTagAt(i-(poolPicker==3||poolPicker==4?1:0)).getString("Name");button(name,x+8,y+24+(i-poolScroll)*17,w-24,16,false,false);}widgets.scroll(x+w-14,y+24,7,new DataOSUiState.Scroll(poolVisible()*17,poolCount()*17),poolScroll*17);if(poolCount()==0)text(tr("Create a pool on a server first.","Сначала задайте пул серверу."),x+8,y+26,w-16,MUTED);}
    private void poolClick(int x,int y){int top=TOP+4;if(x>=377&&x<402&&y>=top&&y<top+20){poolPicker=-1;return;}if(DataOSUiState.hit(x,y,388,top+24,8,poolVisible()*17)){modalDrag=2;dragModal(y);return;}if(x<80||x>=388||y<top+24)return;int i=poolScroll+(y-top-24)/17;if(i>=poolCount()||i>=poolScroll+poolVisible())return;int op=poolPicker;UUID key=(op==3||op==4)&&i==0?TaskConsole.NONE:data().getTagList("Pools",10).getCompoundTagAt(i-((op==3||op==4)?1:0)).getUniqueId("Key");poolPicker=-1;if(op==4){serverPool=i==0?"":data().getTagList("Pools",10).getCompoundTagAt(i-1).getString("Name");queryChanged();}else if(op==3){scroll=0;send(3,0,key);requestNow();}else cmd(11,op==13?3:op,key,null);}
    private int taskScroll,confirm=-1,input;
    private boolean pending;private int sentRevision,seenRevision=Integer.MIN_VALUE,seenActions;
    private String entry="";
    private UUID host=TaskConsole.NONE;
    private String ui(String key){return net.minecraft.client.resources.I18n.format("tasks.odc."+key);}
    private final String[] sections={"active","permanent","offers","contracts","services","history"};
    private void clearTaskContext(){expectedTask=null;confirm=-1;input=0;entry="";host=TaskConsole.NONE;detailsOpen=false;detailScroll=0;editScroll=0;editDrag=null;numberKey=null;editorScrollbarDrag=false;}
    private void syncTaskContext(){
        int rev=data().getInteger("Revision"),actions=data().getInteger("Actions");
        if(pending&&rev!=sentRevision)pending=false;
        if(expectedTask!=null&&expectedTask.equals(data().getUniqueId("Selected")))expectedTask=null;
        if(rev!=seenRevision||actions!=seenActions){confirm=-1;seenRevision=rev;seenActions=actions;}
        if(data().getBoolean("Assign")){if(!host.equals(TaskConsole.NONE)&&detail().getKeySet().isEmpty()){host=TaskConsole.NONE;input=0;}}
        else if(detail().getKeySet().isEmpty()&&expectedTask==null){confirm=-1;input=0;}
        if(!data().getBoolean("Online")){confirm=-1;input=0;}
    }
    private void cmd(int op,int value,UUID key,int[] limits){if(pending||!data().getBoolean("Online"))return;sentRevision=data().getInteger("Revision");pending=true;confirm=-1;RackInteraction.channel.sendToServer(new TaskCommand(menu.windowId,sentRevision,op,value,key,limits));}
    private boolean enabledAction(int i){
        if(pending||expectedTask!=null||!data().getBoolean("Online"))return false;
        if(data().getBoolean("Assign"))return data().getBoolean("DraftAvailable")&&(i!=2||!detail().getKeySet().isEmpty())&&(i!=3||data().getBoolean("CanCommit"));
        return !detail().getKeySet().isEmpty()&&TaskActions.has(data().getInteger("Actions"),operation(i));
    }
    private int operation(int i){return i==0?(data().getInteger("Section")==2?2:3):i==1?7:i==2?(detail().getLong("Notice")==0?8:9):10;}
    private void taskButton(String label,int x,int y,int w,int hh,boolean selected,boolean enabled){widgets.button(label,x,y,w,hh,selected,enabled);}

    private int tierFilter,ruleFilter,sizeFilter,priceFilter,pauseFilter,timeFilter,sortMode,kindFilter,stateFilter,taskSort;private boolean taskDescending;
    private List<String> hosts(){
        SortedSet<String> names=new TreeSet<>();NBTTagList all=data().getTagList("Rows",10);
        for(int i=0;i<all.tagCount();i++)for(String id:all.getCompoundTagAt(i).getString("HostKeys").split(" "))if(!id.isEmpty())names.add(id);
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
        if(data().getBoolean("Assign"))return all;
        List<String> hosts=hosts();List<NBTTagCompound> rows=new ArrayList<>();
        for(int i=0;i<all.tagCount();i++){NBTTagCompound r=all.getCompoundTagAt(i);int t=r.getInteger("Type"),rule=r.getInteger("Rule");
            if(t<0||t>=Workload.ALL.length)continue;if(!DataOSUiState.matches(taskSearch,title(t),r.getString("Company"),r.getString("PoolName"),r.hasKey("Job")?"#"+r.getInteger("Job"):"",r.hasKey("Job")?Integer.toString(r.getInteger("Job")):"",ui(status(r))))continue;
            if((sec==0||sec==2)&&tierFilter>0 && Workload.ALL[t].tier!=tierFilter)continue;
            if(sec==0 && (kindFilter>0 && kindFilter!=(t<9?1:t<21?2:3) || stateFilter>0&&!states[stateFilter].equals(status(r)) || !hostKey.isEmpty()&&!Arrays.asList(r.getString("HostKeys").split(" ")).contains(hostKey)))continue;
            if(sec==2 && (ruleFilter>0&&ruleFilter!=rule+1 || sizeFilter>0&&sizeFilter!=r.getInteger("Size")+1 || priceFilter>0&&(priceFilter==2)!=(r.getLong("Fee")>0) || pauseFilter>0&&(pauseFilter==2)!=ContractMarket.CONTINUOUS[rule] || timeFilter>0&&(timeFilter==2)!=ContractMarket.TIMED[rule]))continue;
            rows.add(r);
        }
        if(sec==2 && sortMode>0)rows.sort(Comparator.comparingDouble(r->sortMode==1?-r.getLong("Pay"):sortMode==2?r.getLong("Fee"):sortMode==3?r.getLong("Goal"):sortMode==4?Workload.ALL[r.getInteger("Type")].tier:-(r.getLong("Pay")-r.getLong("Fee"))*Workload.ALL[r.getInteger("Type")].x/r.getLong("Goal")));
        if(sec!=2||sortMode==0){Comparator<NBTTagCompound> cmp=taskSort==1?Comparator.comparing(r->title(r.getInteger("Type")),String.CASE_INSENSITIVE_ORDER):taskSort==2?Comparator.comparing(this::status):Comparator.comparingInt(r->r.hasKey("Job")?r.getInteger("Job"):r.getInteger("Type"));rows.sort(DataOSUiState.ordered(cmp,Comparator.comparing(r->rowKey(r).toString()),taskDescending));}
        NBTTagList result=new NBTTagList();for(NBTTagCompound r:rows)result.appendTag(r);return result;
    }
    private String[] controls(int sec){
        String all=ui("all");
        if(sec==0)return new String[]{"T "+(tierFilter==0?all:tierFilter),ui("kind"+kindFilter),ui(states[stateFilter]),hostKey.isEmpty()?ui("hosts"):hostKey.substring(0,Math.min(8,hostKey.length()))};
        return new String[]{"T "+(tierFilter==0?all:tierFilter),ruleFilter==0?ui("rules"):ui("rule"+(ruleFilter-1)),ui("size"+sizeFilter),ui("price"+priceFilter),ui("pause"+pauseFilter),ui("time"+timeFilter),ui("sort"+sortMode)};
    }
    private List<String> filterOptions(int sec,int i){List<String> result=new ArrayList<>();if(i==0){result.add(ui("all"));for(int k=1;k<=3;k++)result.add("T "+k);return result;}if(sec==0){if(i==1)for(int k=0;k<4;k++)result.add(ui("kind"+k));else if(i==2)for(String st:states)result.add(ui(st));else{result.add(ui("hosts"));for(String host:hosts())result.add(host);}return result;}
        String prefix=i==1?"rule":i==2?"size":i==3?"price":i==4?"pause":i==5?"time":"sort";int count=i==1?9:i==2?6:i==6?6:3;for(int k=0;k<count;k++)result.add(i==1?(k==0?ui("rules"):ui(prefix+(k-1))):ui(prefix+k));return result;
    }
    private void filter(int sec,int i){popup=1;popupSection=sec;popupFilter=i;popupItems=filterOptions(sec,i);popupScroll=0;focus=0;}
    private void chooseFilter(int k){int sec=popupSection,i=popupFilter;if(i==0)tierFilter=k;else if(sec==0){if(i==1)kindFilter=k;else if(i==2)stateFilter=k;else hostKey=k==0?"":popupItems.get(k);}else if(i==1)ruleFilter=k;else if(i==2)sizeFilter=k;else if(i==3)priceFilter=k;else if(i==4)pauseFilter=k;else if(i==5)timeFilter=k;else sortMode=k;taskScroll=0;confirm=-1;}
    private int rowTop(){int sec=data().getInteger("Section");return !data().getBoolean("Assign")&&(sec==0||sec==2)?120:102;}
    private int visibleRows(){return Math.max(1,(h-rowTop()-96)/16);}
    private boolean selected(NBTTagCompound r){
        if(data().getBoolean("Assign"))return !host.equals(TaskConsole.NONE)&&host.equals(rowKey(r));
        return TaskActions.matches(data().getUniqueId("Selected"),data().getInteger("SelectedType"),rowKey(r),r.getInteger("Type"));
    }
    private NBTTagCompound detail(){NBTTagList rows=detailsOpen?data().getTagList("Rows",10):taskRows();for(int i=0;i<rows.tagCount();i++){NBTTagCompound r=rows.getCompoundTagAt(i);if(selected(r))return r;}return new NBTTagCompound();}
    private UUID rowKey(NBTTagCompound r){return r.hasUniqueId("Key")?r.getUniqueId("Key"):TaskConsole.NONE;}
    private String title(int type){return net.minecraft.client.resources.I18n.format("task.odc."+type);}
    private boolean detailsOpen;
    private int detailScroll;
    private String decimal(double v){return String.format(Locale.ROOT,"%.3f",v);}
    private final List<UUID> detailLinks=new ArrayList<>();
    private List<String> detailLines(){detailLinks.clear();
        NBTTagCompound r=detail(),d=data();boolean draft=d.getBoolean("Assign");
        int t=d.getInteger("SelectedType");if(!draft && r.hasKey("Type"))t=r.getInteger("Type");
        List<String> lines=new ArrayList<>();if(t<0||t>=Workload.ALL.length)return lines;
        Workload w=Workload.ALL[t];long now=d.getLong("Now");
        NBTTagList hosts=r.getTagList("Bindings",10);Set<UUID> unique=new LinkedHashSet<>();for(int i=0;i<hosts.tagCount();i++){NBTTagCompound b=hosts.getCompoundTagAt(i);UUID key=b.getUniqueId("Server");if(unique.add(key)){lines.add(tr("Server: ","Сервер: ")+b.getString("Host")+"  >");detailLinks.add(key);}}
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
    private String draftError(String key){switch(key){case "":return tr("Ready to apply","Готово к запуску");case "empty":return tr("Select resources below","Выберите ресурсы ниже");case "offline":return tr("Network snapshot unavailable","Нет данных доступной сети");case "bandwidth":return tr("Insufficient network bandwidth","Недостаточно пропускной способности сети");case "unavailable":return tr("Resource unavailable or reserved by another task","Ресурс недоступен или занят другой задачей");case "local requirements":return tr("Each selected server needs a complete work unit (incl. disk I/O and VRAM)","На каждом выбранном сервере нужна полная единица (в т.ч. I/O и VRAM)");case "single":return tr("Contract requires one server","Контракт требует один сервер");case "fixed":return tr("Contract has fixed resources","Контракт запрещает менять ресурсы");case "pinned":return tr("Contract fixes the server set","Контракт фиксирует набор серверов");case "retained":return tr("Retained data cannot be moved or reduced","Сохранённые данные нельзя переносить или уменьшать");default:return tr("Invalid selection","Недопустимый выбор");}}
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
            NBTTagCompound r=row.data;widgets.row(i,8,y+1,450,22,false);int max=r.getInteger("Max"),amount=r.getInteger("Amount");UUID key=r.getUniqueId("Key");if(key.equals(editDrag))amount=dragValue;
            if(row.physical==-1){text((key.equals(d.getUniqueId("Expanded"))?"v ":"> ")+familyName(row),12,y+3,215,FG);text(tr("Choose model quantity / click to expand","Количество / раскрыть модель"),18,y+13,205,MUTED);}
            else{text(r.getString("Host"),22,y+3,205,FG);text((row.kind==PoolResources.Kind.NETWORK?"NIC "+r.getInteger("NIC")+" | held "+r.getInteger("Held")+" | free "+r.getInteger("Available"):"slot "+r.getInteger("Slot"))+((row.kind==PoolResources.Kind.CPU||row.kind==PoolResources.Kind.GPU)?" / "+r.getInteger("Unit"):""),24,y+13,205,MUTED);}
            boolean enabled=d.getBoolean("DraftAvailable")&&!pending&&max>0;taskButton("-",229,y+4,17,16,false,enabled&&amount>0);taskButton("+",381,y+4,17,16,false,enabled&&amount<max);
            widgets.slider(250,y+6,127,amount,max,enabled);widgets.field(key.equals(numberKey)?numberText:amount+"/"+max,"",400,y+3,56,18,key.equals(numberKey));
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);widgets.scroll(461,top,5,new DataOSUiState.Scroll(bottom-top,rows.size()*EDIT_ROW),editScroll);
        if(pageRequest>=0&&!pending&&editDrag==null&&numberKey==null&&!d.getUniqueId("Expanded").equals(TaskConsole.NONE))cmd(14,pageRequest,d.getUniqueId("Expanded"),null);
        text(draftError(d.getString("Error")),12,h-49,450,d.getBoolean("CanCommit")?0xff35764a:0xff9e422e);text(d.getString("Diagnostic"),12,h-38,450,d.getString("Error").isEmpty()?MUTED:0xff9e422e);
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
    private void editorDragScroll(int y){int top=editorTop();DataOSUiState.Scroll s=new DataOSUiState.Scroll(editorBottom()-top,editorRows().size()*EDIT_ROW);editScroll=s.value(y-top-s.thumb/2);}
    private void editorNumberKey(char c,int k){if(k==Keyboard.KEY_ESCAPE){numberKey=null;return;}if(k==Keyboard.KEY_BACK){if(!numberText.isEmpty())numberText=numberText.substring(0,numberText.length()-1);return;}if(k==Keyboard.KEY_DELETE){numberText="";return;}if(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){try{int n=Integer.parseInt(numberText);if(n>=0&&n<=numberMax){UUID key=numberKey;numberKey=null;cmd(12,n,key,null);}}catch(NumberFormatException ignored){}return;}if(c>='0'&&c<='9'&&numberText.length()<10)numberText+=c;}
    private void taskPanel(){
        syncTaskContext();boolean draft=data().getBoolean("Assign");taskDraftOpen=draft;if(draft){draftPanel();return;}int section=data().getInteger("Section");NBTTagList rows=taskRows();
        if(detailsOpen){
            List<String> lines=detailLines();if(expectedTask!=null||detail().getKeySet().isEmpty()){lines=Collections.singletonList(tr("Loading or task unavailable","Загрузка или задача недоступна"));detailLinks.clear();}int count=detailVisible();detailScroll=Math.max(0,Math.min(detailScroll,Math.max(0,lines.size()-count)));
            for(int i=detailScroll;i<Math.min(lines.size(),detailScroll+count);i++){int yy=55+(i-detailScroll)*13;widgets.row(i,8,yy-2,449,13,false);text(lines.get(i),12,yy,440,i<detailLinks.size()?BLUE:FG);}widgets.scroll(459,53,7,new DataOSUiState.Scroll(count*13,lines.size()*13),detailScroll*13);
            button(ui("back"),8,h-27,100,18,false,false);return;
        }
        for(int i=0;i<6;i++)button(ui(sections[i]),6+i*77,49,75,17,i==section,false);
        widgets.field(taskSearch,tr("Search tasks...","Поиск задач..."),7,68,420,15,focus==2);button("X",431,68,25,15,false,false);if(rowTop()==120){String[] c=controls(section);int w=455/c.length;for(int i=0;i<c.length;i++)button(c[i],7+i*w,86,w-1,15,false,false);}
        widgets.header(7,rowTop()-16,449,15);String[] cols={"ID",tr("Task","Задача"),tr("State","Состояние")};int[] starts={7,57,330},sizes={50,273,126};for(int i=0;i<3;i++)text(cols[i]+(taskSort==i?(taskDescending?" v":" ^"):""),starts[i]+3,rowTop()-12,sizes[i]-6,BLUE);
        taskScroll=Math.max(0,Math.min(taskScroll,rows.tagCount()-visibleRows()));
        for(int n=taskScroll;n<Math.min(rows.tagCount(),taskScroll+visibleRows());n++){
            NBTTagCompound r=rows.getCompoundTagAt(n);int y=rowTop()+(n-taskScroll)*16;
            String name=draft?(r.getBoolean("Chosen")?"[+] ":"[ ] ")+rowKey(r).toString().substring(0,8)+"   CPU "+r.getInteger("CPU")+"   GPU "+r.getInteger("GPU")+"   RAM "+r.getInteger("RAM")+" GB   Disk "+r.getInteger("Disk")+" GB":title(r.getInteger("Type"));
            if(r.hasKey("Company"))name+="   "+r.getString("Company")+"   "+ui("rule"+r.getInteger("Rule"))+"   "+r.getLong("Pay")/1000.0+" NCoin";
            widgets.row(n,7,y,449,15,!pending&&selected(r));text(r.hasKey("Job")?"#"+r.getInteger("Job"):"—",10,y+3,44,FG);text(name,60,y+3,267,FG);text(ui(status(r)),333,y+3,119,FG);
        }
        widgets.scroll(459,rowTop(),7,taskListScroll(),taskScroll*16);
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
        if(detailsOpen){if(y>=h-27&&y<h-9&&x>=8&&x<108){if(navigation.available())goBack();else{detailsOpen=false;expectedTask=null;}return;}if(x>=459&&x<467&&y>=53&&y<53+detailVisible()*13){listDrag=true;listDragOffset=detailListScroll().thumb/2;dragTaskList(y);return;}if(detailScroll==drawnDetailScroll&&x>=8&&x<457&&y>=53&&y<53+detailVisible()*13){int i=detailScroll+(y-53)/13;if(i<detailLinks.size())navServer(detailLinks.get(i));}return;}
        if(pending||!data().getBoolean("Online"))return;
        boolean draft=data().getBoolean("Assign");
        if(draft){draftClick(x,y);return;}
        if(x>=361&&x<461&&y>=h-48&&y<h-32){if(draft?data().getBoolean("DraftAvailable"):!detail().getKeySet().isEmpty()){detailsOpen=true;detailScroll=0;}return;}
        if(y>=49&&y<66&&x>=6&&x<468){clearTaskContext();taskScroll=0;cmd(0,(x-6)/77,TaskConsole.NONE,null);return;}
        if(rowTop()==120&&y>=86&&y<102&&x>=7&&x<462){int sec=data().getInteger("Section");filter(sec,Math.min(controls(sec).length-1,(x-7)/(455/controls(sec).length)));return;}
        if(y>=68&&y<83&&x>=7&&x<457){if(x>=431){taskSearch="";taskScroll=0;focus=0;}else focus=2;return;}focus=0;
        if(y>=rowTop()-16&&y<rowTop()&&x>=7&&x<456){int next=x<57?0:x<330?1:2;if(next==taskSort)taskDescending=!taskDescending;else{taskSort=next;taskDescending=false;}if(data().getInteger("Section")==2)sortMode=0;taskScroll=0;return;}
        if(x>=459&&x<467&&y>=rowTop()&&y<rowTop()+visibleRows()*16){listDrag=true;listDragOffset=taskListScroll().thumb/2;dragTaskList(y);return;}
        if(taskScroll==drawnTaskScroll&&x>=7&&x<456&&y>=rowTop()&&y<rowTop()+visibleRows()*16){
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
        }else{int op=operation(i);if(op!=3){confirm=op;return;}performTaskAction(op,key);}
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
    private int scrollTop(){return TOP+((tab==1||tab==7)?HEADER+22:0);}
    private int thumb(){return mainScroll().thumb;}
    private int thumbY(){return scrollTop()+mainScroll().position(scroll);}
    private void scrollbar(){widgets.scroll(459,scrollTop(),8,mainScroll(),scroll);}
    private DataOSUiState.Scroll mainScroll(){return new DataOSUiState.Scroll(visibleHeight(),content());}
    private int detailVisible(){return Math.max(1,(h-91)/13);}
    private DataOSUiState.Scroll taskListScroll(){return new DataOSUiState.Scroll(visibleRows()*16,taskRows().tagCount()*16);}
    private DataOSUiState.Scroll detailListScroll(){return new DataOSUiState.Scroll(detailVisible()*13,detailLines().size()*13);}
    private void dragTaskList(int y){if(detailsOpen)detailScroll=detailListScroll().value(y-53-listDragOffset)/13;else taskScroll=taskListScroll().value(y-rowTop()-listDragOffset)/16;}
    private int popupTop(){return TOP+22;}
    private int popupVisible(){return Math.min(popupItems.size(),Math.max(1,(viewHeight-48)/17));}
    private void drawPopup(){int x=194,y=popupTop(),w=260,hh=24+popupVisible()*17;widgets.modal(x,y,w,hh,popup==0?tr("Power state","Состояние питания"):tr("Select filter","Выберите фильтр"));button("X",x+w-24,y+3,20,14,false,false);popupScroll=DataOSUiState.clamp(popupScroll,0,Math.max(0,popupItems.size()-popupVisible()));for(int i=popupScroll;i<Math.min(popupItems.size(),popupScroll+popupVisible());i++)button(popupItems.get(i),x+5,y+20+(i-popupScroll)*17,w-18,16,popup==0&&powerFilter==i,false);widgets.scroll(x+w-10,y+20,7,new DataOSUiState.Scroll(popupVisible()*17,popupItems.size()*17),popupScroll*17);}
    private void popupClick(int x,int y){int top=popupTop();if(DataOSUiState.hit(x,y,430,top+3,20,14)){popup=-1;return;}if(DataOSUiState.hit(x,y,444,top+20,8,popupVisible()*17)){modalDrag=3;dragModal(y);return;}if(DataOSUiState.hit(x,y,199,top+20,242,popupVisible()*17)){int k=popupScroll+(y-top-20)/17;if(popup==0){powerFilter=k;queryChanged();}else if(popup==2){eventCategory=k==0?"":popupItems.get(k);queryChanged();}else if(popup==3){eventLevel=k==0?"":popupItems.get(k);queryChanged();}else chooseFilter(k);}popup=-1;}
    private void dragModal(int y){if(modalDrag==1){DataOSUiState.Scroll s=new DataOSUiState.Scroll(serverLineVisible*13,serverLineCount*13);serverScroll=s.value(y-(TOP+3+59)-s.thumb/2)/13;}else if(modalDrag==2){DataOSUiState.Scroll s=new DataOSUiState.Scroll(poolVisible()*17,poolCount()*17);poolScroll=s.value(y-(TOP+4+24)-s.thumb/2)/17;}else if(modalDrag==3){DataOSUiState.Scroll s=new DataOSUiState.Scroll(popupVisible()*17,popupItems.size()*17);popupScroll=s.value(y-(popupTop()+20)-s.thumb/2)/17;}}
    private void performTaskAction(int op,UUID key){confirm=-1;if(op==2||op==3&&!detail().hasKey("Job")){poolPicker=op==3?13:2;poolScroll=0;return;}cmd(op,0,key,null);}
    private void confirmWindow(){int x=87,y=TOP+28;rect(5,TOP,W-10,viewHeight,0x55000000);widgets.modal(x,y,300,77,tr("Confirm action","Подтвердите действие"));text(ui(confirm==2?"accept":confirm==7?"pause":confirm==8?"maintenance":confirm==9?"undo":"cancel")+" — "+title(data().getInteger("SelectedType")),x+8,y+25,284,FG);if(confirm==2)text(ui("fee")+" "+decimal(detail().getLong("Fee")/1000.)+" NCoin",x+8,y+37,284,MUTED);button(tr("Confirm","Подтвердить"),x+8,y+51,138,18,false,false);button(tr("Back","Назад"),x+154,y+51,138,18,false,false);}
    private void confirmClick(int x,int y){int op=confirm;confirm=-1;int top=TOP+28;if(DataOSUiState.hit(x,y,95,top+51,138,18)&&TaskActions.has(data().getInteger("Actions"),op)&&!detail().getKeySet().isEmpty()&&data().getBoolean("Online"))performTaskAction(op,data().getUniqueId("Selected"));}
    @Override public void handleMouseInput() throws IOException{if(!menu.terminalPowered())return;super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;int step=Integer.signum(wheel)*3;if(popup>=0){popupScroll=DataOSUiState.clamp(popupScroll-step,0,Math.max(0,popupItems.size()-popupVisible()));return;}if(tab==2&&confirm>=0)return;if(poolPicker>=0){poolScroll=DataOSUiState.clamp(poolScroll-step,0,Math.max(0,poolCount()-poolVisible()));return;}if(tab==1&&serverDetail!=null){serverScroll=DataOSUiState.clamp(serverScroll-step,0,Math.max(0,serverLineCount-serverLineVisible));return;}if(tab==2){if(data().getBoolean("Assign")){editScroll=DataOSUiState.clamp(editScroll-step*EDIT_ROW,0,editorMax(editorRows()));return;}if(detailsOpen)detailScroll=Math.max(0,detailScroll-step);else taskScroll=Math.max(0,taskScroll-step);return;}if(tab==7&&eventDetail!=null)return;if(ready()&&(tab==0||tab==1||tab==3||tab==5||tab==6||tab==7)){int x=lx(Mouse.getEventX()*width/mc.displayWidth),y=ly(height-Mouse.getEventY()*height/mc.displayHeight-1);if(x>=5&&x<W-5&&y>=TOP&&y<TOP+viewHeight)setScroll(scroll-step*ROW);}}
    @Override protected void mouseClicked(int mx,int my,int b)throws IOException{if(!menu.terminalPowered())return;int x=lx(mx),y=ly(my);if(b!=0)return;
        if(popup>=0){popupClick(x,y);return;}if(poolPicker>=0){poolClick(x,y);return;}if(tab==2&&confirm>=0){confirmClick(x,y);return;}
        if(DataOSUiState.hit(x,y,92,6,74,15)&&navigation.available()){goBack();return;}
        if(tab==7&&eventDetail!=null){eventWindowClick(x,y);return;}
        if(tab==1&&serverDetail!=null){serverWindowClick(x,y);return;}
        int clickedTab=DataOSUiState.tabAt(x,y);if(clickedTab>=0){changeTab(clickedTab);return;}
        if(tab==3&&ready()){if(DataOSUiState.hit(x,y,12,TOP-scroll+5,300,18)&&y>=TOP&&y<TOP+viewHeight){poolPicker=3;poolScroll=0;return;}if(scroll==drawnScroll&&x>=11&&x<451&&y>=TOP&&y<TOP+viewHeight&&y>=TOP-scroll+RESOURCE_FIXED){NBTTagCompound r=row((y-TOP+scroll-RESOURCE_FIXED)/ROW);if(r!=null){if(r.getBoolean("ServerLink"))navServer(r.getUniqueId("Key"));else navTask(r.getUniqueId("Key"));}return;}}
        if(tab==2&&ready()){taskClick(x,y);return;}
        if((tab==0||tab==1||tab==3||tab==5||tab==6||tab==7)&&x>=459&&x<=467&&y>=scrollTop()&&y<scrollTop()+visibleHeight()){dragging=true;dragOffset=y-thumbY();if(dragOffset<0||dragOffset>=thumb()){dragOffset=thumb()/2;drag(y);}return;}
        if(tab==5){powerClick(x,y);return;}
        if(tab==0){overviewClick(x,y);return;}if(tab==6&&ready()){if(scroll==drawnScroll&&DataOSUiState.hit(x,y,11,TOP,440,viewHeight)&&y-TOP+scroll>=WALLET_FIXED){NBTTagCompound e=row((y-TOP+scroll-WALLET_FIXED)/ROW);if(e!=null)navEvent(e.getUniqueId("Key"));}return;}
        if(tab==7){logClick(x,y);return;}
        if(tab==1){if(DataOSUiState.hit(x,y,7,TOP+3,174,16)){focus=1;return;}focus=0;if(DataOSUiState.hit(x,y,184,TOP+3,133,16)){poolPicker=4;poolScroll=0;return;}if(DataOSUiState.hit(x,y,320,TOP+3,112,16)){popup=0;popupScroll=0;popupItems=Arrays.asList(powerLabel(0),powerLabel(1),powerLabel(2),powerLabel(3));return;}if(DataOSUiState.hit(x,y,435,TOP+3,21,16)){serverSearch=serverPool="";powerFilter=0;serverSort=1;serverDescending=false;queryChanged();return;}
            if(y>=SERVER_TOP&&y<SERVER_TOP+HEADER&&x>=6&&x<456){for(int i=0;i<COL.length-1;i++)if(x>=6+COL[i]&&x<6+COL[i+1]){if(serverSort==i)serverDescending=!serverDescending;else{serverSort=i;serverDescending=false;}queryChanged();return;}}
            if(ready()&&scroll==drawnScroll&&x>=6&&x<456&&y>=SERVER_TOP+HEADER&&y<TOP+viewHeight){NBTTagCompound r=row((y-SERVER_TOP-HEADER+scroll)/ROW);if(r!=null){if(x>=6+COL[8]){if(r.getBoolean("Editable")&&data().getBoolean("Online"))send(1,r.getBoolean("Enabled")?0:1,r.getUniqueId("Key"));return;}editing=null;openServer(r.getUniqueId("Key"));if(x>=6+COL[7]&&r.getBoolean("Editable")){editing=serverDetail;editAction=2;groupText=r.getString("Pool");}return;}}}
    }
    private void drag(int y){setScroll(mainScroll().value(y-scrollTop()-dragOffset));}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(!menu.terminalPowered())return;if(modalDrag!=0){dragModal(ly(y));return;}if(listDrag){dragTaskList(ly(y));return;}if(editorScrollbarDrag){editorDragScroll(ly(y));return;}if(editDrag!=null){dragValue=sliderValue(lx(x),dragMax);return;}if(dragging)drag(ly(y));}
    @Override protected void mouseReleased(int x,int y,int state){if(!menu.terminalPowered())return;dragging=false;listDrag=false;modalDrag=0;editorScrollbarDrag=false;if(editDrag!=null){UUID key=editDrag;int value=dragValue;editDrag=null;if(tab==2&&data().getBoolean("Assign"))cmd(12,value,key,null);}}
    @Override protected void keyTyped(char c,int k)throws IOException{if(!menu.terminalPowered()){if(k==Keyboard.KEY_ESCAPE)super.keyTyped(c,k);return;}if(popup>=0){if(k==Keyboard.KEY_ESCAPE)popup=-1;return;}if(poolPicker>=0){if(k==Keyboard.KEY_ESCAPE)poolPicker=-1;return;}if(tab==2&&confirm>=0){if(k==Keyboard.KEY_ESCAPE)confirm=-1;return;}
        if(tab==7&&eventDetail!=null){if(k==Keyboard.KEY_ESCAPE)closeEvent();return;}
        if(tab==1&&editing!=null){if(k==Keyboard.KEY_ESCAPE){editing=null;return;}if(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){commitServerEdit();return;}groupText=DataOSUiState.edit(groupText,c,k==Keyboard.KEY_BACK,k==Keyboard.KEY_DELETE,32);return;}
        if(tab==1&&serverDetail!=null){if(k==Keyboard.KEY_ESCAPE)closeServer();return;}
        if(focus!=0){if(k==Keyboard.KEY_ESCAPE||k==Keyboard.KEY_RETURN||k==Keyboard.KEY_NUMPADENTER){focus=0;return;}if(focus==1){String n=DataOSUiState.edit(serverSearch,c,k==Keyboard.KEY_BACK,k==Keyboard.KEY_DELETE,64);if(!n.equals(serverSearch)){serverSearch=n;queryChanged();}}else if(focus==3){String n=DataOSUiState.edit(eventSearch,c,k==Keyboard.KEY_BACK,k==Keyboard.KEY_DELETE,64);if(!n.equals(eventSearch)){eventSearch=n;queryChanged();}}else{String n=DataOSUiState.edit(taskSearch,c,k==Keyboard.KEY_BACK,k==Keyboard.KEY_DELETE,64);if(!n.equals(taskSearch)){taskSearch=n;taskScroll=0;confirm=-1;}}return;}
        if(tab==2&&data().getBoolean("Assign")&&numberKey!=null){editorNumberKey(c,k);return;}if(tab==2&&data().getBoolean("Assign")&&k==Keyboard.KEY_ESCAPE){cmd(0,data().getInteger("Section"),TaskConsole.NONE,null);return;}if(tab==2&&input!=0){taskKey(c,k);return;}if(k==Keyboard.KEY_ESCAPE&&navigation.available()){goBack();return;}super.keyTyped(c,k);}
    @Override protected void drawGuiContainerBackgroundLayer(float f,int x,int y){}
    @Override public boolean doesGuiPauseGame(){return false;}
}
