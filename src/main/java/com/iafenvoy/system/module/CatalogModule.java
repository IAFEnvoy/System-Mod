package com.iafenvoy.system.module;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * An enabled module grants its configured entries; omitted options allow the whole corresponding registry.
 */
public interface CatalogModule<T> extends SharedModule {
    ResourceKey<Registry<T>> catalog();

    Optional<List<Either<ResourceKey<T>, TagKey<T>>>> options();

    default boolean allows(RegistryAccess registries, ResourceLocation id) {
        return this.options().map(options -> registries.registry(this.catalog())
                .map(registry -> RegistryCodecs.matchesKeys(options, registry, id)).orElse(false)).orElse(true);
    }
}
