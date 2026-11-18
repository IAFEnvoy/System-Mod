package com.iafenvoy.system.assistant.jobs;

import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.commerce.CommerceCosts;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.iafenvoy.system.data.input.JobStartInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.data.output.PaymentQuote;
import com.iafenvoy.system.data.output.RewardOutput;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.OnlineJobRun;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.OnlineJobsSystemModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.OnlineJobsDataStorage;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Worker-side transitions commit progress and reward entitlement in the same player transaction.
 */
public final class OnlineJobs {
    private record Quote(String id, PersistHolder<OnlineJobEntry> job, CommerceCosts.Prepared payment, long expires) {
    }

    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
    private final RegistryAccess registries;
    private final OnlineJobsSystemModule module;
    private final CatalogAccess access;
    private final List<PersistHolder<OnlineJobEntry>> entries;

    public OnlineJobs(RegistryAccess registries, OnlineJobsSystemModule module, CatalogAccess access) {
        this.registries = registries;
        this.module = module;
        this.access = access;
        Registry<OnlineJobEntry> jobs = registries.lookupOrThrow(SystemResourceKeys.ONLINE_JOB);
        this.entries = jobs.listElements().filter(holder -> holder.value().enabled())
                .map(PersistHolder::new).sorted(Comparator.<PersistHolder<OnlineJobEntry>>comparingInt(job -> job.value().order())
                        .reversed().thenComparing(PersistHolder::id)).toList();
    }

    private List<PersistHolder<OnlineJobEntry>> available() {
        // Tag membership can change on reload without rebuilding this module's immutable definitions.
        return this.entries.stream().filter(job -> this.module.allows(this.registries, job.id())).toList();
    }

    public OnlineJobsOutput query(ExecutionContext context, int offset) {
        context.ensureMemoryCurrent();
        Optional<OnlineJobRun> run = context.store().read(context.player(), data -> ModuleData.get(data, OnlineJobsSystemModule.STORAGE).getRun());
        return this.access.read(context, player -> {
            List<PersistHolder<OnlineJobEntry>> entries = this.available().stream().filter(job -> CatalogAccess.visible(player, job.value())).toList();
            int start = Math.min(Math.max(0, offset), entries.size());
            int end = Math.min(start + 18, entries.size());
            List<PersistHolder<OnlineJobEntry>> page = entries.subList(start, end);
            Map<Identifier, CommerceCatalog.Price> prices = new LinkedHashMap<>();
            Set<Identifier> locked = page.stream().filter(job -> {
                        CommerceCosts.Prepared payment = CommerceCosts.prepare(job.value().costs(), player, CostContext.of(player, "system:online_jobs").with("system:source", job.id()));
                        prices.put(job.id(), payment.getData());
                        return !CatalogAccess.usable(player, job.value()) || !payment.payable();
                    })
                    .map(PersistHolder::id).collect(Collectors.toUnmodifiableSet());
            return new OnlineJobsOutput(page, run, start, end < entries.size(), locked, prices, Optional.empty(), Optional.empty());
        });
    }

