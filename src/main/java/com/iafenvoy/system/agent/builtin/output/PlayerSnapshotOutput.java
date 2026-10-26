package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Built-in result data and its serialization codec. */
public record PlayerSnapshotOutput(String name, float health, float maxHealth, int experienceLevel,
                             String dimension, List<String> onlinePlayers, String language) {
    public static final Codec<PlayerSnapshotOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PlayerSnapshotOutput::name),
            Codec.FLOAT.fieldOf("health").forGetter(PlayerSnapshotOutput::health),
            Codec.FLOAT.fieldOf("maxHealth").forGetter(PlayerSnapshotOutput::maxHealth),
            Codec.INT.fieldOf("experienceLevel").forGetter(PlayerSnapshotOutput::experienceLevel),
            Codec.STRING.fieldOf("dimension").forGetter(PlayerSnapshotOutput::dimension),
            Codec.STRING.listOf().fieldOf("onlinePlayers").forGetter(PlayerSnapshotOutput::onlinePlayers),
            Codec.STRING.fieldOf("language").forGetter(PlayerSnapshotOutput::language)
    ).apply(i, PlayerSnapshotOutput::new));

    public PlayerSnapshotOutput {
        onlinePlayers = List.copyOf(onlinePlayers);
    }
}
