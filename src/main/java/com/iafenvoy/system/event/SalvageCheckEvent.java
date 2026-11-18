package com.iafenvoy.system.event;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-thread veto at quote and confirmation. Listeners must not mutate inventory or perform effects.
 */
public final class SalvageCheckEvent extends Event {
    private final ServerPlayer player;
    private final Identifier rule;
    private final ItemStack input;
    private Failure failure;

    public SalvageCheckEvent(ServerPlayer player, Identifier rule, ItemStack input) {
        this.player = player;
        this.rule = rule;
        this.input = input.copy();
    }

    public ServerPlayer player() {
        return this.player;
    }

    public Identifier rule() {
        return this.rule;
    }

    public ItemStack input() {
        return this.input.copy();
    }

    public Optional<Failure> failure() {
        return Optional.ofNullable(this.failure);
    }

    public void reject(Failure failure) {
        if (this.failure == null) this.failure = Objects.requireNonNull(failure);
    }
}
