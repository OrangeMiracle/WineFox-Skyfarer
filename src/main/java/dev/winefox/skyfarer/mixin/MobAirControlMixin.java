package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class MobAirControlMixin {
    @Unique private boolean wfs$controlsAir() { return (Object)this instanceof EntityMaid maid && SkyfarerRuntime.isAirControlActive(maid); }
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"))
    private void wfs$navigation(PathNavigation navigation, Operation<Void> original) { if (!wfs$controlsAir()) original.call(navigation); }
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
    private void wfs$move(MoveControl control, Operation<Void> original) { if (!wfs$controlsAir()) original.call(control); }
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V"))
    private void wfs$look(LookControl control, Operation<Void> original) { if (!wfs$controlsAir()) original.call(control); }
}
