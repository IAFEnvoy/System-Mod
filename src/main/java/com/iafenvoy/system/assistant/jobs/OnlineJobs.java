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
        List<OnlineJobRun> runs = context.store().read(context.player(), data -> ModuleData.get(data, OnlineJobsSystemModule.STORAGE).getRuns().stream().filter(run -> !run.claimed()).toList());
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
            return new OnlineJobsOutput(page, runs, this.module.queueSize(), start, end < entries.size(), locked, prices, Optional.empty(), Optional.empty());
        });
    }

    public OnlineJobsOutput start(ExecutionContext context, JobStartInput input) {
        context.ensureMemoryCurrent();
        if (input.quote().isEmpty()) {
            boolean full = context.store().read(context.player(), data -> ModuleData.get(data, OnlineJobsSystemModule.STORAGE).occupied() >= this.module.queueSize());
            if (full) throw new AgentException(AgentFailure.JOB_QUEUE_FULL);
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
            return new OnlineJobsOutput(output.jobs(), output.runs(), output.queueSize(), output.offset(), output.hasMore(), output.lockedJobs(), output.prices(),
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
            storage.add(new OnlineJobRun(quote.id(), quote.job(), 0, false, OnlineJobRun.State.PREPARED, quote.payment().getData()), this.module.queueSize());
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
                OnlineJobRun run = storage.find(quote.id());
                storage.replace(run.state(OnlineJobRun.State.QUEUED));
                return null;
            });
        } catch (RuntimeException error) {
            context.store().update(context.player(), data -> {
                OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
                if (touched.get()) storage.replace(storage.find(quote.id()).state(OnlineJobRun.State.UNKNOWN));
                else storage.remove(quote.id());
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
            String deliveryId = "job:" + id;
            SpaceDataStorage inbox = ModuleData.get(data, SpaceSystemModule.STORAGE);
            Delivery delivery = inbox.getDelivery(deliveryId);
            // Space entitlements outlive the bounded queue receipts, including historical claim buttons.
            if (delivery == null) {
                OnlineJobRun run = storage.find(id);
                if (!run.ready()) throw new AgentException(AgentFailure.JOB_NOT_READY);
                if (run.claimed()) throw new AgentException(AgentFailure.STALE_REFERENCE);
                PersistHolder<Reward> reward = new PersistHolder<>(run.job().id(), run.job().value().reward());
                inbox.assertDeliveryCount(deliveryId);
                inbox.putDeliveryIfAbsent(deliveryId, new Delivery(deliveryId, reward));
                storage.replace(run.claim());
                delivery = inbox.getDelivery(deliveryId);
            }
            RewardOutput output = new RewardOutput(delivery.reward, Optional.of(deliveryId), Component.translatable("system.result.job_claimed"), "");
            return "DELIVERED".equals(delivery.state) ? output.asDelivered() : output;
        });
    }

    public OnlineJobsOutput cancel(ExecutionContext context, String id) {
        context.ensureMemoryCurrent();
        context.store().update(context.player(), data -> {
            context.check();
            OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
            OnlineJobRun run = storage.find(id);
            // Completed output must be claimed, never accidentally discarded by an old cancellation button.
            if (!run.cancelable())
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            storage.remove(id);
            return null;
        });
        return this.query(context, 0);
    }
}
