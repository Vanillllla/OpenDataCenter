package odc;
import java.util.*;
/** Persistent map coordinates, independent of world coordinates. */
final class MapEdits {
 static final int LIMIT=1000000, GRID=12;
 final Map<UUID,NetworkMapLayout.Point> positions=new HashMap<>();
 static int coordinate(double v,boolean snap){if(!Double.isFinite(v))return 0;v=Math.max(-LIMIT,Math.min(LIMIT,v));return (int)Math.max(-LIMIT,Math.min(LIMIT,snap?Math.round(v/GRID)*GRID:Math.round(v)));}
 boolean put(UUID id,int x,int y){if(id==null||Math.abs((long)x)>LIMIT||Math.abs((long)y)>LIMIT)return false;positions.put(id,new NetworkMapLayout.Point(x,y));return true;}
 void reset(Collection<UUID> ids){for(UUID id:ids)positions.remove(id);}
 static double distance(double x,double y,double a,double b,double c,double d){double dx=c-a,dy=d-b,den=dx*dx+dy*dy,t=den==0?0:Math.max(0,Math.min(1,((x-a)*dx+(y-b)*dy)/den));return Math.hypot(x-a-t*dx,y-b-t*dy);}
 static int loadState(double used,double cap,int state){return state!=0||used<0?4:used<=1e-7?0:cap<=0||used>=cap-1e-5?3:used>=cap*.8?2:1;}
}
