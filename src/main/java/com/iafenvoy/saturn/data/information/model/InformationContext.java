package com.iafenvoy.saturn.data.information.model;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;

import java.util.Objects;
import java.util.Optional;

/**
 * Formatting and an optional explicit definition/serialization context. The host owns permissions and filtering.
 */
public record InformationContext(String language, Optional<HolderLookup.Provider> registries) {
    public static final InformationContext DEFAULT = new InformationContext("en_us");

    public InformationContext {
        Objects.requireNonNull(language);
        Objects.requireNonNull(registries);
    }

    public InformationContext(String language) {
        this(language, Optional.empty());
    }

    public InformationContext(String language, HolderLookup.Provider registries) {
        this(language, Optional.of(registries));
    }

    public DynamicOps<JsonElement> ops(PlayerView view) {
        return this.registries.<DynamicOps<JsonElement>>map(provider -> provider.createSerializationContext(JsonOps.INSTANCE))
                .orElseGet(view::serializationOps);
    }
}
