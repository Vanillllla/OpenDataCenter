package odc;

import java.io.IOException;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class TransformerScreen extends GuiContainer {
    private final TransformerMenu menu;
    TransformerScreen(TransformerMenu menu){super(menu);this.menu=menu;xSize=260;ySize=180;}
    private String tr(String key,Object... args){return I18n.format("gui.odc.transformer."+key,args);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mx,int my){
        int x=guiLeft,y=guiTop;int[] d=menu.data;
        DataOSWidgets w=new DataOSWidgets(fontRenderer,mx,my);
        DataOSWidgets.rect(x,y,xSize,ySize,DataOSWidgets.EDGE);
        DataOSWidgets.rect(x+2,y+2,xSize-4,ySize-4,DataOSWidgets.WHITE);
        DataOSWidgets.rect(x+3,y+3,xSize-6,18,DataOSWidgets.BLUE);
        w.text(tr("title",d[0]),x+8,y+8,244,0xffffffff);
        w.text(tr("rating",d[1]),x+9,y+27,242,DataOSWidgets.FG);
        w.text(tr("output",d[2]),x+9,y+40,242,DataOSWidgets.FG);
        w.text(tr("input",d[3],d[10]),x+9,y+53,242,DataOSWidgets.FG);
        w.text(tr(d[4]==GridProtection.ON?"armed":d[4]==GridProtection.TRIPPED?"tripped":"off"),x+9,y+66,242,d[4]==GridProtection.ON?0xff987029:DataOSWidgets.MUTED);
        w.text(tr(d[11]==1?"funded":"pending"),x+9,y+79,242,DataOSWidgets.MUTED);
        w.header(x+6,y+93,248,13);w.text(tr("grid"),x+9,y+96,242,DataOSWidgets.BLUE);
        w.text(tr("grid_funded",d[5]),x+9,y+110,242,DataOSWidgets.FG);
        w.text(tr("grid_rated",d[6]),x+9,y+123,242,DataOSWidgets.MUTED);
        w.text(tr("rates",d[7],d[8]),x+9,y+136,242,DataOSWidgets.MUTED);
        w.button(tr(d[4]==GridProtection.ON?"switch_off":d[9]==1?"switch_on":"incomplete"),x+8,y+151,244,20,false,d[0]>=1&&(d[4]==GridProtection.ON||d[9]==1));
    }
    @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();super.drawScreen(mx,my,partial);}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button==0&&menu.data[0]>=1&&(menu.data[4]==GridProtection.ON||menu.data[9]==1)&&DataOSUiState.hit(mx,my,guiLeft+8,guiTop+151,244,20)){
            mc.playerController.sendEnchantPacket(menu.windowId,TransformerMenu.TOGGLE);return;
        }
        super.mouseClicked(mx,my,button);
    }
}
