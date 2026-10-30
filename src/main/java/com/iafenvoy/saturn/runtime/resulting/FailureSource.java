package com.iafenvoy.saturn.runtime.resulting;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record FailureSource(ResourceLocation id) {
    public static FailureSource of(ResourceKey<? extends Registry<?>> registry) {
        return new FailureSource(registry.location());
    }
}
