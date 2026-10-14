package dev.winefox.skyfarer.flight;

/** Immutable flight requirement shared by propulsion providers. No entity/item/world on workers. */
public record PropulsionDemand(Phase phase,double x,double height,double z,double minimumSpeed,
        SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,double rate,boolean exiting,int contactDeadline,CompactTurnPlanner.Goal turn) {
    public enum Source { COAST, RIPTIDE, FIREWORK, VIRTUAL_PROPULSION }
    public enum Phase { TAKEOFF, APPROACH, REPOSITION, REJOIN, STRIKE, EXIT, AIR_RECOVERY, EMERGENCY, TRANSIT }
    public PropulsionDemand(Phase phase,double x,double height,double z,double minimumSpeed,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,double rate,boolean exiting,int deadline){this(phase,x,height,z,minimumSpeed,path,aim,rate,exiting,deadline,null);}
    public PropulsionDemand(Phase phase,double x,double height,double z,double minimumSpeed,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,double rate,boolean exiting){this(phase,x,height,z,minimumSpeed,path,aim,rate,exiting,Integer.MAX_VALUE);}
    public PropulsionDemand(Phase phase,double x,double height,double z,double minimumSpeed){this(phase,x,height,z,minimumSpeed,null,null,8,false);}
    public boolean departure(){return phase==Phase.TAKEOFF;}
    public boolean emergency(){return phase==Phase.EMERGENCY;}
    public boolean continuation(){return path!=null;}
    public PropulsionDemand after(int ticks){return new PropulsionDemand(phase,x,height,z,minimumSpeed,path,aim==null?null:aim.after(ticks),rate,exiting,Math.max(0,contactDeadline-ticks),turn);}
    public PropulsionDemand withDeadline(int ticks){return new PropulsionDemand(phase,x,height,z,minimumSpeed,path,aim,rate,exiting,ticks,turn);}
    public PropulsionDemand withAim(FlightPrediction.Aim current){return new PropulsionDemand(phase,x,height,z,minimumSpeed,path,current,rate,exiting,contactDeadline,turn);}
    public PropulsionDemand withTurn(CompactTurnPlanner.Goal goal,double turnRate){return new PropulsionDemand(phase,x,height,z,minimumSpeed,path,aim,turnRate,exiting,contactDeadline,goal);}
    public PropulsionDemand advanceTurn(FlightPrediction.State state){return turn!=null&&turn.readyInward(state.x(),state.z())?withTurn(turn.inward(),rate):this;}
}
