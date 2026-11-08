package com.iafenvoy.saturn.data.information.model;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Optional;

public record SavedPlayerViewContext(PlayerViewMeta meta, HolderLookup.Provider registries, CompoundTag player,
                                     Optional<String> statistics, Optional<String> advancements) {
    public SavedPlayerViewContext {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(registries);
        player = player.copy();
        Objects.requireNonNull(statistics);
        Objects.requireNonNull(advancements);
    }

    @Override
    public CompoundTag player() {
        return this.player.copy();
    }
}
