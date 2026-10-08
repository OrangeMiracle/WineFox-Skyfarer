package net.neoforged.neoforge.event;
public final class EventHooks {
 public static boolean onUseItemStop(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid m,net.minecraft.world.item.ItemStack stack,int remaining){m.releaseCalls++;return m.vetoRelease;}
}
