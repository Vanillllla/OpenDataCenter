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
    static final String[] SPRITES={"server_1_ready","server_1_empty","server_2_ready","server_2_empty","server_4_ready","server_4_empty","switch_panel_1","switch_panel_2","switch_panel_2port","led_on","led_off","led_activity","rack_window","rack_frame","module_side","patch_cable_mesh","patch_cable_mesh_t1","patch_cable_mesh_t3","patch_plug_mesh","pdu_rail","pdu_socket","power_plug","power_lead","psu_rear","psu_blank","server_rear","switch_rear","power_lamp_off","power_lamp_green","power_lamp_red"};
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
        quad("module_side",220,v(l,t,z),v(r,t,z),v(r,t,back),v(l,t,back));
        quad("module_side",150,v(l,b,back),v(r,b,back),v(r,b,z),v(l,b,z));
    }
    private void solidBox(double l,double b,double z,double r,double t,double back){
        String tex="rack_frame";
        quad(tex,225,v(l,b,z),v(r,b,z),v(r,t,z),v(l,t,z));
        quad(tex,175,v(r,b,back),v(l,b,back),v(l,t,back),v(r,t,back));
        quad(tex,185,v(l,b,back),v(l,b,z),v(l,t,z),v(l,t,back));
        quad(tex,205,v(r,b,z),v(r,b,back),v(r,t,back),v(r,t,z));
        quad(tex,235,v(l,t,z),v(r,t,z),v(r,t,back),v(l,t,back));
        quad(tex,155,v(l,b,back),v(r,b,back),v(r,b,z),v(l,b,z));
    }
    private void rear(String tex,double l,double b,double r,double t,double z){quad(tex,230,v(r,b,z),v(l,b,z),v(l,t,z),v(r,t,z));}
    private void powerFaces(RackPowerMesh.Face[] faces){
        for(RackPowerMesh.Face face:faces){double[][] q=face.vertices;quad(RackPowerMesh.TEXTURES[face.material],face.shade,q[0],q[1],q[2],q[3]);}
    }
    private void renderDoor(Tiles.Rack rack,double height,float partial){
        double l=RackLayout.Door.LEFT,r=RackLayout.Door.RIGHT,b=RackLayout.Door.BOTTOM,t=height-b,z=RackLayout.Door.Z,th=RackLayout.Door.THICK/2,edge=.026;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(l,0,z);GlStateManager.rotate((float)RackLayout.Door.angle(rack.doorProgress(partial)),0,1,0);GlStateManager.translate(-l,0,-z);
            begin();
            solidBox(l,b,z-th,l+edge,t,z+th);solidBox(r-edge,b,z-th,r,t,z+th);
            solidBox(l+edge,b,z-th,r-edge,b+edge,z+th);solidBox(l+edge,t-edge,z-th,r-edge,t,z+th);
            // A single raised handle travels with the door, at a convenient height in both rack sizes.
            double hy=height==2?.88:.40,hx=r-.075;
            solidBox(hx,hy,z-.047,hx+.027,hy+.035,z-th);
            solidBox(hx,hy+.145,z-.047,hx+.027,hy+.18,z-th);
            solidBox(hx,hy,z-.060,hx+.027,hy+.18,z-.043);
            end();
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);GlStateManager.disableAlpha();
            try{begin();front("rack_window",l+edge,b+edge,r-edge,t-edge,z);end();}
            finally{GlStateManager.enableAlpha();GlStateManager.depthMask(true);GlStateManager.disableBlend();}
        }finally{GlStateManager.popMatrix();}
    }
    @Override public void render(Tiles.Rack rack,double x,double y,double z,float partial,int destroyStage,float alpha){
        if(rack.role==-1||rack.getWorld()==null||!(rack.getWorld().getBlockState(rack.getPos()).getBlock() instanceof Blocks.Rack))return;
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5,y,z+.5);GlStateManager.rotate(180-rack.getWorld().getBlockState(rack.getPos()).getValue(Blocks.FACING).getHorizontalIndex()*90,0,1,0);GlStateManager.translate(-.5,0,-.5);
        GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);
        atlas=Minecraft.getMinecraft().getTextureMapBlocks();bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        try{
            double height=rack.role==1?2:1,pitch=rack.role==1?1.875/9:.875/4;
            begin();
            powerFaces(RackPowerMesh.rail(rack.capacity()));
            // Temporary rear 0U rail; final paired hoses/materials in 0.6.3.8.
            if(rack.manifold)solidBox(.105,.065,.925,.175,height-.065,.985);
            String lamp=rack.powerLamp==GridProtection.LAMP_RED?"power_lamp_red":rack.powerLamp==GridProtection.LAMP_GREEN?"power_lamp_green":"power_lamp_off";
            quad(lamp,255,v(.875,height-.123,.986),v(.845,height-.123,.986),v(.845,height-.093,.986),v(.875,height-.093,.986));
            for(int i=0;i<rack.capacity();i++){
                ItemStack item=rack.slots.get(i);int units=Hardware.rackUnits(item);if(units==0)continue;
                double bottom=.0625+i*pitch+.012,top=.0625+(i+units)*pitch-.012,h=top-bottom;
                body(.125,bottom,.875,top,.15,.90);
                int st=Hardware.switchTier(item);
                if(st>0){
                    rear("switch_rear",.125,bottom,.875,top,RackPowerMesh.BACK);
                    powerFaces(RackPowerMesh.lane(rack.capacity(),i,true));
                    front(SwitchLimits.panel(st),.125,bottom,.875,top,.15);
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
                    rear("server_rear",.125,bottom,.875,top,RackPowerMesh.BACK);
                    int[] spec=Hardware.spec(item);ChassisSpec chassis=Hardware.chassis(item);int psu=RackPowerMesh.psuMask(chassis,spec);
                    for(int slot=0;slot<chassis.psuSlots&&i+slot<rack.capacity();slot++)powerFaces(RackPowerMesh.lane(rack.capacity(),i+slot,(psu&(1<<slot))!=0));
                    boolean on=(rack.poweredMask&(1<<i))!=0,run=(rack.runningMask&(1<<i))!=0;
                    front("server_"+units+"_"+(ServerSpec.state(chassis,spec)==ServerSpec.READY?"ready":"empty"),.125,bottom,.875,top,.15);
                    front(on?"led_on":"led_off",.804,top-h*.27,.850,top-h*.10,.148);
                    front(run?"led_activity":"led_off",.750,top-h*.27,.786,top-h*.10,.148);
                }
            }
            end();
            if(rack.glassRack())renderDoor(rack,height,partial);
        }finally{GlStateManager.color(1,1,1,1);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();}
    }
}
