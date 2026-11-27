package odc;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;

/** Shared light theme renderer; layout and hit testing use logical pixels. */
final class DataOSWidgets {
    static final int FG=0xff26323d, MUTED=0xff65717e, BLUE=0xff245790, WHITE=0xfffafbfc, EDGE=0xff9faebb, ALT=0xffedf2f7;
    final FontRenderer font; final int mx,my;
    DataOSWidgets(FontRenderer font,int mx,int my){this.font=font;this.mx=mx;this.my=my;}
    static void rect(int x,int y,int w,int h,int color){if(w>0&&h>0)Gui.drawRect(x,y,x+w,y+h,color);}
    /** Unpowered terminal keeps its normal window bounds, leaving the world background visible. */
    static void powerOff(FontRenderer font,int x,int y,int w,int h){
        rect(x,y,w,h,EDGE);
        rect(x+1,y+1,w-2,h-2,0xffdce2e8);
        rect(x+2,y+2,w-4,h-4,0xff000000);
        String label=net.minecraft.client.resources.I18n.format("gui.odc.no_power");
        label=font.trimStringToWidth(label,Math.max(0,w-8));
        font.drawString(label,x+(w-font.getStringWidth(label))/2,y+(h-font.FONT_HEIGHT)/2,0xffffffff);
    }
    void text(String s,int x,int y,int max,int color){if(max>0)font.drawString(font.trimStringToWidth(s,max),x,y,color);}
    void center(String s,int x,int y,int w,int color){s=font.trimStringToWidth(s,Math.max(0,w-4));text(s,x+(w-font.getStringWidth(s))/2,y,w,color);}
    void button(String s,int x,int y,int w,int h,boolean selected,boolean enabled){boolean hover=enabled&&DataOSUiState.hit(mx,my,x,y,w,h);rect(x,y,w,h,selected&&enabled?0xff527eae:0xff9aa9b8);rect(x+1,y+1,w-2,h-2,!enabled?0xffe1e4e7:selected?0xffd8e9fa:hover?0xffedf5ff:0xffe7ebef);rect(x+1,y+1,w-2,1,0xffffffff);rect(x+1,y+h-2,w-2,1,0xffc0cbd5);center(s,x,y+(h-8)/2,w,enabled?FG:0xff929aa2);}
    void field(String s,String hint,int x,int y,int w,int h,boolean focused){rect(x,y,w,h,focused?BLUE:EDGE);rect(x+1,y+1,w-2,h-2,0xffffffff);text(s.isEmpty()&&!focused?hint:s+(focused?"_":""),x+4,y+(h-8)/2,w-8,s.isEmpty()&&!focused?MUTED:FG);}
    void row(int index,int x,int y,int w,int h,boolean selected){rect(x,y,w,h,selected?0xffcde1fa:index%2==0?ALT:WHITE);}
    void header(int x,int y,int w,int h){rect(x,y,w,h,0xffdce7f1);}
    void scroll(int x,int y,int w,DataOSUiState.Scroll s,int value){rect(x,y,w,s.view,0xffdce2e8);rect(x+1,y+s.position(value),Math.max(1,w-2),s.thumb,0xff8ca4bb);}
    void modal(int x,int y,int w,int h,String title){rect(x,y,w,h,EDGE);rect(x+1,y+1,w-2,h-2,WHITE);rect(x+2,y+2,w-4,16,BLUE);text(title,x+8,y+6,w-36,0xffffffff);}
    void slider(int x,int y,int width,int amount,int max,boolean enabled){int pos=(int)((width-8)*(long)amount/Math.max(1,max));pos=DataOSUiState.clamp(pos,0,width-8);rect(x,y+4,width,5,0xffbac6d0);rect(x,y+4,pos+4,5,0xff719dcb);rect(x+pos,y,8,13,enabled?BLUE:MUTED);}
}
