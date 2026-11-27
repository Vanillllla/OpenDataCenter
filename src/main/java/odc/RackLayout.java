package odc;

/** Four physical bays. A server occupies contiguous bays beginning at its inventory index. */
public final class RackLayout {
    private RackLayout(){}
    public static int startAt(int[] sizes,int bay){
        if(sizes.length!=4||bay<0||bay>=4)return -1;
        for(int i=0;i<=bay;i++)if(validSize(sizes[i])&&i+sizes[i]<=4&&i+sizes[i]>bay)return i;
        return -1;
    }
    public static boolean validSize(int units){return units==1||units==2||units==4;}
    public static boolean canInsert(int[] sizes,int index,int units){
        if(sizes.length!=4||index<0||!validSize(units)||index+units>4)return false;
        for(int j=0;j<4;j++){if(j==index)continue;int v=sizes[j];if(validSize(v)&&j<index+units&&index<j+v)return false;}
        return true;
    }
}
