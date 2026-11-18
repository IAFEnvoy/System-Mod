package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.stream.Stream;

/**
 * Only this private projection is sent to the reward selector; other players never enter its context.
 */
public record SignInDecision(Identifier rewardId, String reason) {
    public static final Codec<SignInDecision> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("rewardId").forGetter(SignInDecision::rewardId),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(SignInDecision::reason)
    ).apply(i, SignInDecision::new));

    public record Player(String name, float health, float maxHealth, int experienceLevel, String dimension,
                         String language) {
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

    /**
     * Prompt-local aliases and plain summaries never serialize item components or executable reward definitions.
     */
    public record Candidate(Identifier rewardId, String label, String contents) {
        public static final Codec<Candidate> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("rewardId").forGetter(Candidate::rewardId),
                Codec.STRING.fieldOf("label").forGetter(Candidate::label),
                Codec.STRING.fieldOf("contents").forGetter(Candidate::contents)
        ).apply(i, Candidate::new));

        /**
         * Invoke on the game thread because extensible content labels can read live registries.
         */
        public static Candidate of(int index, PersistHolder<Reward> reward) {
            String contents = String.join("; ", Stream.concat(
                            reward.value().items().stream().map(item -> MiscUtil.clip(item.item().getRegisteredName(), 96) + " x" + item.count()),
                            reward.value().contents().stream().map(stack -> MiscUtil.clip(stack.name().getString(), 96) + " x" + stack.amount()))
                    .limit(8).toList());
            return new Candidate(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "candidate/" + index),
                    MiscUtil.clip(reward.value().label().getString(), 80), MiscUtil.clip(contents, 320));
        }
    }

    public record Input(Player player, List<Candidate> candidates, String date) {
        public static final MapCodec<Input> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Player.CODEC.fieldOf("player").forGetter(Input::player),
                Candidate.CODEC.listOf().fieldOf("candidates").forGetter(Input::candidates),
                Codec.STRING.fieldOf("date").forGetter(Input::date)
        ).apply(i, Input::new));
    }
}
