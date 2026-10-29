package com.iafenvoy.system.module;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.storage.DataStorageContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * A module owns the holder key; the independently registered type ID selects its codec.
 */
public record ModuleStorage<T extends DataStorage>(ResourceLocation owner, ResourceLocation id, Class<T> type, MapCodec<T> codec,
                                                   Supplier<T> factory) {
    public boolean ticking() {
        try {
            return this.type.getMethod("tick", DataStorageContext.class).getDeclaringClass() != DataStorage.class;
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException(error);
        }
    }
}
