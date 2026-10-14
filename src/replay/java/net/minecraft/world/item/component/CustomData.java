package net.minecraft.world.item.component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
public record CustomData(CompoundTag tag) {
 public static final CustomData EMPTY=new CustomData(new CompoundTag());
 public CompoundTag copyTag(){return tag.copy();}
 public static void update(Object key,ItemStack stack,java.util.function.Consumer<CompoundTag> edit){var tag=stack.custom.copyTag();edit.accept(tag);stack.custom=new CustomData(tag);}
}
