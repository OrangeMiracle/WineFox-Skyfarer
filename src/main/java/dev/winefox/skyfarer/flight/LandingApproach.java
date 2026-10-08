package dev.winefox.skyfarer.flight;

/** Bounded live guard for the actual final-descent control law. Ground contact is an intended
 * terminal state only at a supported, standing-clear point; walls and unknown cells still veto. */
public final class LandingApproach {
    private LandingApproach(){}
    @FunctionalInterface public interface Support { boolean accepts(double x,double feetY,double z); }
    public static boolean clear(FlightPrediction.State start,double x,double feetY,double z,double eye,
                                int attached,double gravity,double yawRate,double pitchRate,
                                GlidePlanner.Corridor space,Support support){
        var s=start;
        for(int tick=0;tick<32;tick++){
            double dx=x-s.x(),dz=z-s.z();
            double yaw=Math.toDegrees(Math.atan2(-dx,dz));
            double pitch=Math.clamp(-Math.toDegrees(Math.atan2(feetY-s.y(),Math.hypot(dx,dz))),-60,60);
            var next=FlightPrediction.step(s,yaw,pitch,yawRate,pitchRate,pitchRate,gravity,RocketProfile.active(tick,attached,start));
            if(next.vy()<0&&next.y()-eye<=feetY){
                double fraction=(s.y()-eye-feetY)/(s.y()-next.y());
                if(fraction<0||fraction>1)return false;
                var touchdown=new FlightPrediction.State(s.x()+(next.x()-s.x())*fraction,feetY+eye,
                    s.z()+(next.z()-s.z())*fraction,next.vx(),next.vy(),next.vz(),next.yaw(),next.pitch(),next.physics());
                return space.clear(s,touchdown,.02,0)&&support.accepts(touchdown.x(),feetY,touchdown.z());
            }
            if(!space.clear(s,next,.15,.12)){
                // Near the certified ground plane, padding intersects the floor before the body.
                // Remove only that padding; still require an actually clear body and standing room.
                if(next.y()-eye>feetY+.25||next.vy()>=0||!space.clear(s,next,.02,0)
                    ||!support.accepts(next.x(),feetY,next.z()))return false;
            }
            s=next;
        }
        return true;
    }
}
