package net.neoforged.neoforge.items;
import net.minecraft.world.item.ItemStack;
public interface IItemHandlerModifiable {int getSlots();ItemStack getStackInSlot(int i);ItemStack extractItem(int i,int n,boolean sim);void setStackInSlot(int i,ItemStack s);boolean isItemValid(int i,ItemStack s);int getSlotLimit(int i);}
