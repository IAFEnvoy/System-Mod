package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.AdvancementRule;
import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Trusted server event bindings, independent of model settings and reloadable reward definitions.
 */
public record AdvancementConfig(List<AdvancementRule> rules) {
    public static final String FILE_NAME = "system-advancements.json";
    public static final Codec<AdvancementConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            CodecIO.checked(AdvancementRule.CODEC.listOf(), rules -> rules.stream().map(AdvancementRule::id).distinct().count() == rules.size())
                    .lenientOptionalFieldOf("rules", List.of()).forGetter(AdvancementConfig::rules)
    ).apply(i, AdvancementConfig::new));
    public static final ConfigInstance<AdvancementConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new AdvancementConfig(List.of()));

    public AdvancementConfig {
        rules = List.copyOf(rules);
    }
}
