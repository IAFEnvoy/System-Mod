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
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record OnlineJobsOutput(List<PersistHolder<OnlineJobEntry>> jobs, Optional<OnlineJobRun> run,
                               int offset, boolean hasMore, Set<Identifier> lockedJobs,
                               Map<Identifier, CommerceCatalog.Price> prices, Optional<PaymentQuote> quote,
                               Optional<Identifier> quoteJob) implements CardData {
    public static final MapCodec<OnlineJobsOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PersistHolder.codec(OnlineJobEntry.DISPLAY_CODEC).listOf().fieldOf("jobs").forGetter(OnlineJobsOutput::jobs),
            OnlineJobRun.CODEC.optionalFieldOf("run").forGetter(OnlineJobsOutput::run),
            Codec.INT.fieldOf("offset").forGetter(OnlineJobsOutput::offset),
            Codec.BOOL.fieldOf("hasMore").forGetter(OnlineJobsOutput::hasMore),
            Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("lockedJobs", Set.of()).forGetter(OnlineJobsOutput::lockedJobs),
            Codec.unboundedMap(Identifier.CODEC, CommerceCatalog.Price.CODEC).optionalFieldOf("prices", Map.of()).forGetter(OnlineJobsOutput::prices),
            PaymentQuote.CODEC.optionalFieldOf("quote").forGetter(OnlineJobsOutput::quote),
            Identifier.CODEC.optionalFieldOf("quoteJob").forGetter(OnlineJobsOutput::quoteJob)
    ).apply(i, OnlineJobsOutput::new));

    public OnlineJobsOutput {
        jobs = List.copyOf(jobs);
        lockedJobs = Set.copyOf(lockedJobs);
        prices = Map.copyOf(prices);
    }

    @Override
    public MapCodec<OnlineJobsOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "online_jobs"))
                .title("system.ui.nav_online_jobs").summary("system.result.online_jobs")
                .enable("quote", this.quote.isPresent()).enable("active", this.run.isPresent()).enable("busy", this.run.filter(current -> !current.claimed()).isPresent());
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
                    .actionParameter("disabled", this.lockedJobs.contains(job.id()));
        }
        this.run.ifPresent(current -> {
            builder.contents("current_", current.job().value().contents()).put("current_label", current.job().value().label())
                    .put("current_items", current.job().value().items().stream().map(ItemPreviewOutput::of).toList())
                    .put("current_status", Component.translatable(current.claimed() ? "system.jobs.claimed" : current.state() != OnlineJobRun.State.RUNNING ? "system.jobs.payment_" + current.state().getSerializedName() : current.ready() ? "system.jobs.ready" : "system.jobs.running"))
                    .put("current_time", Component.translatable("system.jobs.progress", current.elapsed() / 20, current.job().value().duration()))
                    .put("progress", 100.0 * current.elapsed() / current.job().value().ticks());
            if (current.ready() && !current.claimed())
                builder.serverButton("claim", "system.action.job_claim", action("job_claim"), Map.of("run", current.id()))
                        .actionParameter("footer", true);
            if (!current.claimed() && !current.ready() && current.state() == OnlineJobRun.State.RUNNING)
                builder.serverButton("cancel", "system.action.job_cancel", action("job_cancel"), Map.of("run", current.id()))
                        .actionParameter("footer", true);
        });
        this.quote.ifPresent(value -> {
            builder.put("quote_label", value.label()).put("quote_cost", PaymentQuote.describe(value.price()));
            builder.serverButton("confirm_start", "system.jobs.confirm_start", action("job_start"), Map.of("job", this.quoteJob.orElseThrow().toString(), "quote", value.reference()))
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

    private static Identifier action(String path) {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, path);
    }
}
