package com.iafenvoy.system.agent.database;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/**
 * A namespaced collection with one value codec; keys are local to that collection.
 */
public record DatabaseTable<T>(ResourceLocation id, Codec<T> codec) {
}
