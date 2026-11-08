package com.iafenvoy.system.agent.chat;

import com.iafenvoy.system.config.file.AgentConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Immutable admission rules; world storage owns persistence independently of player preferences.
 */
public record AiAccess(ConversationMode defaultMode, Rule defaults, Map<UUID, Rule> players) {
    public static final Codec<AiAccess> CODEC = RecordCodecBuilder.create(i -> i.group(
            ConversationMode.CODEC.lenientOptionalFieldOf("defaultMode", ConversationMode.STANDARD).forGetter(AiAccess::defaultMode),
            Rule.CODEC.lenientOptionalFieldOf("defaults", Rule.INHERIT).forGetter(AiAccess::defaults),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Rule.CODEC).lenientOptionalFieldOf("players", Map.of()).forGetter(AiAccess::players)
    ).apply(i, AiAccess::new));
    public static final AiAccess DEFAULT = new AiAccess(ConversationMode.STANDARD, Rule.INHERIT, Map.of());

    public AiAccess {
        players = Map.copyOf(players);
    }

    public Permissions permissions(UUID player, boolean localOwner) {
        Rule rule = this.players.getOrDefault(player, Rule.INHERIT);
        Rule inherited = localOwner ? Rule.INHERIT : this.defaults;
        ConversationMode maximum = localOwner ? ConversationMode.FREE : this.defaultMode;
        // Explicit player grants override the default ceiling, just as explicit denials override host privileges.
        if (rule.allowFreeChat().orElse(false)) maximum = ConversationMode.FREE;
        else if (rule.allowAi().orElse(false) && maximum == ConversationMode.OFFLINE)
            maximum = ConversationMode.STANDARD;
        boolean ai = rule.allowAi().or(inherited::allowAi).orElse(true);
        return new Permissions(maximum, ai, ai && rule.allowFreeChat().or(inherited::allowFreeChat).orElse(true),
                rule.modelCallsPerDay().or(this.defaults::modelCallsPerDay).orElseGet(() -> AgentConfig.INSTANCE.get().modelCallsPerPlayerDay()), true);
    }

    public AiAccess withDefaultMode(ConversationMode mode) {
        return new AiAccess(mode, this.defaults, this.players);
    }

    public AiAccess updatePlayer(UUID player, UnaryOperator<Rule> change) {
        Map<UUID, Rule> rules = new HashMap<>(this.players);
        Rule next = change.apply(rules.getOrDefault(player, Rule.INHERIT));
        if (Rule.INHERIT.equals(next)) rules.remove(player);
        else rules.put(player, next);
        return new AiAccess(this.defaultMode, this.defaults, rules);
    }

    public record Permissions(ConversationMode maxMode, boolean allowAi, boolean allowFreeChat, int modelCallsPerDay,
                              boolean modelConfigured) {
        /**
         * Runtime readiness is independent of administrator grants and saved player preferences.
         */
        public Permissions withModelConfigured(boolean configured) {
            return new Permissions(this.maxMode, this.allowAi, this.allowFreeChat, this.modelCallsPerDay, configured);
        }

        public boolean allows(ConversationMode mode) {
            return mode == mode.atMost(this.maxMode)
                    && (!mode.usesAi() || this.modelConfigured && this.allowAi && this.modelCallsPerDay > 0 && (!mode.chats() || this.allowFreeChat));
        }

        public ConversationMode effective(ConversationMode mode) {
            ConversationMode limited = mode.atMost(this.maxMode);
            if (!this.modelConfigured || !this.allowAi || this.modelCallsPerDay == 0) return ConversationMode.OFFLINE;
            return this.allowFreeChat ? limited : limited.atMost(ConversationMode.STANDARD);
        }
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
