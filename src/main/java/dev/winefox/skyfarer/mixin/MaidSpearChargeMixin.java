package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.notunanancyowen.spears.components.KineticWeapon;
import com.notunanancyowen.spears.dataholders.SpearUser;
import dev.winefox.skyfarer.compat.spears.MaidSpearCollisions;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import dev.winefox.skyfarer.flight.FlightMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Collection;
import java.util.function.Predicate;

@Mixin(value = KineticWeapon.class, remap = false)
public abstract class MaidSpearChargeMixin {
    @WrapMethod(method = "usageTick")
    private void wfs$withdrawalCharge(ItemStack stack, int remaining, LivingEntity user, EquipmentSlot slot, Operation<Void> original) {
        if (user instanceof EntityMaid maid && !maid.level().isClientSide) {
            var controller = SkyfarerRuntime.get(maid);
            if (controller != null && controller.postChargeActive()) {
                int elapsed = stack.getUseDuration(user) - remaining;
                // Keep the same use session/cooldowns. Only this maid's timed withdrawal extends validity.
                int capped = FlightMath.withdrawalUseTicks(elapsed, SpearCompat.delayTicks(stack), SpearCompat.chargeWindow(stack));
                remaining = stack.getUseDuration(user) - capped;
            }
        }
        original.call(stack, remaining, user, slot);
    }
    @WrapOperation(method = "usageTick", at = @At(value = "INVOKE", target = "Lcom/notunanancyowen/spears/Spears;collectPiercingCollisions(Lnet/minecraft/world/entity/LivingEntity;FFFLjava/util/function/Predicate;)Ljava/util/Collection;"))
    private Collection<EntityHitResult> wfs$verticalTolerance(LivingEntity user, float min, float max, float margin,
        Predicate<Entity> predicate, Operation<Collection<EntityHitResult>> original) {
        var nativeHits = original.call(user, min, max, margin, predicate);
        if (user instanceof EntityMaid maid && !maid.level().isClientSide) {
            var controller = SkyfarerRuntime.get(maid);
            if (controller != null && controller.spearAttackActive()) return MaidSpearCollisions.extend(maid, min, max, margin, predicate, nativeHits);
        }
        return nativeHits;
    }
    @WrapOperation(method = "usageTick", at = @At(value = "INVOKE", target = "Lcom/notunanancyowen/spears/dataholders/SpearUser;pierce(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"))
    private boolean wfs$recordActualDamage(SpearUser user, EquipmentSlot slot, Entity target, float damage,
        boolean dealDamage, boolean knockback, boolean dismount, Operation<Boolean> original) {
        var controller = user instanceof EntityMaid maid && !maid.level().isClientSide ? SkyfarerRuntime.get(maid) : null;
        boolean active = controller != null && controller.spearAttackActive();
        if (active && controller.alreadyPierced(target.getUUID())) return false;
        float before = target instanceof LivingEntity living ? living.getHealth() + living.getAbsorptionAmount() : 0;
        boolean result = original.call(user, slot, target, damage, dealDamage, knockback, dismount);
        if (active && dealDamage && result && target instanceof LivingEntity living
            && (living.getHealth() + living.getAbsorptionAmount() < before || !living.isAlive())) controller.recordSpearHit(target.getUUID());
        return result;
    }
}
