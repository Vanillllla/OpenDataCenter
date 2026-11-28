package odc;
import java.io.IOException;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
@SideOnly(Side.CLIENT)
final class BatteryScreen extends GuiContainer {
    private final BatteryMenu menu;private GuiTextField field;private boolean edited;private int last=-1;
    BatteryScreen(BatteryMenu m){super(m);menu=m;xSize=230;ySize=120;}
    private String tr(String k){return I18n.format("gui.odc.battery."+k);}
    @Override public void initGui(){String old=field==null?"":field.getText();super.initGui();field=new GuiTextField(0,fontRenderer,guiLeft+9,guiTop+41,180,16);field.setMaxStringLength(9);field.setValidator(s->s.matches("[0-9]*"));field.setText(old);}
    private int value(){try{int n=Integer.parseInt(field.getText());return n>=1&&n<=TransformerCore.MAX_WATTS?n:-1;}catch(NumberFormatException e){return -1;}}
    @Override public void updateScreen(){super.updateScreen();field.updateCursorCounter();if(!edited&&menu.data[0]>0&&last!=menu.data[0]){last=menu.data[0];field.setText(Integer.toString(last));}}
    @Override protected void drawGuiContainerBackgroundLayer(float p,int mx,int my){
        int x=guiLeft,y=guiTop;DataOSWidgets w=new DataOSWidgets(fontRenderer,mx,my);
        DataOSWidgets.rect(x,y,xSize,ySize,DataOSWidgets.EDGE);DataOSWidgets.rect(x+2,y+2,xSize-4,ySize-4,DataOSWidgets.WHITE);DataOSWidgets.rect(x+3,y+3,xSize-6,18,DataOSWidgets.BLUE);
        w.text(tr("title"),x+8,y+8,214,0xffffffff);w.text(tr("output"),x+9,y+27,210,DataOSWidgets.FG);w.text("W",x+196,y+45,24,DataOSWidgets.FG);
        field.drawTextBox();w.button(tr("save"),x+8,y+65,214,18,false,value()>0&&menu.data[0]>0);
        if(menu.data[1]!=GridProtection.ON)w.button(tr(menu.data[2]==1?"reset":"incomplete"),x+8,y+91,214,18,false,menu.data[2]==1);
    }
    private void save(){int n=value();if(n>0&&menu.data[0]>0){RackInteraction.channel.sendToServer(new BatteryCommand(menu.windowId,n));edited=false;last=menu.data[0];field.setFocused(false);}}
    @Override public void drawScreen(int mx,int my,float p){drawDefaultBackground();super.drawScreen(mx,my,p);}
    @Override protected void mouseClicked(int mx,int my,int b)throws IOException{field.mouseClicked(mx,my,b);if(b==0&&DataOSUiState.hit(mx,my,guiLeft+8,guiTop+65,214,18)){save();return;}if(b==0&&menu.data[1]!=GridProtection.ON&&menu.data[2]==1&&DataOSUiState.hit(mx,my,guiLeft+8,guiTop+91,214,18)){mc.playerController.sendEnchantPacket(menu.windowId,1);return;}super.mouseClicked(mx,my,b);}
    @Override protected void keyTyped(char c,int k)throws IOException{if(k==org.lwjgl.input.Keyboard.KEY_RETURN&&field.isFocused()){save();return;}if(field.textboxKeyTyped(c,k)){edited=true;return;}super.keyTyped(c,k);}
}
