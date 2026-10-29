package com.iafenvoy.system.data;

import com.iafenvoy.saturn.util.codec.CollectionCodecs;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Names and descriptions are translation keys; module conditions are evaluated only by the server.
 */
public record SystemDefinition(String name, String description, List<ModuleDefinition> modules) {
    public static final SystemDefinition DEFAULT = new SystemDefinition("system.definition.default.name", "system.definition.default.description", List.of(new ModuleDefinition(Either.right(TagKey.create(SystemResourceKeys.SYSTEM_MODULE, ResourceLocation.fromNamespaceAndPath("system", "default"))))));
    public static final Codec<SystemDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(SystemDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(SystemDefinition::description),
            CollectionCodecs.list(ModuleDefinition.CODEC).optionalFieldOf("modules", List.of()).forGetter(SystemDefinition::modules)
    ).apply(i, SystemDefinition::new));

    public SystemDefinition {
        modules = List.copyOf(modules);
    }
}
