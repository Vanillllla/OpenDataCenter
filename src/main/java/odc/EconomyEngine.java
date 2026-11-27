package odc;
/** Pure fixed-point economy arithmetic, in milli-coins (1 coin = 1000). */
final class EconomyEngine {
    static final long LIMIT=9_000_000_000_000L;
    static final long AI_GOAL=100000,AI_REWARD=250000;
    private EconomyEngine(){}
    static boolean transfer(long[] from,long[] to,int coin,long amount){if(from==to||coin<0||coin>=2||amount<=0||from[coin]<amount||to[coin]>LIMIT-amount)return false;from[coin]-=amount;to[coin]+=amount;return true;}
    static long credit(long[] balance,int coin,long amount){if(coin<0||coin>=2||amount<=0)return 0;long accepted=Math.min(amount,Math.max(0,LIMIT-balance[coin]));balance[coin]+=accepted;return accepted;}
    static boolean exchange(long[] balance,int coin,long amount,int rate){if(coin<1||coin>=2||amount<=0||amount>LIMIT||rate<1||rate>5000||balance[coin]<amount)return false;long result=amount*rate/1000;if(result<=0||balance[0]>LIMIT-result)return false;balance[coin]-=amount;balance[0]+=result;return true;}
    static long microReward(int work,int difficulty,int divisor){if(work<=0)return 0;return 2_000_000L*work/Math.max(1L,(long)Math.max(1,difficulty)*Math.max(1,divisor));}
    static int difficulty(int old,long work){int target=100+(int)Math.min(900,Math.max(0,work)/1200);return Math.max(100,Math.min(1000,(old*3+target)/4));}
    static int rate(int old,long seconds,int coin){return coin==0?1000:Math.max(500,Math.min(5000,old+(int)(Math.sin(seconds*.019+coin*4)*90)));}
    static long progress(long old,int work){return Math.max(0,Math.min(AI_GOAL,old+Math.max(0,work)));}
}
