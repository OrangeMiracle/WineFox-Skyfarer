package net.neoforged.neoforge.items;
import net.minecraft.world.item.ItemStack;
public class ItemStackHandler implements IItemHandlerModifiable {
 final ItemStack[] stacks;public ItemStackHandler(int n){stacks=new ItemStack[n];java.util.Arrays.fill(stacks,ItemStack.EMPTY);}
 public int getSlots(){return stacks.length;}public ItemStack getStackInSlot(int i){return stacks[i];}public void setStackInSlot(int i,ItemStack s){stacks[i]=s;}
 public ItemStack extractItem(int i,int n,boolean sim){var s=stacks[i];if(!sim)stacks[i]=ItemStack.EMPTY;return s;}
 public boolean isItemValid(int i,ItemStack s){return true;}public int getSlotLimit(int i){return 64;}
}
