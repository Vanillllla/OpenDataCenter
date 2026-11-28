package odc;
public final class TransformerCoreTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(TransformerCore.defaultWatts(1)==5000&&TransformerCore.defaultWatts(2)==25000&&TransformerCore.defaultWatts(3)==100000,"tier defaults");
        check(TransformerCore.energyMilliJoules(1000,20)==1000000,"20 TPS units");
        check(TransformerCore.requiredUnits(1,1,100)==1,"round up");
        check(TransformerCore.paidWatts(1,1,100,5000)==2,"paid conversion");
        check(TransformerCore.paidWatts(Long.MAX_VALUE,20,100000000,5000)==5000,"rating clamps before overflow");
        check(TransformerCore.deliver(null,true,5000,5000,20,50).watts==0,"no adapter no energy");
        long[] requests={0,0};
        TransformerCore.ExternalInput supply=(n,simulate)->{check(!simulate,"actual debit");requests[0]++;requests[1]=n;return n;};
        check(TransformerCore.deliver(supply,false,5000,5000,20,50).watts==0&&requests[0]==0,"switch open");
        check(TransformerCore.deliver(supply,true,5000,0,20,50).units==0&&requests[0]==0,"zero demand");
        TransformerCore.Delivery d=TransformerCore.deliver(supply,true,5000,25000,20,50);
        check(d.watts==5000&&d.units==100000&&requests[1]==100000,"tier cap");
        d=TransformerCore.deliver(supply,true,5000,100,20,50);
        check(d.watts==100&&d.units==2000,"demand cap");
        d=TransformerCore.deliver((n,s)->n/2,true,5000,100,20,50);
        check(d.watts==50&&d.units==1000,"partial delivery");
        check(TransformerCore.deliver((n,s)->0,true,5000,100,20,50).watts==0,"no payment");
        boolean failed=false;try{TransformerCore.deliver((n,s)->n+1,true,5000,100,20,50);}catch(IllegalStateException e){failed=true;}
        check(failed,"invalid debit rejected");
        for(int rate=1;rate<200;rate++)for(int w=0;w<100;w++){
            long units=TransformerCore.requiredUnits(w,20,rate);
            check(units*rate>=TransformerCore.energyMilliJoules(w,20),"no rounding deficit");
            check(units==0||(units-1)*rate<TransformerCore.energyMilliJoules(w,20),"minimal debit");
            TransformerCore.Delivery q=TransformerCore.deliver((n,s)->n,true,5000,w,20,rate);
            check(q.watts==w&&q.units*rate>=TransformerCore.energyMilliJoules(q.watts,20),"paid delivery");
        }
        PowerEngine.Budget grid=new PowerEngine.Budget(PowerEngine.sum(50000,0),20);
        check(grid.capacityWatts==50000&&grid.use(50000)&&!grid.use(1),"unpaid transformer cannot add supply");
        System.out.println("TransformerCore: "+checks+" checks passed");
    }
}
