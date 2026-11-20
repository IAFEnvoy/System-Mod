package com.iafenvoy.system.data.result;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.progress.SignInProgress;
import com.iafenvoy.system.data.progression.ProgressionEstimate;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Daily sign-in outcome with today's candidate rewards.
 */
public record SignInStatus(String date, boolean signedIn, List<PersistHolder<Reward>> candidates, boolean historySaved,
                           boolean externalModel, Set<Identifier> lockedRewards,
                           Optional<ProgressionEstimate> progression,
                           SignInProgress progress, boolean fixed, String calendarZone,
                           List<Component> milestones, List<Component> activities) implements CardData {
    public static final MapCodec<SignInStatus> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("date").forGetter(SignInStatus::date),
            Codec.BOOL.fieldOf("signedIn").forGetter(SignInStatus::signedIn),
            PersistHolder.codec(Reward.CODEC).listOf().fieldOf("candidates").forGetter(SignInStatus::candidates),
            Codec.BOOL.fieldOf("historySaved").forGetter(SignInStatus::historySaved),
            Codec.BOOL.fieldOf("externalModel").forGetter(SignInStatus::externalModel),
            Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("lockedRewards", Set.of()).forGetter(SignInStatus::lockedRewards),
            ProgressionEstimate.CODEC.optionalFieldOf("progression").forGetter(SignInStatus::progression),
            SignInProgress.CODEC.optionalFieldOf("progress", SignInProgress.EMPTY).forGetter(SignInStatus::progress),
            Codec.BOOL.optionalFieldOf("fixed", false).forGetter(SignInStatus::fixed),
            Codec.STRING.optionalFieldOf("calendarZone", "UTC").forGetter(SignInStatus::calendarZone),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("milestones", List.of()).forGetter(SignInStatus::milestones),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("activities", List.of()).forGetter(SignInStatus::activities)
    ).apply(i, SignInStatus::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "sign_in_status");

    public SignInStatus(String date, boolean signedIn, List<PersistHolder<Reward>> candidates, boolean historySaved,
                        boolean externalModel, Set<Identifier> lockedRewards, Optional<ProgressionEstimate> progression,
                        SignInProgress progress, boolean fixed, String calendarZone, List<Component> milestones) {
        this(date, signedIn, candidates, historySaved, externalModel, lockedRewards, progression, progress, fixed, calendarZone, milestones, List.of());
    }

    public SignInStatus {
        candidates = List.copyOf(candidates);
        lockedRewards = Set.copyOf(lockedRewards);
        milestones = List.copyOf(milestones);
        activities = List.copyOf(activities);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary("system.result.sign_in_status");
        builder.put("date", this.date()).put("signed_in", this.signedIn())
                .enable("fixed", this.fixed)
                .put("counts", Component.translatable("system.signin.counts", this.progress.total(), this.progress.currentStreak(LocalDate.parse(this.date), ZoneId.of(this.calendarZone)), this.progress.longest()))
                .put("period_counts", Component.translatable("system.signin.period_counts", this.progress.currentWeek(LocalDate.parse(this.date), ZoneId.of(this.calendarZone)), this.progress.currentMonth(LocalDate.parse(this.date), ZoneId.of(this.calendarZone))))
                .put("milestones", this.milestones).put("activities", this.activities)
                .put("history_saved", this.historySaved()).put("external_model", this.externalModel())
                .list("candidates", this.candidates(), (prefix, reward) -> builder.reward(prefix, reward)
                        .enable(prefix + "condition_unmet", this.lockedRewards.contains(reward.id())));
        this.progression.ifPresent(estimate -> {
            builder.enable("offline_signin", true).enable("no_offline_rewards", !this.signedIn && this.candidates.isEmpty())
                    .enable("progression_bootstrap", estimate.value() <= 0)
                    .put("progression_summary", Component.translatable("system.signin.progression", Math.round(estimate.percentile() * 100),
                            String.format(Locale.ROOT, "%.2f", estimate.value()), estimate.samples(),
                            Component.translatable("system.progression.confidence." + estimate.confidence().getSerializedName())));
            if (estimate.value() > 0)
                builder.put("progression_range", Component.translatable("system.signin.range",
                        String.format(Locale.ROOT, "%.2f", estimate.lower()), String.format(Locale.ROOT, "%.2f", estimate.upper())));
        });
        return builder.serverButton("records", "system.records.title", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "record_query"), Map.of());
    }

    @Override
    public MapCodec<SignInStatus> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("sign_in", "", "", Boolean.toString(this.signedIn()), List.of()));
    }

}
