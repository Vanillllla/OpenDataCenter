package odc;

/** Shared, side-independent task action rules. Packet handling still checks identity and permissions. */
final class TaskActions {
    static boolean has(int mask,int op){return op>=0&&op<31&&(mask&(1<<op))!=0;}
    static boolean authorized(java.util.UUID selected,java.util.UUID requested,int mask,int op){return selected.equals(requested)&&has(mask,op);}
    static boolean matches(java.util.UUID selected,int type,java.util.UUID key,int rowType){return type>=0&&type==rowType&&selected.equals(key);}
    static int mask(boolean online,int section,boolean valid,boolean job,boolean enabled,boolean periodic,boolean continuous,long notice,long now,boolean affordable){
        if(!online||!valid||section==5)return 0;
        if(!job)return section==2?(affordable?1<<2:0):(section==1||section==4?1<<3:0);
        if(section!=0&&section!=3&&section!=4)return 0;
        boolean ready=notice>0&&now>=notice;
        int m=0;
        if(!periodic||!enabled||ready)m|=(1<<3)|(1<<10);
        if(enabled&&(!periodic||ready)&&!continuous)m|=1<<7;
        if(periodic&&enabled&&notice==0)m|=1<<8;
        if(periodic&&enabled&&notice>now)m|=1<<9;
        return m;
    }
}
