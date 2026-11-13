package com.iafenvoy.system.assistant.workshop;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/** Costs see the live inventory except the stack being repaired; it can never pay for its own repair. */
record RepairInventory(Container inventory, int excluded) implements Container {
    @Override
    public int getContainerSize() {
        return this.inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return !this.hasAnyMatching(stack -> !stack.isEmpty());
    }

    @Override
    public @NonNull ItemStack getItem(int slot) {
        return slot == this.excluded ? ItemStack.EMPTY : this.inventory.getItem(slot);
    }

    @Override
    public @NonNull ItemStack removeItem(int slot, int count) {
        return slot == this.excluded ? ItemStack.EMPTY : this.inventory.removeItem(slot, count);
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int slot) {
        return slot == this.excluded ? ItemStack.EMPTY : this.inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        if (slot != this.excluded) this.inventory.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        this.inventory.setChanged();
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.inventory.stillValid(player);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < this.getContainerSize(); slot++) this.setItem(slot, ItemStack.EMPTY);
    }
}
