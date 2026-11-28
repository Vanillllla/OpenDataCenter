package odc;
public final class ExternalWindowTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        Object a=new Object(),b=new Object(),unknown=new Object();
        TransformerCore.Input he=TransformerCore.Input.HE,fe=TransformerCore.Input.FE;
        ExternalWindow w=new ExternalWindow(100);
        w.add(a,60);w.add(b,80);
        check(w.offer(a,Long.MAX_VALUE,50,he,true)==1200,"simulated tier limit");
        check(w.paidWatts()==0&&!w.sufficient(),"simulation not paid");
        check(w.offer(unknown,100,50,he,false)==0,"unregistered source");
        check(w.offer(a,800,50,he,false)==800&&w.paidWatts()==40,"actual HE");
        check(w.offer(a,1000,100,fe,false)==200&&w.paidWatts()==60,"shared HE/FE tier limit");
        check(w.offer(a,1,50,he,false)==0,"no duplicate capacity");
        check(w.offer(b,1000,50,fe,false)==800&&w.sufficient(),"aggregate demand across transformers");
        check(w.paidWatts()==100&&w.offer(b,1,50,he,false)==0,"no excess draw");
        check(w.receipts.get(a).he==800&&w.receipts.get(a).fe==200,"unit accounting separate");
        w.close();check(w.offer(b,1,50,he,false)==0,"closed receipt cannot be reused");
        ExternalWindow next=new ExternalWindow(100);next.add(a,60);
        check(next.paidWatts()==0&&!next.sufficient(),"fresh window no carry");
        next.offer(a,Long.MAX_VALUE,50,he,false);check(!next.sufficient()&&next.paidWatts()==60,"partial supply");
        ExternalWindow round=new ExternalWindow(1);round.add(a,5);
        check(round.offer(a,10,333,he,false)==4&&round.paidWatts()==1&&round.sufficient(),"last unit rounding");
        ExternalWindow zero=new ExternalWindow(0);zero.add(a,100);
        check(zero.sufficient()&&zero.offer(a,100,50,fe,false)==0,"zero demand");
        ExternalWindow huge=new ExternalWindow(100000000);huge.add(a,100000000);
        check(huge.offer(a,Long.MAX_VALUE,1,he,false)==100000000000L&&huge.paidWatts()==100000000,"long HE units");
        for(int rate=1;rate<150;rate++)for(int demand=1;demand<80;demand++){
            ExternalWindow x=new ExternalWindow(demand);x.add(a,100);
            long u=x.offer(a,Long.MAX_VALUE,rate,fe,false);
            check(x.sufficient()&&x.paidWatts()==demand&&u*rate>=demand*1000L,"conservative conversion");
            check((u-1)*rate<demand*1000L,"minimal units");
        }
        System.out.println("ExternalWindow: "+checks+" checks passed");
    }
}
