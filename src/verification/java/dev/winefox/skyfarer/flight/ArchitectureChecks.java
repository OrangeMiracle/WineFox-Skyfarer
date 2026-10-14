package dev.winefox.skyfarer.flight;

import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;

/** Guards the actual compiled boundary, including synthetic lambda methods. */
public final class ArchitectureChecks {
    public static void main(String[] args)throws Exception {
        for(String caller:List.of("ai/AirCombatController","command/TransitFlight")){
            visit(caller,new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                            require(!method.equals("isAutoSpinAttack"),caller+" leaked provider spin lifecycle");
                            for(String forbidden:List.of("FireworkManager","FireworkRocketEntity","FireworkLifetimeAccessor","RiptidePropulsion","RiptideReadiness","RocketProfile","CometBaubles","PropulsionResolver","VirtualPropulsion"))
                                require(!owner.endsWith("/"+forbidden),caller+" leaked provider call "+owner+"."+method);
                        }
                    };
                }
            });
        }
        // A new legacy entry must not silently reopen direct resource consumption.
        try(var files=Files.walk(Path.of("build/classes/java/main/dev/winefox/skyfarer"))){
            for(var path:files.filter(p->p.toString().endsWith(".class")).toList()){
                new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int a,String name,String descriptor,String signature,String[] exceptions){
                        return new MethodVisitor(Opcodes.ASM9){
                            @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                                if(owner.endsWith("/FireworkManager")&&method.equals("boost"))
                                    require(path.getFileName().toString().startsWith("PropulsionCapability$"),"direct ignition outside resolver provider: "+path+"."+name);
                                if(path.getFileName().toString().equals("CometThrustEffects.class"))
                                    require(!owner.startsWith("net/minecraft/client/"),"client renderer linked from server effects");
                            }
                        };
                    }
                },0);
            }
        }
        // Production task records cannot contain live game objects or caller closures.
        try(var files=Files.list(Path.of("build/classes/java/main/dev/winefox/skyfarer/async"))){
            for(var path:files.filter(p->p.getFileName().toString().startsWith("PlanningTask$")).toList()){
                new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public FieldVisitor visitField(int a,String name,String descriptor,String signature,Object value){
                        for(String forbidden:List.of("net/minecraft/", "touhoulittlemaid/", "java/util/function/"))
                            require(!descriptor.contains(forbidden),path+" contains live field "+name+":"+descriptor);
                        return null;
                    }
                },ClassReader.SKIP_CODE);
            }
        }
        var settings=dev.winefox.skyfarer.config.SkyfarerConfig.PARAMETERS;
        for(String removed:List.of("previous_target_bonus","climb_height","forward_collision_check_distance"))
            require(!settings.containsKey(removed),"dead config still exposed: "+removed);
        for(String retained:List.of("max_turn_angle","firework_cooldown_ticks","spear_vertical_expansion","prediction_length_multiplier"))
            require(settings.containsKey(retained),"live config removed: "+retained);
        require(Arrays.stream(dev.winefox.skyfarer.ai.AirCombatState.values()).noneMatch(s->Set.of("BREAKAWAY","ABORT","EMERGENCY").contains(s.name())),"unreachable state retained");
        System.out.println("0.7.3 architecture: provider-free upper control, sole resolver ignition port, server/client effects isolation, closed worker DTOs and live config retention passed.");
    }
    private static void visit(String name,ClassVisitor visitor)throws Exception {
        try(var in=ArchitectureChecks.class.getClassLoader().getResourceAsStream("dev/winefox/skyfarer/"+name+".class")){
            require(in!=null,"missing production class "+name);new ClassReader(in).accept(visitor,0);
        }
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
