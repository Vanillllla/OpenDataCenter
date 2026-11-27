package odc;
import java.util.UUID;
public final class TaskActionsTest {
    static void eq(int expected,int actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    static int m(int... ops){int mask=0;for(int op:ops)mask|=1<<op;return mask;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        eq(0,TaskActions.mask(true,0,false,true,true,true,false,0,100,true));
        eq(0,TaskActions.mask(false,0,true,true,true,true,false,0,100,true));
        eq(0,TaskActions.mask(true,5,true,true,true,true,false,0,100,true));
        eq(m(2),TaskActions.mask(true,2,true,false,false,false,false,0,100,true));
        eq(0,TaskActions.mask(true,2,true,false,false,false,false,0,100,false));
        eq(m(3),TaskActions.mask(true,1,true,false,false,false,false,0,100,true));
        eq(m(3),TaskActions.mask(true,4,true,false,false,false,false,0,100,true));
        eq(m(3,7,10),TaskActions.mask(true,0,true,true,true,false,false,0,100,true));
        eq(m(3,10),TaskActions.mask(true,3,true,true,true,false,true,0,100,true));
        eq(m(3,10),TaskActions.mask(true,0,true,true,false,false,false,0,100,true));
        eq(m(8),TaskActions.mask(true,4,true,true,true,true,false,0,100,true));
        eq(m(9),TaskActions.mask(true,4,true,true,true,true,false,101,100,true));
        eq(m(3,7,10),TaskActions.mask(true,4,true,true,true,true,false,100,100,true));
        eq(m(3,10),TaskActions.mask(true,4,true,true,false,true,false,0,100,true));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),none=new UUID(0,0);
        check(TaskActions.authorized(a,a,m(8),8));check(!TaskActions.authorized(a,b,m(8),8));check(!TaskActions.authorized(a,a,m(7),8));
        check(TaskActions.matches(a,21,a,21));check(!TaskActions.matches(a,21,b,21));check(!TaskActions.matches(none,1,none,2));check(!TaskActions.matches(none,-1,none,0));
        System.out.println("PASS TaskActionsTest (21 checks)");
    }
}
