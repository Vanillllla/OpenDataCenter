package odc;
import java.util.*;
public final class MapEditsTest {
 public static void main(String[] args){int checks=0;MapEdits e=new MapEdits();Random r=new Random(617);UUID a=new UUID(0,1),b=new UUID(0,2);for(int i=0;i<10000;i++){int x=r.nextInt(3000000)-1500000,y=MapEdits.coordinate(x,true);if(Math.abs(y)>MapEdits.LIMIT||!e.put(a,y,y)||e.positions.get(a).x!=y)throw new AssertionError();checks+=3;}if(e.put(a,Integer.MIN_VALUE,0))throw new AssertionError();e.put(b,1,2);e.reset(Collections.singleton(a));if(e.positions.containsKey(a)||!e.positions.containsKey(b))throw new AssertionError();if(MapEdits.distance(5,3,0,0,10,0)!=3||MapEdits.distance(13,4,0,0,10,0)!=5)throw new AssertionError();for(int i=0;i<5;i++){int state=MapEdits.loadState(new double[]{0,10,90,100,0}[i],100,i==4?1:0);if(state!=i)throw new AssertionError();}System.out.println("MapEdits PASS: "+(checks+10)+" checks");}
}
