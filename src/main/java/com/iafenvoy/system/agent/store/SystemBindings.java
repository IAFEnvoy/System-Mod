package com.iafenvoy.system.agent.store;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * A collection leaves room for multiple systems; the current runtime permits only one binding.
 */
public final class SystemBindings {
    public static final int MAX_SYSTEMS = 1;
    public static final SystemBindings EMPTY = new SystemBindings(List.of());
    public static final Codec<SystemBindings> CODEC = ResourceLocation.CODEC.listOf(0, MAX_SYSTEMS).xmap(SystemBindings::new, SystemBindings::all);
    private final List<ResourceLocation> systems;

    private SystemBindings(List<ResourceLocation> systems) {
        this.systems = List.copyOf(systems);
    }

    public static SystemBindings single(ResourceLocation system) {
        return new SystemBindings(List.of(system));
    }

    public List<ResourceLocation> all() {
        return this.systems;
    }

    public Optional<ResourceLocation> current() {
        return this.systems.stream().findFirst();
    }

    public boolean bound() {
        return !this.systems.isEmpty();
    }
}
