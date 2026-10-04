package dev.winefox.skyfarer.item;

import net.minecraft.world.item.SpyglassItem;

/** Inherits native scope ability, sounds, animation, duration and use statistics. */
public final class CommandSpyglassItem extends SpyglassItem {
    private final boolean creative;
    public CommandSpyglassItem(Properties properties,boolean creative) {super(properties);this.creative=creative;}
    public boolean unlimited() {return creative;}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.winefox_skyfarer.command_spyglass"));
        if(creative)lines.add(net.minecraft.network.chat.Component.translatable("tooltip.winefox_skyfarer.creative_command_spyglass"));
    }
}
