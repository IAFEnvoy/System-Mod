package com.iafenvoy.system.data.progress;

import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.data.catalog.ConditionalEntry;
import com.iafenvoy.system.data.catalog.EntryConditions;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record FactRewardRule(Identifier event, Optional<Identifier> source, List<String> subjects, Frequency frequency,
                             Reward reward, boolean enabled, EntryConditions conditions) implements ConditionalEntry {
    public static final Codec<FactRewardRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("event").forGetter(FactRewardRule::event),
            Identifier.CODEC.optionalFieldOf("source").forGetter(FactRewardRule::source),
            Codec.STRING.listOf().optionalFieldOf("subjects", List.of()).forGetter(FactRewardRule::subjects),
            Frequency.CODEC.optionalFieldOf("frequency", Frequency.SUBJECT).forGetter(FactRewardRule::frequency),
            Reward.CODEC.codec().fieldOf("reward").forGetter(FactRewardRule::reward),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(FactRewardRule::enabled),
            EntryConditions.CODEC.forGetter(FactRewardRule::conditions)
    ).apply(i, FactRewardRule::new));

    public FactRewardRule {
        subjects = List.copyOf(subjects);
    }

    public boolean matches(PlayerFact fact) {
        return this.event.equals(fact.event()) && this.source.map(fact.source()::equals).orElse(true)
                && (this.subjects.isEmpty() || this.subjects.contains(fact.subject()));
    }

    public String entitlement(Identifier rule, PlayerFact fact, ZoneId zone) {
        LocalDate day = BusinessCalendar.date(fact.timestamp(), zone);
        String period = switch (this.frequency) {
            case EVENT -> fact.occurrence();
            case DAILY -> day.toString();
            case WEEKLY -> BusinessCalendar.week(day).toString();
            case MONTHLY -> BusinessCalendar.month(day).toString();
            case SUBJECT -> "once";
        };
        return "fact:" + PlayerFact.key(rule.toString(), fact.event().toString(), fact.source().toString(), fact.subject(), period);
    }

    public enum Frequency implements StringRepresentable {
        SUBJECT, EVENT, DAILY, WEEKLY, MONTHLY;
        public static final Codec<Frequency> CODEC = StringRepresentable.fromEnum(Frequency::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
