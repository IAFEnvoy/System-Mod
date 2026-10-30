package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Built-in result data and its serialization codec.
 */
public record PlayerSnapshotOutput(UUID playerId, String name, float health, float maxHealth, int experienceLevel,
                                   String dimension, List<String> onlinePlayers, String language) implements CardData {
    public static final MapCodec<PlayerSnapshotOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("playerId").forGetter(PlayerSnapshotOutput::playerId),
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
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary("system.result.player");
        builder.put("player_id", this.playerId()).put("name", this.name()).put("health", this.health()).put("max_health", this.maxHealth())
                .put("experience_level", this.experienceLevel()).put("dimension", this.dimension())
                .put("language", this.language()).list("online_players", this.onlinePlayers(),
                        (prefix, name) -> builder.put(prefix + "player_name", name));
        return builder;
    }

    @Override
    public MapCodec<PlayerSnapshotOutput> codec() {
        return CODEC;
    }

}
