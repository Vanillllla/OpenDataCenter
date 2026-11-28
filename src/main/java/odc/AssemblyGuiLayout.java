package odc;

/** Virtual screen dimensions keep GuiContainer drawing and vanilla input in
 * the same coordinate system without changing the user's global GUI scale. */
final class AssemblyGuiLayout {
    static final int WIDTH=300, MACHINE_HEIGHT=220, INVENTORY_X=62, INVENTORY_Y=222;
    static final int INVENTORY_WIDTH=176, INVENTORY_HEIGHT=96, HEIGHT=INVENTORY_Y+INVENTORY_HEIGHT;
    static final int MARGIN=6;
    final int width,height;
    AssemblyGuiLayout(int availableWidth,int availableHeight){
        int w=Math.max(1,availableWidth),h=Math.max(1,availableHeight);
        double factor=Math.max(1,Math.max((WIDTH+2.0*MARGIN)/w,(HEIGHT+2.0*MARGIN)/h));
        width=(int)Math.ceil(w*factor);height=(int)Math.ceil(h*factor);
    }
    static int playerX(int column){return INVENTORY_X+8+column*18;}
    static int playerY(int row){return INVENTORY_Y+14+row*18;}
    static int hotbarY(){return INVENTORY_Y+72;}
}
