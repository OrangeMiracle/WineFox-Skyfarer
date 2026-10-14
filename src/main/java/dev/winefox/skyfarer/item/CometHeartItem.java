package dev.winefox.skyfarer.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

/** Equipment is bound through TLM's native BaubleManager, never an armor/Curios slot. */
public final class CometHeartItem extends Item {
    private final boolean awakened;
    public CometHeartItem(boolean awakened) {
        super(new Properties().stacksTo(1).fireResistant().rarity(awakened?Rarity.EPIC:Rarity.RARE));
        this.awakened=awakened;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.winefox_skyfarer.maid_bauble"));
        lines.add(Component.translatable(awakened?"tooltip.winefox_skyfarer.everburning_comet_heart":"tooltip.winefox_skyfarer.comet_heart"));
    }
}
