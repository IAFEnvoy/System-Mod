package com.iafenvoy.system.config.file;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.config.AdvancementRule;
import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Trusted server event bindings, independent of model settings and reloadable reward definitions.
 */
public record AdvancementConfig(List<AdvancementRule> rules) {
    public static final String FILE_NAME = "system-advancements.json";
    public static final Codec<AdvancementConfig> CODEC = RecordCodecBuilder.create(i -> {
        Codec<List<AdvancementRule>> codec = AdvancementRule.CODEC.listOf();
        return i.group(
                MiscCodecs.checked(codec, rules1 -> rules1.stream().map(AdvancementRule::id).distinct().count() == rules1.size())
                        .lenientOptionalFieldOf("rules", List.of()).forGetter(AdvancementConfig::rules)
        ).apply(i, AdvancementConfig::new);
    });
    public static final ConfigInstance<AdvancementConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new AdvancementConfig(List.of()));

    public AdvancementConfig {
        rules = List.copyOf(rules);
    }
}
