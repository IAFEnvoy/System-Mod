package com.iafenvoy.saturn.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

public final class HolderHelper {
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath("", "");

    public static ResourceLocation id(Holder<?> holder) {
        return idOptional(holder).orElse(EMPTY);
    }

    public static ResourceLocation idOrNull(Holder<?> holder) {
        return idOptional(holder).orElse(null);
    }

    public static Optional<ResourceLocation> idOptional(Holder<?> holder) {
        return holder.unwrapKey().map(ResourceKey::location);
    }
}
