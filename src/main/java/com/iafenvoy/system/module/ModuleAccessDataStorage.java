package com.iafenvoy.system.module;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;

/** Explicit enable/disable overrides for installed system modules. */
public final class ModuleAccessDataStorage extends DataStorage {
    public static final MapCodec<ModuleAccessDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.listOf().optionalFieldOf("added", List.of()).forGetter(value -> List.copyOf(value.added)),
            Identifier.CODEC.listOf().optionalFieldOf("removed", List.of()).forGetter(value -> List.copyOf(value.removed))
    ).apply(i, (added, removed) -> new ModuleAccessDataStorage(new LinkedHashSet<>(added), new LinkedHashSet<>(removed))));
    private final Set<Identifier> added = new LinkedHashSet<>(), removed = new LinkedHashSet<>();

    public ModuleAccessDataStorage() {
    }

    private ModuleAccessDataStorage(Set<Identifier> added, Set<Identifier> removed) {
        this.added.addAll(added);
        this.removed.addAll(removed);
    }

    public Set<Identifier> added() {
        return Set.copyOf(this.added);
    }

    public Set<Identifier> removed() {
        return Set.copyOf(this.removed);
    }

    public Selection selection() {
        return new Selection(this.added, this.removed);
    }

    public void replace(Selection selection) {
        this.added.clear();
        this.added.addAll(selection.added());
        this.removed.clear();
        this.removed.addAll(selection.removed());
        this.markDirty();
    }

    public boolean set(Identifier module, boolean enabled) {
        Set<Identifier> target = enabled ? this.added : this.removed;
        Set<Identifier> opposite = enabled ? this.removed : this.added;
        boolean changed = target.add(module);
        changed |= opposite.remove(module);
        if (changed) this.markDirty();
        return changed;
    }

    @Override
    public MapCodec<ModuleAccessDataStorage> codec() {
        return CODEC;
    }

    @Override
    public ModuleAccessDataStorage copy() {
        return new ModuleAccessDataStorage(this.added, this.removed);
    }

    public record Selection(Set<Identifier> added, Set<Identifier> removed) {
        public static final Selection EMPTY = new Selection(Set.of(), Set.of());

        public Selection {
            added = Set.copyOf(added);
            removed = Set.copyOf(removed);
        }
    }
}
