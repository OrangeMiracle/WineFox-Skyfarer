package dev.winefox.skyfarer.command;

import dev.winefox.skyfarer.flight.*;

/** Creative travel only: a bounded speed envelope with full-body swept validation before movement. */
public final class CreativeTransitPlanner {
    public static final double DIVE_HORIZONTAL=64,BASE_SPEED=1.7;
    public record Motion(FlightPrediction.State input,FlightPrediction.State next,double yaw,double pitch,double multiplier){}
    public static double lookahead(double multiplier){return Math.clamp(BASE_SPEED*multiplier*4+32,48,480);}
    public static double cruiseHeight(double current,double target,double horizontal,double requested,double ceiling){
        return Math.min(ceiling,Math.min(Math.max(requested,target+24),current+Math.max(0,horizontal-DIVE_HORIZONTAL)*.8));
    }
    public static Motion choose(FlightPrediction.State state,double x,double y,double z,double targetDistance,double horizontal,
        boolean descending,double multiplier,double gravity,GlidePlanner.Corridor swept){
        double dx=x-state.x(),dy=y-state.y(),dz=z-state.z(),length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<.01)return null;
        double yaw=Math.toDegrees(Math.atan2(-dx,dz)),pitch=Math.clamp(-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),-80,80);
        // Smooth visible orientation; creative speed scales travel, not combat energy or damage.
        yaw=FlightPrediction.turn(state.yaw(),yaw,12);pitch=FlightPrediction.turn(state.pitch(),pitch,10);
        double scale=Math.min(Math.clamp(multiplier,1,100),Math.max(1,(targetDistance-DispatchPolicy.CREATIVE_HANDOFF_RANGE)/8));
        double speed=BASE_SPEED*scale;
        if(!descending&&horizontal>DIVE_HORIZONTAL)speed=Math.min(speed,Math.max(.25,(horizontal-DIVE_HORIZONTAL)/Math.max(.1,Math.hypot(dx,dz)/length)));
        speed=Math.min(speed,Math.max(.25,(targetDistance-DispatchPolicy.CREATIVE_HANDOFF_RANGE)*.65));
        // Shorter steps deal with new obstacles or chunk activation latency; never jump an unchecked gap.
        for(int attempt=0;attempt<12;attempt++,speed*=.5){
            var input=new FlightPrediction.State(state.x(),state.y(),state.z(),dx/length*speed,dy/length*speed,dz/length*speed,yaw,pitch);
            var next=FlightPrediction.step(input,yaw,pitch,180,90,90,gravity,false);
            if(swept.clear(input,next,.15,.12))return new Motion(input,next,yaw,pitch,speed/BASE_SPEED);
        }
        return null;
    }
}
