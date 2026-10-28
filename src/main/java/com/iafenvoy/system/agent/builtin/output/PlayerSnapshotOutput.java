package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Built-in result data and its serialization codec.
 */
public record PlayerSnapshotOutput(String name, float health, float maxHealth, int experienceLevel,
                                   String dimension, List<String> onlinePlayers, String language) implements CardData {
    public static final MapCodec<PlayerSnapshotOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PlayerSnapshotOutput::name),
            Codec.FLOAT.fieldOf("health").forGetter(PlayerSnapshotOutput::health),
            Codec.FLOAT.fieldOf("maxHealth").forGetter(PlayerSnapshotOutput::maxHealth),
            Codec.INT.fieldOf("experienceLevel").forGetter(PlayerSnapshotOutput::experienceLevel),
            Codec.STRING.fieldOf("dimension").forGetter(PlayerSnapshotOutput::dimension),
            Codec.STRING.listOf().fieldOf("onlinePlayers").forGetter(PlayerSnapshotOutput::onlinePlayers),
            Codec.STRING.fieldOf("language").forGetter(PlayerSnapshotOutput::language)
    ).apply(i, PlayerSnapshotOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:player");

    public PlayerSnapshotOutput {
        onlinePlayers = List.copyOf(onlinePlayers);
    }

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.put("name", this.name()).put("health", this.health()).put("max_health", this.maxHealth())
                .put("experience_level", this.experienceLevel()).put("dimension", this.dimension())
                .put("language", this.language()).list("onlinePlayers", this.onlinePlayers(),
                        (prefix, name) -> builder.put(prefix + "player_name", name));
    }

    @Override
    public MapCodec<PlayerSnapshotOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
