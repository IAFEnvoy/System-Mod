package com.iafenvoy.saturn.data.storage;

import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * A kind of state a host keeps, and the value kept for it. The instance a host stores IS this object: it holds its
 * own mutable state, ticks itself, and records whether it changed since the main loop last read it. Its own class is
 * what addresses that value, so nothing else has to name a slot, and every registered kind is stored and synced like
 * any other. A value is updated in place; a copy is only made when one is handed to something that compares by
 * value (an item stack's component).
 *
 * <p>A value ticks on a {@link DataStorageContext}, which says nothing about what kind of definition it belongs to,
 * so one kind serves any content a {@link DataStorageHolder} addresses.
 */
public abstract class DataStorage {
    public static final Codec<DataStorage> CODEC = SaturnRegistries.DATA_STORAGE_TYPE.byNameCodec().dispatch("type", DataStorage::codec, Function.identity());
    private boolean dirty;

    public abstract MapCodec<? extends DataStorage> codec();

    public abstract DataStorage copy();

    public void tick(DataStorageContext context) {
    }

    public void writtenAt(long gameTime) {
    }

    public final boolean isDirty() {
        boolean dirty = this.dirty;
        this.dirty = false;
        return dirty;
    }

    protected final void markDirty() {
        this.dirty = true;
    }

    public static String name(DataStorage value) {
        return SaturnRegistries.DATA_STORAGE_TYPE.getKey(value.codec()).toString();
    }
}
