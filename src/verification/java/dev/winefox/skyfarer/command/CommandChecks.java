package dev.winefox.skyfarer.command;

import dev.winefox.skyfarer.flight.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class CommandChecks {
    public static void main(String[] args) throws Exception {
        var a=new MarkQueue();var b=new MarkQueue();var ids=new ArrayList<UUID>();
        for(int i=0;i<6;i++){ids.add(new UUID(0,i));a.toggle(ids.get(i),i==5);}
        require(a.ids().equals(ids.subList(1,6)),"sixth mark must evict oldest");
        require(!a.toggle(ids.get(3),false)&&a.ids().equals(List.of(ids.get(1),ids.get(2),ids.get(4),ids.get(5))),"toggle compacts numbering");
        a.toggle(ids.get(3),false);require(a.ids().getLast().equals(ids.get(3)),"re-mark appends");
        require(a.creative(ids.get(5))&&!a.creative(ids.get(3))&&b.isEmpty(),"per-player and per-mark isolation");
        require(DispatchPolicy.admitted(true,false,128,128)&&!DispatchPolicy.admitted(true,false,128.01,128),"survival home boundary");
        require(DispatchPolicy.admitted(true,true,1e6,128)&&DispatchPolicy.admitted(false,false,1e6,128),"creative home and follow admission");
        require(DispatchPolicy.transit(true,48.01)&&!DispatchPolicy.transit(true,48)&&DispatchPolicy.transit(false,20),"exact handoff and unavailable entity lock");
        for(int mask=0;mask<16;mask++)require(DispatchPolicy.retainCreative((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0)==(mask==15),
            "Home retention must end on death, unmark or unload, and must not affect survival commands");
        require(DispatchPolicy.score(150,60,0,false,0)>DispatchPolicy.score(150,50,180,false,0),"heading beats FIFO/distance alone");
        require(DispatchPolicy.score(150,40,20,true,0)>DispatchPolicy.score(150,40,20,false,0),"previous target preference");
        Set<TicketWindow.Cell> held=Set.of();
        for(int i=-1000;i<=1000;i++) {
            double x=i*16+.1,z=i*7-.1;var wanted=TicketWindow.around(x,z,100,40);
            require(wanted.size()<=36,"ticket count grows with journey");
            require(wanted.contains(new TicketWindow.Cell((int)Math.floor(x/16),(int)Math.floor(z/16))),"negative coordinate/current chunk");
            var next=new HashSet<>(held);next.addAll(wanted);next.removeIf(c->!wanted.contains(c));held=next;
            require(held.equals(wanted),"stale ticket retained");
        }
        var start=new FlightPrediction.State(0,12,0,0,0,1.1,0,0);
        var request=new GlidePlanner.Request(25,12,0,0,14,Double.NaN,0,5,3,8,.08,.3,.4,false,null);
        GlidePlanner.Corridor clear=(from,to,side,vertical)->true;
        var plan=GlidePlanner.choose(start,request,clear);var path=GlidePlanner.trace(start,request,plan,clear);
        require(path.size()>10&&path.getFirst().state().equals(start),"debug prediction is a path, not one waypoint");
        var expected=FlightPrediction.step(start,plan.yaw(),plan.pitch(),plan.yawRate(),plan.pitchRate(),plan.pitchRate(),.08,false);
        require(path.get(1).state().equals(expected),"debug path diverges from applied first physics step");
        require(path.stream().allMatch(p->p.side()>=.04&&p.vertical()>=.04),"collision margins absent");
        try {
            PredictionHorizon.multiplier=()->10;
            var longer=GlidePlanner.trace(start,request,plan,clear);
            require(longer.size()==241&&path.size()==25,"1x/10x prediction uses actual 24/240 tick rollouts");
            require(longer.get(1).state().equals(path.get(1).state()),"extending forecast changes its initial applied physics");
            final int[] collisionChecks={0};
            GlidePlanner.trace(start,request,plan,(from,to,side,vertical)->{collisionChecks[0]++;return true;});
            require(collisionChecks[0]==240,"longer channel is a real collision forecast, not stretched drawing");
            require(!GlidePlanner.choose(start,request,(from,to,side,vertical)->false).safe(),"10x must preserve immediate hard collision rejection");
            var jump=new FlightPrediction.State(0,.82,0,0,.33,0,0,0);
            require(SurfaceLaunchPlanner.choose(jump,0,7,.08,0,(from,to,side,vertical)->Math.min(from.y(),to.y())>.4+vertical,SurfaceLaunchPlanner.DRY)!=null,
                "10x speculative tail must not prevent a safe short-pulse launch just because future propulsion is undecided");
            var expanded=TicketWindow.around(0,0,1,0,480);
            require(expanded.size()<=279&&expanded.contains(new TicketWindow.Cell(30,0)),"long prediction needs bounded route lookahead");
            PredictionHorizon.multiplier=()->100;require(PredictionHorizon.ticks(24)==240,"upper bound");
            PredictionHorizon.multiplier=()->Double.NaN;require(PredictionHorizon.ticks(24)==24,"invalid multiplier fallback");
        } finally {PredictionHorizon.multiplier=()->1;}
        try(var jar=new JarFile("build/moddev/artifacts/neoforge-21.1.250-merged.jar")) {
            var renderer=read(jar,"net/minecraft/client/renderer/entity/ItemRenderer");
            for(String method:List.of("render","getModel"))require(renderer.methods.stream().filter(m->m.name.equals(method)).anyMatch(m->calls(m,"net/minecraft/world/item/ItemStack","is")),"spyglass render hook missing: "+method);
            var player=read(jar,"net/minecraft/world/entity/player/Player");
            require(player.methods.stream().filter(m->m.name.equals("isScoping")).anyMatch(m->calls(m,"net/minecraft/world/item/ItemStack","canPerformAction")),"custom native scope ability unsupported");
        }
        try(var jar=new JarFile("libs/touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar")) {
            var schedule=read(jar,"com/github/tartaricacid/touhoulittlemaid/entity/passive/SchedulePos");
            require(schedule.methods.stream().filter(m->m.name.equals("tick")).anyMatch(m->calls(m,"com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid","canBrainMoving")),"native home recall must respect air control");
            require(schedule.methods.stream().filter(m->m.name.equals("tick")&&m.desc.equals("(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V")).count()==1,"exact Home recall mixin signature");
            var helper=read(jar,"com/github/tartaricacid/touhoulittlemaid/util/TeleportHelper");
            require(helper.methods.stream().anyMatch(m->m.name.equals("teleportToRestrictCenter")&&m.desc.equals("(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z")),"safe Home return API exists in actual dependency");
        }
        System.out.println("Command queue, ownership/range policy, handoff, route priority, bounded tickets, debug forecast and actual spyglass/Home bytecode checks passed.");
    }
    private static ClassNode read(JarFile jar,String name)throws Exception {var n=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(n,ClassReader.SKIP_DEBUG);return n;}
    private static boolean calls(MethodNode m,String owner,String name){for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))return true;return false;}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
