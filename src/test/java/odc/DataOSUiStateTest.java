package odc;

import java.util.*;

/** Offline regression checks for shared DataOS controls and network query serialization. */
public final class DataOSUiStateTest {
    private static int checks;
    private static void check(boolean v){checks++;if(!v)throw new AssertionError("DataOS UI check "+checks);}
    public static void main(String[] args){
        int[] tabs={0,1,2,3,5,4,6,7};
        for(int i=0;i<tabs.length;i++){
            int x=DataOSUiState.TAB_LEFT+i*DataOSUiState.TAB_STEP;
            check(DataOSUiState.tabId(i)==tabs[i]&&DataOSUiState.validTab(tabs[i]));
            check(DataOSUiState.tabAt(x,27)==tabs[i]&&DataOSUiState.tabAt(x+DataOSUiState.TAB_STEP-2,43)==tabs[i]);
            check(DataOSUiState.tabAt(x+DataOSUiState.TAB_STEP-1,43)==-1&&DataOSUiState.tabAt(x,44)==-1);
        }
        check(DataOSUiState.validTab(4)&&!DataOSUiState.validTab(-1)&&!DataOSUiState.validTab(8));
        check(DataOSUiState.tabAt(4,30)==-1&&DataOSUiState.tabAt(469,30)==-1);
        DataOSUiState.Query q=new DataOSUiState.Query("gpu","Pool GPU",1,0,true);
        check(q.encode().equals(DataOSUiState.Query.decode(q.encode()).encode()));
        check(q.accepts("GPU-01",11,"Pool GPU",true,true));
        check(!q.accepts("CPU-01",11,"Pool GPU",true,true));
        check(!q.accepts("GPU-01",11,"Pool CPU",true,true));
        check(!q.accepts("GPU-01",11,"Pool GPU",true,false));
        check(new DataOSUiState.Query("#11","",0,1,false).accepts("Other",11,"Pool",false,false));
        check(new DataOSUiState.Query("сервер","",2,1,false).accepts("СЕРВЕР",0,"Pool",false,false));
        check(new DataOSUiState.Query("","",3,1,false).accepts("X",0,"Pool",true,false));
        check(!new DataOSUiState.Query("","",3,1,false).accepts("X",0,"Pool",false,false));
        check(DataOSUiState.Query.decode("invalid").encode().equals(DataOSUiState.Query.defaults().encode()));
        check(DataOSUiState.Query.decode("\n\n-1\n999\n1").column==8);
        check(DataOSUiState.Query.decode("\n\n-1\n999\n1").power==0);
        check(DataOSUiState.edit("abc",'x',false,false,3).equals("abc"));
        check(DataOSUiState.edit("abc",'x',true,false,3).equals("ab"));
        check(DataOSUiState.edit("abc",'x',false,true,3).isEmpty());
        check(DataOSUiState.edit("abc",'\n',false,false,9).equals("abc"));
        check(DataOSUiState.hit(10,10,10,10,5,5));check(!DataOSUiState.hit(15,14,10,10,5,5));
        for(int view=1;view<=360;view+=7)for(int content=0;content<=9000;content+=113){DataOSUiState.Scroll s=new DataOSUiState.Scroll(view,content);check(s.thumb<=s.view&&s.thumb>=1);check(s.position(0)==0&&s.value(0)==0);check(s.position(s.max)==s.room);if(s.room>0)check(s.value(s.room)==s.max);check(s.value(-10)==0);check(s.value(s.room+20)<=s.max);int last=0;for(int i=0;i<=s.room;i++){int n=s.value(i);check(n>=last&&n<=s.max);last=n;}}
        // Equal primary values preserve a stable secondary ID, including descending sorting.
        List<Integer> nums=new ArrayList<>(Arrays.asList(4,1,5,0,2,3));nums.sort(DataOSUiState.ordered(Comparator.comparingInt((Integer i)->i%2),Comparator.naturalOrder(),false));check(nums.equals(Arrays.asList(0,2,4,1,3,5)));nums.sort(DataOSUiState.ordered(Comparator.comparingInt((Integer i)->i%2),Comparator.naturalOrder(),true));check(nums.equals(Arrays.asList(1,3,5,0,2,4)));
        DataOSUiState.History<UUID> nav=new DataOSUiState.History<>();UUID a=UUID.randomUUID(),b=UUID.randomUUID();nav.push(a);nav.push(b);check(nav.available());check(nav.pop().equals(b));check(nav.pop().equals(a));check(!nav.available());check(nav.pop()==null);for(int i=0;i<40;i++)nav.push(new UUID(0,i));for(int i=39;i>=8;i--)check(nav.pop().equals(new UUID(0,i)));check(!nav.available());
        System.out.println("DataOS UI state: "+checks+" checks passed");
    }
}
