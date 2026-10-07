package dev.winefox.skyfarer.flight;

import net.minecraft.util.Mth;

public final class EquipmentPropulsionChecks {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var random=new java.util.Random(6212);int vectors=0;
        for(float strength:new float[]{1.5f,2.25f,3f})for(int i=0;i<500;i++) {
            float yaw=random.nextFloat()*720-360,pitch=random.nextFloat()*180-90;
            float x=-Mth.sin(yaw*(float)(Math.PI/180))*Mth.cos(pitch*(float)(Math.PI/180));
            float y=-Mth.sin(pitch*(float)(Math.PI/180)),z=Mth.cos(yaw*(float)(Math.PI/180))*Mth.cos(pitch*(float)(Math.PI/180));
            float length=Mth.sqrt(x*x+y*y+z*z);x*=strength/length;y*=strength/length;z*=strength/length;
            var s=new FlightPrediction.State(0,100,0,.3,-.7,1.1,yaw,pitch);var pushed=RiptidePlanner.push(s,strength);
            require(pushed.vx()==s.vx()+x&&pushed.vy()==s.vy()+y&&pushed.vz()==s.vz()+z,"native additive impulse mismatch");vectors++;
        }
        var start=new FlightPrediction.State(0,80,0,0,-.1,.7,0,-15);
        var clear=RiptidePlanner.choose(start,0,85,120,3,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY);
        require(clear!=null&&clear.trace().size()==43,"full windup/spin/recovery missing");
        double previous=0;
        for(float strength:new float[]{1.5f,2.25f,3f}) {
            var plan=RiptidePlanner.choose(start,0,85,120,strength,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY);
            require(plan!=null&&plan.strength()==strength,"riptide level missing from prediction");
            var release=RiptidePlanner.push(start,strength);
            double speed=Math.sqrt(release.vx()*release.vx()+release.vy()*release.vy()+release.vz()*release.vz());
            require(speed>previous,"stronger riptide did not change velocity");previous=speed;
        }
        require(RiptidePlanner.choose(start,0,85,12,3,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY)==null,"overshoot allowed");
        require(RiptidePlanner.choose(start,0,85,120,3,.08,(a,b,s,v)->b.z()<15,SurfaceLaunchPlanner.DRY)==null,"wall allowed");
        // Native sky test must consult isRainingAt (sky + heightmap + biome), not global rain alone.
        checkCalls("net/minecraft/world/entity/Entity.class","isInWaterOrRain","isInRain");
        checkCalls("net/minecraft/world/entity/Entity.class","isInRain","isRainingAt");
        checkCalls("net/minecraft/world/level/Level.class","isRainingAt","canSeeSky");
        checkCalls("net/minecraft/world/level/Level.class","isRainingAt","getHeightmapPos");
        checkCalls("dev/winefox/skyfarer/compat/tlm/RiptidePropulsion.class","strengths","isInWaterOrRain");
        checkCalls("dev/winefox/skyfarer/compat/tlm/GroundSpearCombat.class","tick","doHurtTarget");
        checkCalls("dev/winefox/skyfarer/ai/AirCombatController.class","manageEnergy","tryRiptide");
        checkCalls("dev/winefox/skyfarer/ai/AirCombatController.class","prepare","tryRiptide");
        checkCalls("dev/winefox/skyfarer/compat/tlm/RiptidePropulsion.class","strength","getTridentSpinAttackStrength");
        checkCalls("dev/winefox/skyfarer/compat/tlm/RiptidePropulsion.class","tick","startGliding");
        checkCalls("dev/winefox/skyfarer/compat/tlm/RiptidePropulsion.class","tick","releaseUsingItem");
        checkCalls("dev/winefox/skyfarer/compat/tlm/RiptidePropulsion.class","ownsUse","getUsedItemHand");
        checkCalls("dev/winefox/skyfarer/mixin/MaidRiptideReleaseMixin.class","wfs$release","releaseAuthorized");
        checkCalls("dev/winefox/skyfarer/mixin/RiptideUseAuditMixin.class","wfs$auditStop","riptideUseStopped");
        checkCalls("net/minecraft/world/entity/LivingEntity.class","releaseUsingItem","onUseItemStop");
        checkCalls("net/minecraft/world/entity/LivingEntity.class","releaseUsingItem","releaseUsing");
        checkCalls("net/minecraft/world/item/TridentItem.class","releaseUsing","getUseDuration");
        checkCalls("dev/winefox/skyfarer/compat/spears/SpearCompat.class","stopCharge","getUseItem");
        System.out.println(vectors+" native impulse vectors, full Riptide collision/overshoot and native environment/melee integration checks passed");
    }
    private static void checkCalls(String path,String method,String call) throws Exception {
        boolean[] found={false};try(var stream=EquipmentPropulsionChecks.class.getClassLoader().getResourceAsStream(path)) {
            new org.objectweb.asm.ClassReader(stream).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9){
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int a,String name,String d,String sig,String[] ex){
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9){@Override public void visitMethodInsn(int op,String owner,String n,String desc,boolean itf){if(name.equals(method)&&n.equals(call))found[0]=true;}};
                }
            },0);
        }require(found[0],path+":"+method+" missing "+call);
    }
}
