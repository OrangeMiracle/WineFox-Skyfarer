package dev.winefox.skyfarer.ai;

import net.minecraft.world.entity.LivingEntity;
public record TargetSelection(LivingEntity target, double score) {}
