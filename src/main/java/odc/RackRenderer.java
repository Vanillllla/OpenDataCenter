package odc;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** One renderer per complete rack. A 4U panel is a single mesh, even across the block seam. */
final class RackRenderer extends TileEntitySpecialRenderer<Tiles.Rack> {
    private void texture(String name){bindTexture(new ResourceLocation(ODC.ID,"textures/blocks/"+name+".png"));}
    private static void quad(double[] a,double[] b,double[] c,double[] d){BufferBuilder buf=Tessellator.getInstance().getBuffer();buf.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);buf.pos(a[0],a[1],a[2]).tex(0,1).endVertex();buf.pos(b[0],b[1],b[2]).tex(1,1).endVertex();buf.pos(c[0],c[1],c[2]).tex(1,0).endVertex();buf.pos(d[0],d[1],d[2]).tex(0,0).endVertex();Tessellator.getInstance().draw();}
    private static double[] v(double x,double y,double z){return new double[]{x,y,z};}
    private void body(double l,double b,double r,double t,double z,double back){texture("steel");GlStateManager.color(.72f,.76f,.78f,1);quad(v(l,b,z),v(l,b,back),v(l,t,back),v(l,t,z));quad(v(r,b,back),v(r,b,z),v(r,t,z),v(r,t,back));quad(v(l,b,back),v(r,b,back),v(r,t,back),v(l,t,back));GlStateManager.color(.92f,.94f,.95f,1);quad(v(l,t,z),v(r,t,z),v(r,t,back),v(l,t,back));quad(v(l,b,back),v(r,b,back),v(r,b,z),v(l,b,z));}
    private void front(String tex,double l,double b,double r,double t,double z){texture(tex);GlStateManager.color(1,1,1,1);quad(v(l,b,z),v(r,b,z),v(r,t,z),v(l,t,z));}
    @Override public void render(Tiles.Rack rack,double x,double y,double z,float partial,int destroyStage,float alpha){
        if(rack.role==-1||rack.getWorld()==null||!(rack.getWorld().getBlockState(rack.getPos()).getBlock() instanceof Blocks.Rack))return;
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y,z+.5);GlStateManager.rotate(180-rack.getWorld().getBlockState(rack.getPos()).getValue(Blocks.FACING).getHorizontalIndex()*90,0,1,0);GlStateManager.translate(-.5,0,-.5);GlStateManager.disableLighting();GlStateManager.disableCull();
        try{double height=rack.role==1?2:1;double pitch=rack.role==1?1.875/9:.875/4;
            for(int i=0;i<rack.capacity();i++){ItemStack s=rack.slots.get(i);int u=Hardware.rackUnits(s);if(u==0)continue;double bottom=.0625+i*pitch+.009,top=.0625+(i+u)*pitch-.009;
                body(.12,bottom,.88,top,.115,.90);int st=Hardware.switchTier(s);String tex;
                if(st>0){tex="switch_panel_"+st;front(tex,.12,bottom,.88,top,.11);double width=.76/16;for(int p=0;p<Hardware.ports(st);p++){boolean lit=rack.switchOnline&&rack.ports[p]>0&&(rack.poweredMask&(1<<(rack.ports[p]-1)))!=0;front(lit?"led_on":"led_off",.12+(2+p*(st==1?3:1.65))*width,top-(top-bottom)*.20,.12+(3+p*(st==1?3:1.65))*width,top-(top-bottom)*.08,.108);}if(rack.switchOnline)front("led_on",.83,bottom+.05,.86,bottom+.075,.108);}
                else{boolean on=(rack.poweredMask&(1<<i))!=0,run=(rack.runningMask&(1<<i))!=0;tex="server_"+u+"_"+(Hardware.state(s)==ServerSpec.READY?"ready":"empty");front(tex,.12,bottom,.88,top,.11);front(on?"led_on":"led_off",.81,top-(top-bottom)*.22,.85,top-(top-bottom)*.10,.108);boolean blink=run&&(rack.getWorld().getTotalWorldTime()/5)%2==0;front(blink?"led_on":"led_off",.74,top-(top-bottom)*.22,.78,top-(top-bottom)*.10,.108);}}
            if(rack.getWorld().getBlockState(rack.getPos()).getBlock()==Hardware.RACK_GLASS){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);GlStateManager.disableAlpha();front("rack_window",.095,.0625,.905,height-.0625,.033);GlStateManager.enableAlpha();GlStateManager.depthMask(true);GlStateManager.disableBlend();}
        }finally{GlStateManager.color(1,1,1,1);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();}
    }
}
