package com.iafenvoy.system.data.progress;

import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public record SignInRules(List<Identifier> dailyCycle, List<Rule> milestones) {
    public static final SignInRules DEFAULT = new SignInRules(List.of(), List.of());
    public static final Codec<SignInRules> CODEC = RecordCodecBuilder.<SignInRules>create(i -> i.group(
            Identifier.CODEC.listOf(0, 366).optionalFieldOf("daily_cycle", List.of()).forGetter(SignInRules::dailyCycle),
            Rule.CODEC.listOf(0, 128).optionalFieldOf("milestones", List.of()).forGetter(SignInRules::milestones)
    ).apply(i, SignInRules::new)).validate(rules -> rules.milestones.stream().map(Rule::id).distinct().count() != rules.milestones.size()
            ? DataResult.error(() -> "Duplicate sign-in milestone ID") : DataResult.success(rules));

    public SignInRules {
        dailyCycle = List.copyOf(dailyCycle);
        milestones = List.copyOf(milestones);
    }

    public Identifier fixed(SignInProgress progress) {
        return this.dailyCycle.isEmpty() ? null : this.dailyCycle.get((int) (progress.total() % this.dailyCycle.size()));
    }

    public List<Component> preview(SignInProgress progress, LocalDate date, ZoneId zone) {
        return this.milestones.stream().<Component>map(rule -> Component.translatable("system.signin.milestone.progress",
                Component.translatable("system.signin.metric." + rule.metric.getSerializedName()), switch (rule.metric) {
                    case TOTAL -> progress.total();
                    case STREAK -> progress.currentStreak(date, zone);
                    case WEEK -> progress.currentWeek(date, zone);
                    case MONTH -> progress.currentMonth(date, zone);
                }, rule.target, rule.reward.label())).toList();
    }

    public record Rule(Identifier id, Metric metric, int target, Reward reward) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.<Rule>create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Rule::id),
                Metric.CODEC.fieldOf("metric").forGetter(Rule::metric),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("target").forGetter(Rule::target),
                Reward.CODEC.codec().fieldOf("reward").forGetter(Rule::reward)
        ).apply(i, Rule::new)).validate(rule -> rule.metric == Metric.WEEK && rule.target > 7 || rule.metric == Metric.MONTH && rule.target > 31
                ? DataResult.error(() -> "Unreachable sign-in period target") : DataResult.success(rule));
    }

    public enum Metric implements StringRepresentable {
        TOTAL, STREAK, WEEK, MONTH;
        public static final Codec<Metric> CODEC = StringRepresentable.fromEnum(Metric::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
