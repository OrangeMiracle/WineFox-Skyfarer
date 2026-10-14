package dev.winefox.skyfarer.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.*;
import dev.winefox.skyfarer.WineFoxSkyfarer;

/** Additive GLM: table and probability are standard data-pack conditions. */
public final class CometLootModifier extends LootModifier {
    public static final MapCodec<CometLootModifier> CODEC=RecordCodecBuilder.mapCodec(instance->codecStart(instance).apply(instance,CometLootModifier::new));
    public CometLootModifier(LootItemCondition[] conditions){super(conditions);}
    @Override public MapCodec<? extends IGlobalLootModifier> codec(){return CODEC;}
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context){loot.add(new ItemStack(WineFoxSkyfarer.COMET_HEART.get()));return loot;}
}
