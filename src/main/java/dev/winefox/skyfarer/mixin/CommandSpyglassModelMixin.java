package dev.winefox.skyfarer.mixin;
import dev.winefox.skyfarer.item.CommandSpyglassItem;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Preserve vanilla GUI vs three-dimensional held spyglass model selection. */
@Mixin(ItemRenderer.class)
public abstract class CommandSpyglassModelMixin {
 @Redirect(method={"render","getModel"},at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
 private boolean wfs$scopeModel(ItemStack stack,Item item){return item==Items.SPYGLASS&&stack.getItem() instanceof CommandSpyglassItem || stack.is(item);}
}
