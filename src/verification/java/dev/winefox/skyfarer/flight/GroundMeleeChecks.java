package dev.winefox.skyfarer.flight;

import dev.winefox.skyfarer.compat.spears.GroundMeleeReach;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;

/** Real MC geometry and dependency linkage without bootstrapping a Minecraft instance. */
public final class GroundMeleeChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var eye=new Vec3(0,1.5,0);var direction=new Vec3(0,0,1);
        var far=new AABB(-.3,0,5.7,.3,2,6.3);
        double base=GroundMeleeReach.stoppingMaximum(GroundMeleeReach.maximum(4.5,2,0));
        double favored=GroundMeleeReach.stoppingMaximum(GroundMeleeReach.maximum(4.5,2,2));
        require(!GroundMeleeReach.contact(eye,direction,far,2,base),"native base reach overextended");
        require(GroundMeleeReach.contact(eye,direction,far,2,favored),"favorability missing from actual geometry");
        require(!GroundMeleeReach.contact(eye,direction,new AABB(-.3,0,.4,.3,2,1),2,favored),"favorability erased blind zone");
        require(GroundMeleeReach.contact(eye,direction,new AABB(-.3,0,1.9,.3,2,2.5),2,base),"segment starts inside body but contact rejected");
        String collector="(Lnet/minecraft/world/entity/LivingEntity;FFFLjava/util/function/Predicate;)Ljava/util/Collection;";
        checkCall("com/notunanancyowen/spears/components/PiercingWeapon","stab","com/notunanancyowen/spears/Spears","collectPiercingCollisions",collector);
        checkCall("dev/winefox/skyfarer/mixin/MaidSpearMeleeMixin","wfs$maidMeleeRange","dev/winefox/skyfarer/compat/spears/SpearCompat","groundMaximum","(Lnet/minecraft/world/entity/LivingEntity;F)F");
        checkCall("dev/winefox/skyfarer/compat/spears/SpearCompat","groundMaximum",null,"getAttackDistancePlusByPoint",null);
        checkCall("dev/winefox/skyfarer/compat/spears/SpearCompat","groundContact","com/notunanancyowen/spears/Spears","collectPiercingCollisions",collector);
        checkCall("dev/winefox/skyfarer/compat/tlm/GroundSpearCombat","tick",null,"doHurtTarget",null);
        checkCall("com/github/tartaricacid/touhoulittlemaid/entity/ai/control/MaidMoveControl","tick",null,"getJumpControl",null);
        checkCall("com/github/tartaricacid/touhoulittlemaid/entity/ai/control/MaidMoveControl","tick",null,"jump",null);
        checkCall("com/github/tartaricacid/touhoulittlemaid/entity/ai/brain/MaidBrain","registerCoreGoals","net/minecraft/world/entity/ai/behavior/MoveToTargetSink","<init>",null);
        checkCall("dev/winefox/skyfarer/compat/tlm/SkyfarerEquipment","flightReady",null,"wingQualification",null);
        checkCall("dev/winefox/skyfarer/compat/tlm/SkyfarerEquipment","ensureWings",null,"wingQualification",null);
        System.out.println("Ground melee passed: real AABB geometry, bonus/blind-zone, native stab collector descriptor and actual bonus linkage.");
    }
    static void checkCall(String path,String method,String owner,String call,String descriptor) throws Exception {
        boolean[] found={false};try(var stream=GroundMeleeChecks.class.getClassLoader().getResourceAsStream(path+".class")) {
            require(stream!=null,"missing dependency "+path);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String name,String d,String s,String[] ex){
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int op,String o,String n,String desc,boolean itf){
                        if(name.equals(method)&&n.equals(call)&&(owner==null||owner.equals(o))&&(descriptor==null||descriptor.equals(desc)))found[0]=true;
                    }};
                }
            },ClassReader.SKIP_DEBUG);
        }require(found[0],path+":"+method+" missing native call "+call);
    }
}
