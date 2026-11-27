package odc;

/** Slots are indexed bottom to top. Slot 4 is the extra bridge slot. */
public final class RackLayout {
    private RackLayout(){}
    /** Shared animation and ray geometry, with the front of the rack facing local -Z. */
    static final class Door {
        static final double LEFT=.10, RIGHT=.90, Z=.038, BOTTOM=.065, THICK=.026;
        static final int TICKS=10;
        static double clamp(double n){return Double.isFinite(n)?Math.max(0,Math.min(1,n)):0;}
        static double progress(boolean open,double from,double elapsed){
            double p=clamp(from),step=Math.max(0,elapsed)/TICKS;
            return open?Math.min(1,p+step):Math.max(0,p-step);
        }
        static double angle(double progress){double p=clamp(progress);return 90*p*p*(3-2*p);}
        static boolean accessible(boolean glass,boolean open,double progress){return !glass||open&&progress>=1;}
        static double[] local(double x,double y,double z,int facing){
            double a=Math.toRadians(180-facing*90),c=Math.cos(a),s=Math.sin(a),dx=x-.5,dz=z-.5;
            return new double[]{.5+c*dx-s*dz,y,.5+s*dx+c*dz};
        }
        static double[] leaf(double[] p,double angle){
            double a=Math.toRadians(angle),c=Math.cos(a),s=Math.sin(a),dx=p[0]-LEFT,dz=p[2]-Z;
            return new double[]{LEFT+c*dx-s*dz,p[1],Z+s*dx+c*dz};
        }
        /** First intersection fraction on a finite segment; negative means a miss. */
        static double hit(double[] start,double[] end,double angle,double height){
            double[] a=leaf(start,angle),b=leaf(end,angle);
            double[] min={LEFT,BOTTOM,Z-THICK/2},max={RIGHT,height-BOTTOM,Z+THICK/2};
            double lo=0,hi=1;
            for(int i=0;i<3;i++){
                double d=b[i]-a[i];
                if(Math.abs(d)<1e-9){if(a[i]<min[i]-1e-9||a[i]>max[i]+1e-9)return -1;continue;}
                double t=(min[i]-a[i])/d,u=(max[i]-a[i])/d;
                lo=Math.max(lo,Math.min(t,u));hi=Math.min(hi,Math.max(t,u));if(lo>hi)return -1;
            }
            return lo;
        }
    }
    public static boolean validSize(int u){return u==1||u==2||u==4;}
    public static int startAt(int[] sizes,int bay){if(bay<0||bay>=sizes.length)return -1;for(int i=0;i<=bay;i++)if(validSize(sizes[i])&&i+sizes[i]<=sizes.length&&i+sizes[i]>bay)return i;return -1;}
    public static boolean canInsert(int[] sizes,int index,int units){if(index<0||!validSize(units)||index+units>sizes.length)return false;for(int j=0;j<sizes.length;j++)if(j!=index&&validSize(sizes[j])&&j<index+units&&index<j+sizes[j])return false;return true;}
    public static int retainedIndex(int index,int size,boolean keepUpper){int base=keepUpper?5:0;return validSize(size)&&index>=base&&index+size<=base+4?index-base:-1;}
}
