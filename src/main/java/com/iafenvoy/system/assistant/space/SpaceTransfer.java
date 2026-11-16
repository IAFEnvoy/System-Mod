package com.iafenvoy.system.assistant.space;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.EconomySystemModule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One server-thread preflight covers every item and extension effect before the first mutation.
 */
public final class SpaceTransfer {
    private final ServerPlayer player;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<Runnable> effects = new ArrayList<>();
    private final Map<Identifier, Long> currencies = new LinkedHashMap<>();
    private long experiencePoints, experienceLevels;
    private List<ItemStack> planned;

    public SpaceTransfer(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public void item(ItemStack stack) {
        this.planned = null;
        this.items.add(stack.copy());
    }

    /**
     * Extensions validate spawning/placement now, and stage only the actual effect here.
     */
    public void effect(Runnable effect) {
        if (this.effects.size() >= 64) throw new AgentException(AgentFailure.RESULT_TOO_LARGE);
        this.effects.add(effect);
    }

    /**
     * Wallet credits are committed with space consumption in the same durable player transaction.
     */
    public void currency(Identifier currency, long amount) {
        if (amount <= 0) throw new AgentException(AgentFailure.INVALID_REWARD);
        try {
            this.currencies.merge(currency, amount, Math::addExact);
        } catch (ArithmeticException error) {
            throw new AgentException(AgentFailure.BALANCE_OVERFLOW, error);
        }
    }

    public void experience(long amount, boolean levels) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) throw new AgentException(AgentFailure.INVALID_REWARD);
        long total = (levels ? this.experienceLevels : this.experiencePoints) + amount;
        if (total > Integer.MAX_VALUE) throw new AgentException(AgentFailure.INVALID_REWARD);
        if (levels) this.experienceLevels = total;
        else this.experiencePoints = total;
    }

    /**
     * Applied to a detached draft for preflight, then again inside the final storage transaction.
     */
    public void credit(PlayerData data) {
        this.currencies.forEach((currency, amount) -> ModuleData.get(data, EconomySystemModule.STORAGE).addBalance(currency, amount));
    }

    public boolean hasGameEffects() {
        return !this.items.isEmpty() || !this.effects.isEmpty() || this.experiencePoints > 0 || this.experienceLevels > 0;
    }

    public void validate() {
        this.planned = null;
        boolean experience = this.experiencePoints > 0 || this.experienceLevels > 0;
        if (this.experiencePoints > 0 && (this.experiencePoints > Integer.MAX_VALUE - (long) this.player.totalExperience
                || this.player.getXpNeededForNextLevel() <= 0)
                || experience && this.experienceLevels > Integer.MAX_VALUE / 9L - 32 - this.player.experienceLevel)
            throw new AgentException(AgentFailure.INVALID_REWARD);
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++)
            slots.add(this.player.getInventory().getItem(slot).copy());
        for (ItemStack item : this.items) {
            int remaining = item.getCount();
            for (ItemStack slot : slots) {
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, item)) continue;
                int count = Math.min(remaining, Math.max(0, slot.getMaxStackSize() - slot.getCount()));
                slot.grow(count);
                remaining -= count;
            }
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                if (!slots.get(slot).isEmpty()) continue;
                int count = Math.min(remaining, item.getMaxStackSize());
                slots.set(slot, item.copyWithCount(count));
                remaining -= count;
            }
            if (remaining > 0) throw new AgentException(AgentFailure.INVENTORY_FULL);
        }
        this.planned = slots;
    }

    public void apply() {
        if (this.planned == null) throw new IllegalStateException("Space transfer has not been validated");
        // Apply the exact validated plan; Inventory.add has special creative/damaged-item behavior.
        for (int slot = 0; slot < this.planned.size(); slot++)
            if (!ItemStack.matches(this.player.getInventory().getItem(slot), this.planned.get(slot)))
                this.player.getInventory().setItem(slot, this.planned.get(slot));
        this.player.getInventory().setChanged();
        if (this.experienceLevels > 0) this.player.giveExperienceLevels((int) this.experienceLevels);
        if (this.experiencePoints > 0) this.player.giveExperiencePoints((int) this.experiencePoints);
        this.effects.forEach(Runnable::run);
        this.player.containerMenu.broadcastChanges();
        if (!this.items.isEmpty()) this.player.playSound(SoundEvents.ITEM_PICKUP, 0.2F,
                ((this.player.getRandom().nextFloat() - this.player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }
}
