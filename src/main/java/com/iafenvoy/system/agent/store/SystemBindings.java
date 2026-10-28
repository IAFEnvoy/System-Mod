package com.iafenvoy.system.agent.store;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A collection leaves room for multiple systems; the current runtime permits only one binding.
 */
public final class SystemBindings {
    public static final int MAX_SYSTEMS = 1;
    public static final SystemBindings EMPTY = new SystemBindings(List.of());
    public static final Codec<SystemBindings> CODEC = Identifier.CODEC.listOf(0, MAX_SYSTEMS).xmap(SystemBindings::new, SystemBindings::all);
    private final List<Identifier> systems;

    private SystemBindings(List<Identifier> systems) {
        this.systems = List.copyOf(systems);
    }

    public static SystemBindings single(Identifier system) {
        return new SystemBindings(List.of(system));
    }

    public List<Identifier> all() {
        return this.systems;
    }

    public Optional<Identifier> current() {
        return this.systems.stream().findFirst();
    }

    public boolean bound() {
        return !this.systems.isEmpty();
    }
}
