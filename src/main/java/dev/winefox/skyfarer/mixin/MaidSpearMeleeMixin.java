package dev.winefox.skyfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.notunanancyowen.spears.components.PiercingWeapon;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Collection;
import java.util.function.Predicate;

/** Extend the actual ordinary stab, preserving native minimum, occlusion, damage and filtering. */
@Mixin(value=PiercingWeapon.class,remap=false)
public abstract class MaidSpearMeleeMixin {
    @WrapOperation(method="stab",at=@At(value="INVOKE",target="Lcom/notunanancyowen/spears/Spears;collectPiercingCollisions(Lnet/minecraft/world/entity/LivingEntity;FFFLjava/util/function/Predicate;)Ljava/util/Collection;"))
    private Collection<EntityHitResult> wfs$maidMeleeRange(LivingEntity user,float min,float max,float margin,
        Predicate<Entity> predicate,Operation<Collection<EntityHitResult>> original) {
        return original.call(user,min,SpearCompat.groundMaximum(user,max),margin,predicate);
    }
}
