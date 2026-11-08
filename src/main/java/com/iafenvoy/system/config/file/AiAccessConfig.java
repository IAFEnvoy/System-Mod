package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Server-owned admission rules; player preferences never modify or replace these permissions.
 */
public record AiAccessConfig(Rule defaults, Map<UUID, Rule> players) {
    public static final String FILE_NAME = "system-ai-access.json";
    private static final Codec<UUID> PLAYER_ID = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(UUID.fromString(value));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Invalid player UUID: " + value);
        }
    }, UUID::toString);
    public static final Codec<AiAccessConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rule.CODEC.lenientOptionalFieldOf("defaults", Rule.INHERIT).forGetter(AiAccessConfig::defaults),
            Codec.unboundedMap(PLAYER_ID, Rule.CODEC).lenientOptionalFieldOf("players", Map.of()).forGetter(AiAccessConfig::players)
    ).apply(i, AiAccessConfig::new));
    public static final ConfigInstance<AiAccessConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new AiAccessConfig(Rule.INHERIT, Map.of()));

    public AiAccessConfig {
        players = Map.copyOf(players);
    }

    public Permissions permissions(UUID player) {
        Rule rule = this.players.getOrDefault(player, Rule.INHERIT);
        boolean ai = rule.allowAi().or(this.defaults::allowAi).orElse(true);
        return new Permissions(ai, ai && rule.allowFreeChat().or(this.defaults::allowFreeChat).orElse(true),
                rule.modelCallsPerDay().or(this.defaults::modelCallsPerDay).orElseGet(() -> AgentConfig.INSTANCE.get().modelCallsPerPlayerDay()));
    }

    public AiAccessConfig updatePlayer(UUID player, UnaryOperator<Rule> change) {
        Map<UUID, Rule> rules = new HashMap<>(this.players);
        Rule next = change.apply(rules.getOrDefault(player, Rule.INHERIT));
        if (Rule.INHERIT.equals(next)) rules.remove(player);
        else rules.put(player, next);
        return new AiAccessConfig(this.defaults, rules);
    }

    public record Permissions(boolean allowAi, boolean allowFreeChat, int modelCallsPerDay) {
    }

    public record Rule(Optional<Boolean> allowAi, Optional<Boolean> allowFreeChat, Optional<Integer> modelCallsPerDay) {
        public static final Rule INHERIT = new Rule(Optional.empty(), Optional.empty(), Optional.empty());
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.lenientOptionalFieldOf("allowAi").forGetter(Rule::allowAi),
                Codec.BOOL.lenientOptionalFieldOf("allowFreeChat").forGetter(Rule::allowFreeChat),
                ExtraCodecs.NON_NEGATIVE_INT.lenientOptionalFieldOf("modelCallsPerDay").forGetter(Rule::modelCallsPerDay)
        ).apply(i, Rule::new));

        public Rule withAi(boolean enabled) {
            return new Rule(Optional.of(enabled), this.allowFreeChat, this.modelCallsPerDay);
        }

        public Rule withFreeChat(boolean enabled) {
            return new Rule(this.allowAi, Optional.of(enabled), this.modelCallsPerDay);
        }

        public Rule withModelCallsPerDay(int calls) {
            return new Rule(this.allowAi, this.allowFreeChat, Optional.of(calls));
        }
    }
}