    public OnlineJobsOutput start(ExecutionContext context, JobStartInput input) {
        context.ensureMemoryCurrent();
        if (input.quote().isEmpty()) {
            boolean busy = context.store().read(context.player(), data -> ModuleData.get(data, OnlineJobsSystemModule.STORAGE).getRun().filter(run -> !run.claimed()).isPresent());
            if (busy) throw new AgentException(AgentFailure.JOB_ACTIVE);
            Quote quote = this.access.read(context, player -> {
                PersistHolder<OnlineJobEntry> job = this.available().stream().filter(value -> value.id().equals(input.job())).findFirst()
                        .orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
                CatalogAccess.require(player, job.value());
                CommerceCosts.Prepared payment = CommerceCosts.prepare(job.value().costs(), player, CostContext.of(player, "system:online_jobs").with("system:source", job.id()));
                if (!payment.payable()) throw new AgentException(AgentFailure.INSUFFICIENT_COST);
                return new Quote(UUID.randomUUID().toString(), job, payment, System.currentTimeMillis() + 120000);
            });
            this.quotes.entrySet().removeIf(entry -> entry.getValue().expires() < System.currentTimeMillis());
            if (this.quotes.size() >= 1024 && !this.quotes.containsKey(context.player()))
                throw new AgentException(AgentFailure.BUSY);
            this.quotes.put(context.player(), quote);
            OnlineJobsOutput output = this.query(context, 0);
            return new OnlineJobsOutput(output.jobs(), output.run(), output.offset(), output.hasMore(), output.lockedJobs(), output.prices(),
                    Optional.of(new PaymentQuote(quote.id(), quote.job().value().label(), quote.payment().getData())), Optional.of(quote.job().id()));
        }
        context.requirePlayerAction(input);
        Quote quote = this.quotes.get(context.player());
        if (quote == null || !quote.id().equals(input.quote().get()) || !quote.job().id().equals(input.job()) || quote.expires() < System.currentTimeMillis())
            throw new AgentException(AgentFailure.STALE_QUOTE);
        if (!this.quotes.remove(context.player(), quote)) throw new AgentException(AgentFailure.STALE_QUOTE);
        context.store().update(context.player(), data -> {
            context.check();
            OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
            if (storage.getRun().filter(run -> !run.claimed()).isPresent())
                throw new AgentException(AgentFailure.JOB_ACTIVE);
            storage.setRun(Optional.of(new OnlineJobRun(quote.id(), quote.job(), 0, false, OnlineJobRun.State.PREPARED, quote.payment().getData())));
            return null;
        });
        AtomicBoolean touched = new AtomicBoolean();
        try {
            this.access.read(context, player -> {
                CatalogAccess.require(player, quote.job().value());
                if (!this.module.allows(this.registries, quote.job().id()))
                    throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
                context.check();
                quote.payment().pay(player, touched);
                return true;
            });
            context.store().update(context.player(), data -> {
                OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
                OnlineJobRun run = storage.getRun().filter(value -> value.id().equals(quote.id())).orElseThrow(() -> new AgentException(AgentFailure.EFFECT_UNKNOWN));
                storage.setRun(Optional.of(run.state(OnlineJobRun.State.RUNNING)));
                return null;
            });
        } catch (RuntimeException error) {
            context.store().update(context.player(), data -> {
                OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
                storage.getRun().filter(run -> run.id().equals(quote.id())).ifPresent(run -> storage.setRun(touched.get() ? Optional.of(run.state(OnlineJobRun.State.UNKNOWN)) : Optional.empty()));
                return null;
            });
            throw new AgentException(touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(error), error);
        } finally {
            this.quotes.remove(context.player(), quote);
        }
        return this.query(context, 0);
    }

    public RewardOutput claim(ExecutionContext context, String id) {
        context.ensureMemoryCurrent();
        if (!context.available(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "deliver")))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return context.store().update(context.player(), data -> {
            context.check();
            OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
            OnlineJobRun run = storage.getRun().filter(current -> current.id().equals(id))
                    .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
            if (!run.ready()) throw new AgentException(AgentFailure.JOB_NOT_READY);
            String deliveryId = "job:" + run.id();
            SpaceDataStorage inbox = ModuleData.get(data, SpaceSystemModule.STORAGE);
            PersistHolder<Reward> reward = new PersistHolder<>(run.job().id(), run.job().value().reward());
            if (!run.claimed()) {
                inbox.assertDeliveryCount(deliveryId);
                inbox.putDeliveryIfAbsent(deliveryId, new Delivery(deliveryId, reward));
                storage.setRun(Optional.of(run.claim()));
            }
            RewardOutput output = new RewardOutput(reward, Optional.of(deliveryId), Component.translatable("system.result.job_claimed"), "");
            Delivery delivery = inbox.getDelivery(deliveryId);
            return delivery != null && "DELIVERED".equals(delivery.state) ? output.asDelivered() : output;
        });
    }

    public OnlineJobsOutput cancel(ExecutionContext context, String id) {
        context.ensureMemoryCurrent();
        context.store().update(context.player(), data -> {
            context.check();
            OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
            OnlineJobRun run = storage.getRun().filter(current -> current.id().equals(id))
                    .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
            // Completed output must be claimed, never accidentally discarded by an old cancellation button.
            if (run.ready() || run.state() != OnlineJobRun.State.RUNNING)
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            storage.setRun(Optional.empty());
            return null;
        });
        return this.query(context, 0);
    }
}
