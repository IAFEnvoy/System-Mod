package com.iafenvoy.system.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Names and descriptions are translation keys; module IDs are reserved metadata, not permissions.
 */
public record SystemDefinition(String name, String description, List<Identifier> modules) {
    public static final SystemDefinition DEFAULT = new SystemDefinition("system.definition.default.name",
            "system.definition.default.description", List.of());
    public static final Codec<SystemDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(SystemDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(SystemDefinition::description),
            Identifier.CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(SystemDefinition::modules)
    ).apply(i, SystemDefinition::new));

    public SystemDefinition {
        modules = List.copyOf(modules);
    }
}
