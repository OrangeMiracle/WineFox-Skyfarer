package dev.winefox.skyfarer.flight;

import java.util.List;
import java.util.UUID;

public final class StabilityLaunchChecks {
    public static void main(String[] args)throws Exception{
        try(var jar=new java.util.jar.JarFile("build/moddev/artifacts/neoforge-21.1.250-merged.jar")){
            var entity=new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(jar.getInputStream(jar.getJarEntry("net/minecraft/world/entity/Entity.class"))).accept(entity,org.objectweb.asm.ClassReader.SKIP_DEBUG);
            require(entity.methods.stream().anyMatch(m->m.name.equals("setSharedFlag")&&m.desc.equals("(IZ)V")),"flight flag audit hook does not match actual Minecraft bytecode");
        }
        var start=new FlightPrediction.State(0,30,0,0,0,3,90,0);
        var wall=new FlightCorridor.Box(-200,-20,12,200,200,13);
        GlidePlanner.Corridor space=(a,b,s,v)->!FlightCorridor.intersects(wall,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.3,.6,.4,s,v);
        var danger=EmergencyAvoidancePlanner.danger(start,90,0,5,0,.08,space);
        require(danger.imminent()&&danger.ttc()>1,"sideways momentum was not detected before the final tick");
        require(EmergencyAvoidancePlanner.margin(start,90,5)>EmergencyAvoidancePlanner.margin(new FlightPrediction.State(0,30,0,0,0,.4,0,0),0,5),"no speed/turn margin");
        var coast=EmergencyAvoidancePlanner.choose(start,0,List.of(),.08,space);
        var boost=EmergencyAvoidancePlanner.choose(start,0,List.of(0),.08,space);
        System.out.println("side wall ttc="+danger.ttc()+" margin="+danger.margin()+" glide="+coast+" boost="+(boost==null?null:boost.mode()));
        require(coast==null&&boost!=null&&boost.duration()==0,"expected boost-only escape");
        require(boost.trace().size()>RocketProfile.maximumTicks(0)+16,"partial rocket certificate");
        require(EmergencyAvoidancePlanner.choose(start,20,List.of(0),.08,(a,b,s,v)->false)==null,"active rocket/collision bypass");
        var ceiling=new FlightCorridor.Box(-200,42,-200,200,44,200);
        GlidePlanner.Corridor roof=(a,b,s,v)->!FlightCorridor.intersects(ceiling,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.3,.6,.4,s,v);
        require(EmergencyAvoidancePlanner.certify(new FlightPrediction.State(0,30,0,0,0,.7,0,-70),0,-70,0,0,.08,roof)==null,"rocket worsens ceiling collision but accepted");
        var jump=new FlightPrediction.State(0,.82,0,0,.3332,0,0,-50);
        GlidePlanner.Corridor ground=(a,b,s,v)->Math.min(a.y(),b.y())>.4+v;
        LaunchAttackPlanner.Plan launch=null;
        for(int height:new int[]{6,10,14})for(int distance:new int[]{10,14,18,22}){
            var aim=new FlightPrediction.Aim(0,height-3,distance,.3,7.8,0,0,0,0,2.25,.1,10);
            var p=LaunchAttackPlanner.choose(jump,List.of(new LaunchAttackPlanner.Target(new UUID(0,1),aim)),List.of(0,1),.08,5,.2,12,ground,(a,b,t)->true);
            if(p!=null){System.out.println("launch height="+height+" distance="+distance+" pitch="+p.pitch()+" duration="+p.duration()+" contact="+p.selection().prediction().contactTick());launch=p;break;}
        }
        require(launch!=null,"no uphill launch certificate");
        require(launch.selection().prediction().contactTick()>=10,"windup skipped");
        require(LaunchAttackPlanner.choose(jump,List.of(new LaunchAttackPlanner.Target(new UUID(0,1),new FlightPrediction.Aim(0,10,14,.3,1.8,0,0,0,0,2.25,.1,10))),List.of(0),.08,5,.2,12,(a,b,s,v)->false,(a,b,t)->true)==null,"blocked launch accepted");
        System.out.println("Stability/launch targeted physics checks passed");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
