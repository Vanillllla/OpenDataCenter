package odc;
import java.util.*;
/** Pure map geometry, independent of rendering and server refreshes. */
final class NetworkMapLayout {
    static final int CARD_W=144,CARD_H=57,DX=170,DY=105;
    static final class Point {final double x,y;Point(double x,double y){this.x=x;this.y=y;}}
    final Map<UUID,Point> points=new LinkedHashMap<>();double zoom=1,panX,panY;boolean initialized;
    void update(Map<UUID,Integer> layers){
        points.keySet().retainAll(layers.keySet());
        for(int layer=0;layer<4;layer++){List<UUID> ids=new ArrayList<>();for(Map.Entry<UUID,Integer> e:layers.entrySet())if(e.getValue()==layer)ids.add(e.getKey());Collections.sort(ids);
            // Keep surviving cards fixed, insert new cards in free cells on their row.
            if(!initialized){for(int i=0;i<ids.size();i++)points.put(ids.get(i),new Point((i-(ids.size()-1)/2.0)*DX,layer*DY));}
            else for(UUID id:ids)if(!points.containsKey(id)){int index=0;double x;while(true){x=index==0?0:((index+1)/2)*(index%2==1?DX:-DX);boolean free=true;for(Point p:points.values())if(p.y==layer*DY&&Math.abs(p.x-x)<CARD_W+8){free=false;break;}if(free)break;index++;}points.put(id,new Point(x,layer*DY));}
        }initialized=true;
    }
    void fit(int w,int h){if(points.isEmpty()){zoom=1;panX=w/2.0;panY=h/2.0;return;}double x1=Double.POSITIVE_INFINITY,x2=-x1,y1=x1,y2=-x1;for(Point p:points.values()){x1=Math.min(x1,p.x);x2=Math.max(x2,p.x+CARD_W);y1=Math.min(y1,p.y);y2=Math.max(y2,p.y+CARD_H);}zoom=Math.max(.001,Math.min(1.25,Math.min((w-24)/(x2-x1),(h-24)/(y2-y1))));panX=w/2.0-(x1+x2)*zoom/2;panY=h/2.0-(y1+y2)*zoom/2;}
    void center(UUID id,int w,int h){Point p=points.get(id);if(p!=null){zoom=Math.max(.8,zoom);panX=w/2.0-(p.x+CARD_W/2.0)*zoom;panY=h/2.0-(p.y+CARD_H/2.0)*zoom;}}
    void zoomAt(double factor,double x,double y){double z=Math.max(.001,Math.min(2,zoom*factor));panX=x-(x-panX)*z/zoom;panY=y-(y-panY)*z/zoom;zoom=z;}
    // Compact cards use integer screen-space geometry. Stored world positions never change.
    boolean compact(){return zoom<.75;}
    static final class Bounds {
        final int x,y,w,h;
        Bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        int cx(){return x+w/2;}
        int cy(){return y+h/2;}
        boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}
    }
    Bounds mini(Point p){
        int w=Math.max(3,Math.min(64,(int)Math.round(CARD_W*zoom)));
        int h=Math.max(3,Math.min(24,(int)Math.round(CARD_H*zoom)));
        return new Bounds((int)Math.round((p.x+CARD_W/2.0)*zoom+panX-w/2.0),
            (int)Math.round((p.y+CARD_H/2.0)*zoom+panY-h/2.0),w,h);
    }
    int[] miniPath(Point p,Point q,UUID id){
        Bounds a=mini(p),b=mini(q);int y=a.y+a.h,yy=b.y;
        int mid=(y+yy)/2+(int)Math.round((Math.floorMod(id.hashCode(),7)-3)*3*zoom);
        return new int[]{a.cx(),y,b.cx(),yy,mid};
    }
    UUID hit(double x,double y){
        if(compact()){UUID best=null;double distance=Double.POSITIVE_INFINITY;
            for(Map.Entry<UUID,Point> e:points.entrySet()){Bounds b=mini(e.getValue());if(!b.contains(x,y))continue;
                double d=(x-b.cx())*(x-b.cx())+(y-b.cy())*(y-b.cy());
                if(d<distance){distance=d;best=e.getKey();}}
            return best;
        }double wx=(x-panX)/zoom,wy=(y-panY)/zoom;for(Map.Entry<UUID,Point> e:points.entrySet()){Point p=e.getValue();if(wx>=p.x&&wx<p.x+CARD_W&&wy>=p.y&&wy<p.y+CARD_H)return e.getKey();}return null;}
}
