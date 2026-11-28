package odc;

import java.util.*;

/** Shared, renderer-independent DataOS table/filter and scrolling primitives. */
final class DataOSUiState {
    // Preserve wire IDs so task, event and pool navigation need no renumbering.
    private static final int[] TABS={0,1,2,3,5,4,6,7};
    static final int TAB_LEFT=5,TAB_STEP=58;
    static int tabId(int index){return index>=0&&index<TABS.length?TABS[index]:-1;}
    static boolean validTab(int tab){return tab>=0&&tab<8;}
    static int tabAt(int x,int y){
        int dx=x-TAB_LEFT;
        return y>=27&&y<44&&dx>=0&&dx%TAB_STEP<TAB_STEP-1?tabId(dx/TAB_STEP):-1;
    }
    static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    static boolean hit(int x,int y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    static boolean matches(String query,String... fields){String q=query.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return true;for(String s:fields)if(s!=null&&s.toLowerCase(Locale.ROOT).contains(q))return true;return false;}
    static String edit(String value,char c,boolean back,boolean clear,int limit){if(clear)return "";if(back)return value.isEmpty()?value:value.substring(0,value.length()-1);return !Character.isISOControl(c)&&c!=167&&value.length()<limit?value+c:value;}
    static final class Scroll {
        final int view,content,max,thumb,room;
        Scroll(int view,int content){this.view=Math.max(1,view);this.content=Math.max(0,content);max=Math.max(0,this.content-this.view);thumb=Math.min(this.view,Math.max(10,(int)((long)this.view*this.view/Math.max(this.view,this.content))));room=this.view-thumb;}
        int position(int value){return max==0?0:(int)((long)room*clamp(value,0,max)/max);}
        int value(int position){return room==0?0:clamp((int)((long)position*max/room),0,max);}
    }
    static final class Query {
        final String search,pool;final int power,column;final boolean descending;
        Query(String search,String pool,int power,int column,boolean descending){this.search=search==null?"":search.replace('\n',' ').replace('\r',' ');this.pool=pool==null?"":pool.replace('\n',' ').replace('\r',' ');this.power=clamp(power,0,3);this.column=clamp(column,0,8);this.descending=descending;}
        String encode(){return search+"\n"+pool+"\n"+power+"\n"+column+"\n"+(descending?1:0);}
        static Query decode(String raw){if(raw==null||raw.length()>150)return defaults();String[] p=raw.split("\n",-1);if(p.length!=5||p[0].length()>64||p[1].length()>32)return defaults();try{return new Query(p[0],p[1],Integer.parseInt(p[2]),Integer.parseInt(p[3]),"1".equals(p[4]));}catch(NumberFormatException e){return defaults();}}
        static Query defaults(){return new Query("","",0,1,false);}
        boolean accepts(String name,int id,String pool,boolean enabled,boolean powered){return matches(search,name,Integer.toString(id),"#"+id)&&(this.pool.isEmpty()||this.pool.equals(pool))&&(power==0||power==1&&powered||power==2&&!enabled||power==3&&enabled&&!powered);}
    }
    static <T> Comparator<T> ordered(Comparator<T> main,Comparator<T> tie,boolean reverse){return (reverse?main.reversed():main).thenComparing(tie);}
    static final class History<T> {
        private final Deque<T> entries=new ArrayDeque<>();
        void push(T state){if(entries.size()>=32)entries.removeFirst();entries.addLast(state);}
        T pop(){return entries.pollLast();}boolean available(){return !entries.isEmpty();}
    }
}
