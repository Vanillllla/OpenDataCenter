package odc;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;
import java.io.IOException;
/** Stage 3 equipment list and temporary uplink editor. Stage 4 projection of reservations, not measured traffic. */
final class NetworkInfoScreen extends GuiContainer {
    private final Menus.Menu menu;private int tier=1;private java.util.UUID selected;
    NetworkInfoScreen(Menus.Menu m){super(m);menu=m;xSize=300;ySize=232;}
    private String tr(String key,Object... args){return I18n.format("gui.odc.netinfo."+key,args);}
    private String equipment(int ordinal){NetworkEquipment[] e=NetworkEquipment.values();return ordinal>=0&&ordinal<e.length?I18n.format("tile.odc."+e[ordinal].id+".name"):tr("unknown");}
    private String name(int type,int model){if(model>=0)return equipment(model);if(type==PhysicalTopology.Type.SWITCH.ordinal())return tr("switch");if(type==PhysicalTopology.Type.SERVER.ordinal())return tr("server");return tr("unknown");}
    @Override protected void drawGuiContainerBackgroundLayer(float f,int mx,int my){int x=guiLeft,y=guiTop;int[] d=menu.data;DataOSWidgets w=new DataOSWidgets(fontRenderer,mx,my);
        DataOSWidgets.rect(x,y,xSize,ySize,DataOSWidgets.EDGE);DataOSWidgets.rect(x+2,y+2,xSize-4,ySize-4,DataOSWidgets.WHITE);DataOSWidgets.rect(x+3,y+3,xSize-6,18,DataOSWidgets.BLUE);
        w.text(equipment(d[4]),x+8,y+8,menu.type==Menus.STATION_LIST?208:284,0xffffffff);if(menu.type==Menus.STATION_LIST)w.button(tr("map"),x+224,y+4,69,15,false,true);
        w.text(tr("capacity",d[1]),x+8,y+26,284,DataOSWidgets.FG);w.text(tr(d[0]==1?"powered":"unpowered")+" | "+d[3]+" W",x+8,y+39,284,d[0]==1?0xff367849:DataOSWidgets.MUTED);
        w.text(tr("peers",d[5]),x+8,y+52,284,DataOSWidgets.FG);w.text(d[9]<0?tr("pending"):tr("projection",d[9]),x+8,y+65,284,DataOSWidgets.MUTED);
        w.header(x+6,y+79,288,14);w.text(tr("devices"),x+9,y+82,280,DataOSWidgets.BLUE);
        for(int i=0;i<Math.min(6,Math.max(0,d[7]-d[8]));i++){int k=10+i*8,yy=y+93+i*13;w.row(i,x+6,yy,280,13,uuid(i).equals(selected));w.text((d[k+7]==-1&&d[8]+i<d[5]?"F | ":"L | ")+name(d[k],d[k+1])+ (d[k+7]>=0?" U"+(d[k+7]+1):""),x+9,yy+2,112,DataOSWidgets.FG);w.text(d[k+7]==-2?tr("separated")+" "+LogicalLinks.speed(d[112+i])+"M":d[112+i]>0?"LAN "+LogicalLinks.speed(d[112+i])+"M":d[k+2]+","+d[k+3]+","+d[k+4],x+125,yy+2,80,DataOSWidgets.MUTED);w.text((d[124+i]<0?"?":String.valueOf(d[124+i]))+"/"+d[k+5]+"M",x+207,yy+2,68,DataOSWidgets.FG);DataOSWidgets.rect(x+278,yy+4,4,4,d[k+6]==2?0xff4e9b59:d[k+6]==1?0xffb48b50:0xff919ba4);}
        DataOSUiState.Scroll scroll=new DataOSUiState.Scroll(78,d[7]*13);w.scroll(x+286,y+93,7,scroll,d[8]*13);
        w.text(d[7]==0?tr("empty"):tr("rows",d[8]+1,Math.min(d[7],d[8]+6),d[7]),x+8,y+174,284,DataOSWidgets.MUTED);
        w.text(tr("legend"),x+8,y+186,284,DataOSWidgets.MUTED);
        if(menu.tile instanceof Tiles.NetworkTerminal){for(int i=1;i<=3;i++){int xx=x+8+(i-1)*95;DataOSWidgets.rect(xx,y+198,91,15,tier==i?0xffbad5ec:0xffdce3ea);w.text((i==1?"100M":i==2?"1G":"100G")+" x"+d[120+i],xx+4,y+202,85,DataOSWidgets.FG);}String[] actions={tr("connect"),tr("disconnect"),tr("upgrade")};for(int i=0;i<3;i++){int xx=x+8+i*95;DataOSWidgets.rect(xx,y+215,91,16,selected==null?0xffd9dfe4:0xffc6d9ed);w.text(actions[i],xx+3,y+219,86,DataOSWidgets.FG);}}

    }
    private java.util.UUID uuid(int row){return new java.util.UUID(PanelMenu.getLong(menu.data,80+row*4),PanelMenu.getLong(menu.data,82+row*4));}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{if(!menu.terminalPowered())return;int x=mx-guiLeft,y=my-guiTop;if(button==0&&menu.type==Menus.STATION_LIST&&x>=224&&x<293&&y>=4&&y<19){mc.playerController.sendEnchantPacket(menu.windowId,103);return;}if(button==0&&menu.tile instanceof Tiles.NetworkTerminal&&x>=8&&x<293){if(y>=93&&y<171){int row=(y-93)/13;if(row<Math.min(6,Math.max(0,menu.data[7]-menu.data[8]))&&menu.data[10+row*8]==PhysicalTopology.Type.SWITCH.ordinal())selected=uuid(row);else selected=null;return;}if(y>=198&&y<213){tier=Math.min(3,(x-8)/95+1);return;}if(y>=215&&y<231&&selected!=null){int op=(x-8)/95;if(op<3)RackInteraction.channel.sendToServer(new UplinkCommand(menu.windowId,selected,op,tier));return;}}super.mouseClicked(mx,my,button);}
    @Override public void drawScreen(int x,int y,float f){drawDefaultBackground();if(!menu.terminalPowered()){selected=null;DataOSWidgets.powerOff(fontRenderer,guiLeft,guiTop,xSize,ySize);return;}super.drawScreen(x,y,f);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(!menu.terminalPowered()){if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE)super.keyTyped(c,key);return;}super.keyTyped(c,key);}
    @Override public void handleMouseInput() throws IOException {if(!menu.terminalPowered())return;super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0&&mc.playerController!=null)mc.playerController.sendEnchantPacket(menu.windowId,wheel>0?100:101);}
}
