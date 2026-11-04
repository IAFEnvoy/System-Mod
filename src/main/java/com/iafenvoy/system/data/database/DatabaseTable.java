package com.iafenvoy.system.data.database;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/**
 * A namespaced collection with one value codec; keys are local to that collection.
 */
public record DatabaseTable<T>(Identifier id, Codec<T> codec) {
}
