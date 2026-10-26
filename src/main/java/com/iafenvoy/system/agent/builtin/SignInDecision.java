package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.AgentCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Only this private projection is sent to the reward selector; other players never enter its context. */
public record SignInDecision(String rewardId, String reason) {
    public static final Codec<SignInDecision> CODEC = AgentCodecs.strict(RecordCodecBuilder.<SignInDecision>mapCodec(i -> i.group(
            Codec.string(0, 2000).fieldOf("rewardId").forGetter(SignInDecision::rewardId),
            Codec.string(0, 2000).optionalFieldOf("reason", "").forGetter(SignInDecision::reason)
    ).apply(i, SignInDecision::new))).codec();

    public record Player(String name, float health, float maxHealth, int experienceLevel, String dimension, String language) {
        public static final Codec<Player> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Player::name),
                Codec.FLOAT.fieldOf("health").forGetter(Player::health),
                Codec.FLOAT.fieldOf("maxHealth").forGetter(Player::maxHealth),
                Codec.INT.fieldOf("experienceLevel").forGetter(Player::experienceLevel),
                Codec.STRING.fieldOf("dimension").forGetter(Player::dimension),
                Codec.STRING.fieldOf("language").forGetter(Player::language)
        ).apply(i, Player::new));

        public static Player of(PlayerSnapshotOutput value) {
            return new Player(value.name(), value.health(), value.maxHealth(), value.experienceLevel(), value.dimension(), value.language());
        }
    }

    public record Input(Player player, List<PlayerData.Reward> candidates, String date) {
        public static final MapCodec<Input> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Player.CODEC.fieldOf("player").forGetter(Input::player),
                PlayerData.Reward.CODEC.listOf().fieldOf("candidates").forGetter(Input::candidates),
                Codec.STRING.fieldOf("date").forGetter(Input::date)
        ).apply(i, Input::new));
    }
}
