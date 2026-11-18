package com.iafenvoy.system.data.progress;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.catalog.ConditionalEntry;
import com.iafenvoy.system.data.catalog.EntryConditions;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * One lifetime threshold over server-recorded facts, with a stable registry identity.
 */
public record AchievementDefinition(Component label, Component description, Component series, Identifier event,
                                    Optional<Identifier> source, long target, Reward reward, int order,
                                    boolean enabled, EntryConditions conditions) implements ConditionalEntry {
    public static final Codec<AchievementDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(AchievementDefinition::label),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()).forGetter(AchievementDefinition::description),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("series", Component.translatable("system.achievements.general")).forGetter(AchievementDefinition::series),
            Identifier.CODEC.fieldOf("event").forGetter(AchievementDefinition::event),
            Identifier.CODEC.optionalFieldOf("source").forGetter(AchievementDefinition::source),
            ExtraCodecs.POSITIVE_LONG.fieldOf("target").forGetter(AchievementDefinition::target),
            Reward.CODEC.codec().fieldOf("reward").forGetter(AchievementDefinition::reward),
            Codec.INT.optionalFieldOf("order", 0).forGetter(AchievementDefinition::order),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(AchievementDefinition::enabled),
            EntryConditions.CODEC.forGetter(AchievementDefinition::conditions)
    ).apply(i, AchievementDefinition::new));

    public String counter() {
        return this.event + this.source.map(id -> "|" + id).orElse("");
    }

    public boolean matches(PlayerFact fact) {
        return this.event.equals(fact.event()) && this.source.map(fact.source()::equals).orElse(true);
    }

    public static String entitlement(Identifier id) {
        return "achievement:" + id;
    }
}
