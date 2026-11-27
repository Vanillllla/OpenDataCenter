package odc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Atlas-batched module mesh: one opaque draw, plus one glass draw if needed. */
final class RackRenderer extends TileEntitySpecialRenderer<Tiles.Rack> {
    static final String[] SPRITES={"server_1_ready","server_1_empty","server_2_ready","server_2_empty","server_4_ready","server_4_empty","switch_panel_1","switch_panel_2","led_on","led_off","led_activity","rack_window","module_side","patch_cable_mesh","patch_cable_mesh_t1","patch_cable_mesh_t3","patch_plug_mesh"};
    private BufferBuilder buffer;
    private TextureMap atlas;
    private void begin(){buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);}
    private void end(){Tessellator.getInstance().draw();}
    private static double[] v(double x,double y,double z){return new double[]{x,y,z};}
    private void vertex(double[] p,float u,float v,int shade){buffer.pos(p[0],p[1],p[2]).tex(u,v).color(shade,shade,shade,255).endVertex();}
    private void quad(String tex,int shade,double[] a,double[] b,double[] c,double[] d){
        TextureAtlasSprite s=atlas.getAtlasSprite(ODC.ID+":blocks/"+tex);
        vertex(a,s.getMinU(),s.getMaxV(),shade);vertex(b,s.getMaxU(),s.getMaxV(),shade);
        vertex(c,s.getMaxU(),s.getMinV(),shade);vertex(d,s.getMinU(),s.getMinV(),shade);
    }
    private void front(String tex,double l,double b,double r,double t,double z){quad(tex,255,v(l,b,z),v(r,b,z),v(r,t,z),v(l,t,z));}
    private void body(double l,double b,double r,double t,double z,double back){
        quad("module_side",170,v(l,b,z),v(l,b,back),v(l,t,back),v(l,t,z));
        quad("module_side",195,v(r,b,back),v(r,b,z),v(r,t,z),v(r,t,back));
        quad("module_side",170,v(l,b,back),v(r,b,back),v(r,t,back),v(l,t,back));
        quad("module_side",220,v(l,t,z),v(r,t,z),v(r,t,back),v(l,t,back));
        quad("module_side",150,v(l,b,back),v(r,b,back),v(r,b,z),v(l,b,z));
    }
    @Override public void render(Tiles.Rack rack,double x,double y,double z,float partial,int destroyStage,float alpha){
        if(rack.role==-1||rack.getWorld()==null||!(rack.getWorld().getBlockState(rack.getPos()).getBlock() instanceof Blocks.Rack))return;
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y,z+.5);GlStateManager.rotate(180-rack.getWorld().getBlockState(rack.getPos()).getValue(Blocks.FACING).getHorizontalIndex()*90,0,1,0);GlStateManager.translate(-.5,0,-.5);
        GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);
        atlas=Minecraft.getMinecraft().getTextureMapBlocks();bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        try{
            double height=rack.role==1?2:1,pitch=rack.role==1?1.875/9:.875/4;
            begin();
            for(int i=0;i<rack.capacity();i++){
                ItemStack item=rack.slots.get(i);int units=Hardware.rackUnits(item);if(units==0)continue;
                double bottom=.0625+i*pitch+.012,top=.0625+(i+units)*pitch-.012,h=top-bottom;
                body(.125,bottom,.875,top,.15,.90);
                int st=Hardware.switchTier(item);
                if(st>0){
                    front("switch_panel_"+st,.125,bottom,.875,top,.15);
                    int n=Hardware.ports(st);
                    for(int p=0;p<n;p++){
                        double cx=PatchCordMesh.portX(p,n);int bay=rack.ports[p]-1;
                        boolean connected=bay>=0&&bay<rack.capacity()&&rack.isServer(bay);
                        boolean lit=connected&&rack.switchOnline&&(rack.poweredMask&(1<<bay))!=0;
                        front(lit?"led_on":"led_off",cx-.012,top-h*.20,cx+.012,top-h*.10,.148);
                        if(connected){
                            for(PatchCordMesh.Face face:PatchCordMesh.get(rack.capacity(),i,n,p,bay>i)){
                                double[][] q=face.vertices;
                                quad(face.material==PatchCordMesh.CABLE?(rack.cableTier(p)==1?"patch_cable_mesh_t1":rack.cableTier(p)==3?"patch_cable_mesh_t3":"patch_cable_mesh"):"patch_plug_mesh",face.shade,q[0],q[1],q[2],q[3]);
                            }
                        }
                    }
                }else{
                    boolean on=(rack.poweredMask&(1<<i))!=0,run=(rack.runningMask&(1<<i))!=0;
                    front("server_"+units+"_"+(Hardware.state(item)==ServerSpec.READY?"ready":"empty"),.125,bottom,.875,top,.15);
                    front(on?"led_on":"led_off",.804,top-h*.27,.850,top-h*.10,.148);
                    front(run?"led_activity":"led_off",.750,top-h*.27,.786,top-h*.10,.148);
                }
            }
            end();
            if(rack.getWorld().getBlockState(rack.getPos()).getBlock()==Hardware.RACK_GLASS){
                GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);GlStateManager.disableAlpha();
                begin();front("rack_window",.10,.065,.90,height-.065,.038);end();
                GlStateManager.enableAlpha();GlStateManager.depthMask(true);GlStateManager.disableBlend();
            }
        }finally{GlStateManager.color(1,1,1,1);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();}
    }
}
