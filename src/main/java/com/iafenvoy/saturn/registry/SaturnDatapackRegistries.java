package com.iafenvoy.saturn.registry;

import com.iafenvoy.saturn.data.trigger.TriggerRule;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Native Minecraft datapack registries. Reloading and client synchronization belong to the vanilla registry system, so
 * this class never stores a registry snapshot.
 */
@EventBusSubscriber
public final class SaturnDatapackRegistries {
    private static final List<ResourceKey<? extends Registry<?>>> KEYS = new LinkedList<>();

    @SubscribeEvent
    public static void newDatapackRegistries(NewRegistry event) {
        register(event, SaturnResourceKeys.TRIGGER, TriggerRule.DIRECT_CODEC);
    }

    private static <T> void register(NewRegistry event, ResourceKey<Registry<T>> key, Codec<T> codec) {
        event.dataPackRegistry(key, codec, codec);
        KEYS.add(key);
    }

    public static List<ResourceKey<? extends Registry<?>>> registries() {
        return KEYS;
    }

    public static <T> Stream<Reference<T>> holders(Provider access, ResourceKey<? extends Registry<T>> key) {
        return access.lookupOrThrow(key).listElements();
    }

    // Widened to Holder on purpose: every caller wants the entry, not the reference, and Optional is invariant so the
    // widening has to happen here rather than at each call site.
    public static <T> Optional<Holder<T>> holder(Provider access, ResourceKey<? extends Registry<T>> key, ResourceLocation id) {
        // 1.21.1 resolves the individual entry through the registry lookup, not the provider itself.
        return access.lookupOrThrow(key).get(ResourceKey.create(key, id)).map(holder -> holder);
    }

    public static <T> Optional<T> get(Provider access, ResourceKey<? extends Registry<T>> key, ResourceLocation id) {
        return holder(access, key, id).map(Holder::value);
    }
}
