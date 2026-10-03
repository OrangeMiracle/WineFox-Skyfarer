package dev.winefox.skyfarer.flight;

/** A short low-speed launch can use strong attitude changes before it gains forward momentum. */
public final class LaunchPlanner {
    private LaunchPlanner() {}
    public record Launch(double yaw,double height,GlidePlanner.Plan plan,double cost) {}
    public static GlidePlanner.Request request(double yaw,double height,int boost,double gravity) {
        return new GlidePlanner.Request(yaw,height,0,0,14,Double.NaN,boost,90,45,60,gravity,.1,.05,true,null);
    }
    public static Launch choose(FlightPrediction.State s,double preferredYaw,double gravity,GlidePlanner.Corridor space) {
        Launch best=null;
        for(double turn:new double[]{0,-45,45,-90,90,-135,135,180}) for(double rise:new double[]{3,1,0}) {
            double yaw=preferredYaw+turn,height=s.y()+rise;
            var plan=GlidePlanner.choose(s,request(yaw,height,26,gravity),space);
            if(!plan.safe()) continue;
            double cost=plan.cost()*.05+Math.abs(turn)*.02+(GlidePlanner.ordinary(plan)?0:30);
            if(best==null || cost<best.cost) best=new Launch(yaw,height,plan,cost);
        }
        return best;
    }
}
