package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class SkyfarerTask implements IAttackTask {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID, "skyfarer");
    @Override public ResourceLocation getUid() { return ID; }
    @Override public ItemStack getIcon() { return Items.ELYTRA.getDefaultInstance(); }
    @Override public SoundEvent getAmbientSound(EntityMaid maid) { return null; }
    @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // TLM appends schedule/meal behaviors; its API requires a mutable list.
        return new ArrayList<>(List.of(Pair.of(4, new SkyfarerBehavior())));
    }
    @Override public boolean enableLookAndRandomWalk(EntityMaid maid) { return !dev.winefox.skyfarer.runtime.SkyfarerRuntime.isAirControlActive(maid); }
    @Override public boolean enableEating(EntityMaid maid) { return !dev.winefox.skyfarer.runtime.SkyfarerRuntime.isAirControlActive(maid); }
    @Override public boolean isEnable(EntityMaid maid) { return SkyfarerEquipment.spear(maid); }
    @Override public boolean isWeapon(EntityMaid maid, ItemStack stack) { return SpearCompat.isChargeWeapon(stack); }
    @Override public List<Pair<String, Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        return List.of(Pair.of("spear", SkyfarerEquipment::spear));
    }
    @Override public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) { return getEnableConditionDesc(maid); }
}
