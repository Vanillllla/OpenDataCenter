package odc;

/** Slots are indexed bottom to top. Slot 4 is the extra bridge slot. */
public final class RackLayout {
    private RackLayout(){}
    public static boolean validSize(int u){return u==1||u==2||u==4;}
    public static int startAt(int[] sizes,int bay){if(bay<0||bay>=sizes.length)return -1;for(int i=0;i<=bay;i++)if(validSize(sizes[i])&&i+sizes[i]<=sizes.length&&i+sizes[i]>bay)return i;return -1;}
    public static boolean canInsert(int[] sizes,int index,int units){if(index<0||!validSize(units)||index+units>sizes.length)return false;for(int j=0;j<sizes.length;j++)if(j!=index&&validSize(sizes[j])&&j<index+units&&index<j+sizes[j])return false;return true;}
    public static int retainedIndex(int index,int size,boolean keepUpper){int base=keepUpper?5:0;return validSize(size)&&index>=base&&index+size<=base+4?index-base:-1;}
}
