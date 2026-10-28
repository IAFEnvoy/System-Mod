package com.iafenvoy.system.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.stream.Stream;

/**
 * Names and descriptions are translation keys; module conditions are evaluated only by the server.
 */
public record SystemDefinition(String name, String description, List<ModuleDefinition> modules) {
    public static final SystemDefinition DEFAULT = new SystemDefinition("system.definition.default.name",
            "system.definition.default.description", Stream.of("usage", "player", "items", "signin", "inbox", "shop", "lottery", "advancement").map(path -> new ModuleDefinition(Identifier.fromNamespaceAndPath("system", path))).toList());
    public static final Codec<SystemDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(SystemDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(SystemDefinition::description),
            ModuleDefinition.CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(SystemDefinition::modules)
    ).apply(i, SystemDefinition::new));

    public SystemDefinition {
        modules = List.copyOf(modules);
    }
}
