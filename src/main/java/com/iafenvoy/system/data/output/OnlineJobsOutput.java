package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.OnlineJobRun;
import com.iafenvoy.system.util.CardEventValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record OnlineJobsOutput(List<PersistHolder<OnlineJobEntry>> jobs, List<OnlineJobRun> runs, int queueSize,
                               int offset, boolean hasMore, Set<Identifier> lockedJobs,
                               Map<Identifier, CommerceCatalog.Price> prices, Optional<PaymentQuote> quote,
                               Optional<Identifier> quoteJob) implements CardData {
    public static final MapCodec<OnlineJobsOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PersistHolder.codec(OnlineJobEntry.DISPLAY_CODEC).listOf().fieldOf("jobs").forGetter(OnlineJobsOutput::jobs),
            OnlineJobRun.CODEC.listOf(0, 64).optionalFieldOf("runs", List.of()).forGetter(OnlineJobsOutput::runs),
            Codec.intRange(1, 32).optionalFieldOf("queueSize", 1).forGetter(OnlineJobsOutput::queueSize),
            Codec.INT.fieldOf("offset").forGetter(OnlineJobsOutput::offset),
            Codec.BOOL.fieldOf("hasMore").forGetter(OnlineJobsOutput::hasMore),
            Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("lockedJobs", Set.of()).forGetter(OnlineJobsOutput::lockedJobs),
            Codec.unboundedMap(Identifier.CODEC, CommerceCatalog.Price.CODEC).optionalFieldOf("prices", Map.of()).forGetter(OnlineJobsOutput::prices),
            PaymentQuote.CODEC.optionalFieldOf("quote").forGetter(OnlineJobsOutput::quote),
            Identifier.CODEC.optionalFieldOf("quoteJob").forGetter(OnlineJobsOutput::quoteJob),
            OnlineJobRun.CODEC.optionalFieldOf("run").forGetter(_ -> Optional.empty())
    ).apply(i, (jobs, runs, capacity, offset, more, locked, prices, quote, quoteJob, legacy) ->
            new OnlineJobsOutput(jobs, runs.isEmpty() ? legacy.stream().toList() : runs, capacity, offset, more, locked, prices, quote, quoteJob)));

    public OnlineJobsOutput {
        jobs = List.copyOf(jobs);
        runs = List.copyOf(runs);
        lockedJobs = Set.copyOf(lockedJobs);
        prices = Map.copyOf(prices);
    }

    @Override
    public MapCodec<OnlineJobsOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        long occupied = this.runs.stream().filter(run -> !run.claimed()).count();
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "online_jobs"))
                .title("system.ui.nav_online_jobs").summary("system.result.online_jobs")
                .enable("quote", this.quote.isPresent()).enable("active", !this.runs.isEmpty())
                .put("queue_summary", Component.translatable("system.jobs.queue_summary", occupied, this.queueSize));
        builder.list("jobs", this.jobs, (prefix, job) -> builder.contents(prefix + "preview_", job.value().contents().stream()
                        .limit(Math.max(0, 2 - job.value().items().size())).toList()).put(prefix + "label", job.value().label())
                .put(prefix + "reward_text", CardBuilder.join(job.value().reward().stacks().stream().map(SpaceStack::label).toList(), " · "))
                .put(prefix + "description", job.value().description())
                .put(prefix + "cost", this.prices.containsKey(job.id()) ? PaymentQuote.describe(this.prices.get(job.id())) : Component.empty())
                .enable(prefix + "condition_unmet", this.lockedJobs.contains(job.id()))
                .put(prefix + "duration", Component.translatable("system.jobs.duration", job.value().duration()))
                .put(prefix + "items", job.value().items().stream().limit(2).map(ItemPreviewOutput::of).toList()));
        // Keep job actions first so grid indices address the matching action.
        for (int index = 0; index < this.jobs.size(); index++) {
            PersistHolder<OnlineJobEntry> job = this.jobs.get(index);
            builder.serverButton("start." + index, "system.action.job_start", action("job_start"), Map.of("job", job.id().toString()),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("label", job.value().label())
                    .actionParameter("disabled", this.lockedJobs.contains(job.id()) || occupied >= this.queueSize);
        }
        builder.list("runs", this.runs, (prefix, current) -> {
            builder.contents(prefix + "preview_", current.job().value().contents().stream().limit(Math.max(0, 2 - current.job().value().items().size())).toList())
                    .put(prefix + "items", current.job().value().items().stream().limit(2).map(ItemPreviewOutput::of).toList())
                    .put(prefix + "label", current.job().value().label())
                    .put(prefix + "reward", CardBuilder.join(current.job().value().reward().stacks().stream().map(SpaceStack::label).toList(), " · "))
                    .put(prefix + "cost", PaymentQuote.describe(current.price()))
                    .put(prefix + "status", status(current))
                    .put(prefix + "time", Component.translatable("system.jobs.progress", current.elapsed() / 20, current.job().value().duration()))
                    .put(prefix + "progress", 100.0 * current.elapsed() / current.job().value().ticks())
                    .enable(prefix + "progress_visible", current.state() == OnlineJobRun.State.RUNNING)
                    .enable(prefix + "can_claim", current.ready() && !current.claimed())
                    .enable(prefix + "can_cancel", current.cancelable())
                    .put(prefix + "claim_value", CardEventValue.encode(action("job_claim"), Map.of("run", current.id())))
                    .put(prefix + "cancel_value", CardEventValue.encode(action("job_cancel"), Map.of("run", current.id())));
            if (current.ready() && !current.claimed()) builder.serverButton("claim." + current.id(), "system.action.job_claim", action("job_claim"), Map.of("run", current.id()), CardButton.Placement.BODY, CardButton.Behavior.REPLACE);
            if (current.cancelable())
                builder.serverButton("cancel." + current.id(), "system.action.job_cancel", action("job_cancel"), Map.of("run", current.id()), CardButton.Placement.BODY, CardButton.Behavior.REPLACE);
        });
        this.quote.ifPresent(value -> {
            builder.put("quote_label", value.label()).put("quote_cost", PaymentQuote.describe(value.price()));
            builder.serverButton("confirm_start", "system.jobs.confirm_start", action("job_start"), Map.of("job", this.quoteJob.orElseThrow().toString(), "quote", value.reference()))
                    .actionParameter("disabled", occupied >= this.queueSize)
                    .actionParameter("footer", true);
        });
        builder.serverButton("refresh", "system.action.job_refresh", action("online_jobs"), Map.of("offset", Integer.toString(this.offset)))
                .actionParameter("footer", true);
        if (this.offset > 0)
            builder.serverButton("previous", "system.action.previous", action("online_jobs"), Map.of("offset", Integer.toString(Math.max(0, this.offset - 18))))
                    .actionParameter("footer", true);
        if (this.hasMore)
            builder.serverButton("next", "system.action.more_items", action("online_jobs"), Map.of("offset", Integer.toString(this.offset + this.jobs.size())))
                    .actionParameter("footer", true);
        return builder;
    }

    private static Component status(OnlineJobRun run) {
        return Component.translatable(run.claimed() ? "system.jobs.claimed" : switch (run.state()) {
            case PREPARED -> "system.jobs.payment_prepared";
            case UNKNOWN -> "system.jobs.payment_unknown";
            case QUEUED -> "system.jobs.queued";
            case RUNNING -> run.ready() ? "system.jobs.ready" : "system.jobs.running";
        });
    }

    private static Identifier action(String path) {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, path);
    }
}
