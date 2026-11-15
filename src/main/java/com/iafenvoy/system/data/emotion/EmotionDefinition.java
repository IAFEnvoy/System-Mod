package com.iafenvoy.system.data.emotion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared semantic catalog. Resource packs independently map these IDs to textures and animation frames.
 */
public record EmotionDefinition(String name, String description, String category, int order) {
    public static final Codec<EmotionDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(EmotionDefinition::name),
            Codec.STRING.lenientOptionalFieldOf("description", "").forGetter(EmotionDefinition::description),
            Codec.STRING.lenientOptionalFieldOf("category", "").forGetter(EmotionDefinition::category),
            Codec.INT.lenientOptionalFieldOf("order", 0).forGetter(EmotionDefinition::order)
    ).apply(i, EmotionDefinition::new));

    public static String catalogue(Registry<EmotionDefinition> registry) {
        return registry.entrySet().stream().sorted(Comparator
                        .<Map.Entry<ResourceKey<EmotionDefinition>, EmotionDefinition>>comparingInt(entry -> entry.getValue().order()).reversed()
                        .thenComparing(entry -> entry.getKey().identifier()))
                .map(entry -> {
                    EmotionDefinition value = entry.getValue();
                    return "- " + entry.getKey().identifier() + ": " + value.name()
                            + (value.description().isBlank() ? "" : "; " + value.description())
                            + (value.category().isBlank() ? "" : " [" + value.category() + "]");
                }).collect(Collectors.joining("\n"));
    }
}
