package dev.winefox.skyfarer.flight;

import java.util.List;

/** Exposed tops of actual collision boxes. Never snap a partial block to its cell height. */
public final class SupportSurface {
    private SupportSurface() {}
    public static double nearest(List<FlightCorridor.Box> boxes,double x,double z,double reference,double maximumStep) {
        return nearest(boxes,x,z,reference,maximumStep,0);
    }
    public static double nearest(List<FlightCorridor.Box> boxes,double x,double z,double reference,double maximumStep,double headroom) {
        double result=Double.NaN,error=maximumStep;
        for(var box:boxes) {
            if(!covers(box,x,z)) continue;
            double top=box.maxY(),difference=Math.abs(top-reference);
            if(difference>=error) continue;
            boolean buried=false;
            for(var other:boxes) if(covers(other,x,z) && other.maxY()>top+1e-7
                && (other.minY()<=top+1e-7 || other.minY()<top+headroom-1e-7)) {
                buried=true;break;
            }
            if(!buried) {result=top;error=difference;}
        }
        return result;
    }
    private static boolean covers(FlightCorridor.Box b,double x,double z) {
        return x>=b.minX() && x<=b.maxX() && z>=b.minZ() && z<=b.maxZ();
    }
}
