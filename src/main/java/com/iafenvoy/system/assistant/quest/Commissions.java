package com.iafenvoy.system.assistant.quest;

import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.commerce.CommerceCosts;
import com.iafenvoy.system.assistant.space.SpaceTransfer;
import com.iafenvoy.system.data.card.SpeechContent;
import com.iafenvoy.system.data.catalog.CommissionDefinition;
import com.iafenvoy.system.data.catalog.ShopPurchaseLimit;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.output.*;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.iafenvoy.system.data.store.CommissionRun;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.CommissionSystemModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.CommissionDataStorage;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fixed delivery quests share presentation with other providers, but own acceptance and settlement.
 */
public final class Commissions implements QuestProvider.Lifecycle, QuestProvider.Submission, QuestProvider.Rewards, QuestProvider.Tracking, PlayerModule {
    public static final QuestSource SOURCE = new QuestSource(CommissionSystemModule.ID, Component.translatable("system.commission.source"));
    private final AgentEngine engine;
    private final RegistryAccess registries;
    private final CatalogAccess access;
    private final MinecraftTools game;
    private final Map<Identifier, CommissionSystemModule> modules = new LinkedHashMap<>();
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Settlement> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private record Quote(String id, Identifier definition, String revision, String run, CommerceCosts.Prepared payment,
                         long expires) {
    }

    private static final class Settlement {
        final UUID player;
        final AgentStore store;
        final Identifier definition;
        final String run;
        volatile long completedAt;
        final ZoneId zone;
        final AtomicBoolean touched = new AtomicBoolean(), saving = new AtomicBoolean();
        final CompletableFuture<Void> result = new CompletableFuture<>();
        volatile RuntimeException error;
        volatile boolean ready;
        volatile long retryAt;

        Settlement(ExecutionContext context, Identifier definition, String run) {
            this.player = context.player();
            this.store = context.store();
            this.definition = definition;
            this.run = run;
            this.completedAt = System.currentTimeMillis();
            this.zone = BusinessCalendar.zone();
        }
    }

    private Commissions(ModuleContext context) {
        this.engine = context.engine();
        this.registries = context.get(ContextTypes.GAME).server().registryAccess();
        this.access = context.get(ContextTypes.CATALOGS).access();
        this.game = context.get(ContextTypes.GAME);
    }

    public static Commissions service(ModuleContext context) {
        return context.sharedRuntime(Commissions.class, () -> new Commissions(context));
    }

    public void register(Identifier owner, CommissionSystemModule module) {
        this.modules.put(owner, module);
    }

    @Override
    public QuestSource source() {
        return SOURCE;
    }

    private void check(ExecutionContext context) {
        context.ensureMemoryCurrent();
        this.checkLive(context);
    }

    private void checkLive(ExecutionContext context) {
        context.check();
        if (this.closed || this.modules.keySet().stream().noneMatch(owner -> this.engine.moduleEnabled(context.player(), owner)))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    private boolean allowed(UUID player, Identifier id) {
        return this.modules.entrySet().stream().anyMatch(entry -> this.engine.moduleEnabled(player, entry.getKey())
                && entry.getValue().allows(this.registries, id));
    }

    private int capacity(UUID player) {
        return this.modules.entrySet().stream().filter(entry -> this.engine.moduleEnabled(player, entry.getKey()))
                .mapToInt(entry -> entry.getValue().capacity()).max().orElse(0);
    }

    private Identifier id(String value) {
        try {
            QuestKey key = QuestKey.parse(value);
            if (!key.source().equals(SOURCE.id())) throw new IllegalArgumentException("Wrong commission source");
            return Identifier.parse(key.localId());
        } catch (IllegalArgumentException error) {
            throw new AgentException(AgentFailure.INVALID_INPUT, error);
        }
    }

    private CommissionDataStorage.Entry entry(ExecutionContext context, Identifier id) {
        return context.store().read(context.player(), data -> ModuleData.get(data, CommissionSystemModule.STORAGE).entry(id));
    }

    private CommissionDefinition definition(Identifier id, CommissionDataStorage.Entry entry) {
        return entry.run().filter(CommissionRun::occupied).map(run -> run.definition().value())
                .orElseGet(() -> this.registries.lookupOrThrow(SystemResourceKeys.COMMISSION).getOptional(id)
                        .or(() -> entry.run().map(run -> run.definition().value()))
                        .orElseThrow(() -> new AgentException(AgentFailure.QUEST_UNAVAILABLE)));
    }

    private CommerceCosts.Prepared payment(ServerPlayer player, Identifier id, CommissionDefinition definition) {
        return CommerceCosts.prepare(definition.costs(), player, CostContext.of(player, "system:commission").with("system:source", id));
    }

    private List<QuestTaskOutput> tasks(CommissionDefinition definition, CommerceCosts.Prepared payment, boolean completed) {
        return List.of(new QuestTaskOutput(Component.translatable("system.commission.materials"), completed || payment.payable() ? 1 : 0,
                1, payment.getData().items(), List.of()));
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> query(ExecutionContext context, QuestQueryInput input) {
        return this.query(context, input, false);
    }

    private CompletionStage<List<QuestBriefOutput>> query(ExecutionContext context, QuestQueryInput input, boolean find) {
        this.check(context);
        Map<Identifier, CommissionDataStorage.Entry> saved = context.store().read(context.player(), data -> Map.copyOf(ModuleData.get(data, CommissionSystemModule.STORAGE).entries()));
        return CompletableFuture.completedFuture(this.access.read(context, player -> {
            this.checkLive(context);
            Map<Identifier, CommissionDefinition> candidates = new LinkedHashMap<>();
            this.registries.lookupOrThrow(SystemResourceKeys.COMMISSION).listElements()
                    .filter(holder -> this.allowed(context.player(), holder.key().identifier()) && CatalogAccess.visible(player, holder.value()))
                    .forEach(holder -> candidates.put(holder.key().identifier(), holder.value()));
            saved.forEach((id, entry) -> entry.run().filter(CommissionRun::occupied).ifPresent(run -> candidates.put(id, run.definition().value())));
            List<QuestBriefOutput> result = new ArrayList<>();
            candidates.entrySet().stream().sorted(Comparator.<Map.Entry<Identifier, CommissionDefinition>>comparingInt(entry -> entry.getValue().order()).reversed()
                    .thenComparing(Map.Entry::getKey)).forEach(candidate -> {
                Identifier id = candidate.getKey();
                CommissionDefinition definition = candidate.getValue();
                CommissionDataStorage.Entry entry = saved.getOrDefault(id, CommissionDataStorage.Entry.EMPTY);
                CommissionRun run = entry.run().orElse(null);
                boolean completed = run != null && run.completed();
                boolean claimed = run != null && run.state() == CommissionRun.State.CLAIMED;
                boolean accepted = run != null && run.state() == CommissionRun.State.ACCEPTED;
                boolean available = entry.available(definition.limit(), System.currentTimeMillis(), BusinessCalendar.zone());
                if (input.view() == QuestQueryInput.View.CLAIMABLE && (!completed || claimed)) return;
                if (input.view() == QuestQueryInput.View.SUBMITTABLE && (!accepted || !this.payment(player, id, definition).payable()))
                    return;
                if (!find && input.view() == QuestQueryInput.View.ACTIVE && (completed && !claimed || !available && (run == null || !run.occupied())))
                    return;
                String query = input.query().strip().toLowerCase(Locale.ROOT);
                if (!query.isEmpty() && !query.equals("*") && !id.toString().toLowerCase(Locale.ROOT).contains(query) && !definition.label().getString().toLowerCase(Locale.ROOT).contains(query))
                    return;
                if (result.size() >= 512) return;
                result.add(new QuestBriefOutput(0, SOURCE.key(id.toString()), definition.label(), definition.chapter(), completed && !(claimed && available) ? 100 : 0,
                        definition.chapter().getString(), "commission", SOURCE.name(), SOURCE, definition.icon(), entry.revision()));
            });
            return List.copyOf(result);
        }));
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> find(ExecutionContext context, QuestFindInput input) {
        // Direct details remain available for completed and locked entries; list selectors still carry a revision.
        return this.query(context, new QuestQueryInput(input.query(), QuestQueryInput.View.ACTIVE), true);
    }

    @Override
    public CompletionStage<QuestOutput> detail(ExecutionContext context, QuestDetailInput input, Optional<String> revision) {
        this.check(context);
        Identifier id = this.id(input.questId());
        CommissionDataStorage.Entry entry = this.entry(context, id);
        if (revision.filter(value -> !value.equals(entry.revision())).isPresent())
            throw new AgentException(AgentFailure.STALE_QUEST_LIST);
        return CompletableFuture.completedFuture(this.access.read(context, player -> this.detail(player, context, id, entry)));
    }

    private QuestOutput detail(ServerPlayer player, ExecutionContext context, Identifier id, CommissionDataStorage.Entry entry) {
        this.checkLive(context);
        CommissionDefinition definition = this.definition(id, entry);
        CommissionRun run = entry.run().orElse(null);
        if ((run == null || !run.occupied()) && (!this.allowed(context.player(), id) || !CatalogAccess.visible(player, definition)))
            throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
        boolean pending = run != null && run.state() == CommissionRun.State.COMPLETED;
        boolean accepted = run != null && run.state() == CommissionRun.State.ACCEPTED;
        CommerceCosts.Prepared payment = this.payment(player, id, definition);
        boolean canAccept = (run == null || !run.occupied()) && this.registries.lookupOrThrow(SystemResourceKeys.COMMISSION).getOptional(id).isPresent()
                && CatalogAccess.usable(player, definition)
                && entry.available(definition.limit(), System.currentTimeMillis(), BusinessCalendar.zone());
        boolean completed = run != null && run.completed() && !canAccept;
        String state = run == null || canAccept ? "available" : run.state().getSerializedName();
        MutableComponent quota = Component.translatable("system.commission.quota", Component.translatable("system.commission.period." + definition.limit().period().getSerializedName()),
                definition.limit().period() == ShopPurchaseLimit.Period.LIFETIME ? entry.total()
                        : definition.limit().period().start(System.currentTimeMillis(), BusinessCalendar.zone()) == entry.periodStart() ? entry.count() : 0,
                definition.limit().count());
        Component eligibility = definition.conditions().activity().description().<Component>map(value -> value.copy().append(" · ").append(quota)).orElse(quota);
        QuestLifecycleOutput lifecycle = new QuestLifecycleOutput(entry.revision(), Component.translatable("system.commission.state." + state),
                canAccept, accepted, Optional.empty(), Optional.of(eligibility));
        Reward reward = run != null && run.occupied() ? run.definition().value().reward() : definition.reward();
        QuestRewardOutput preview = new QuestRewardOutput("reward", reward.label(), pending, pending ? "AVAILABLE" : completed ? "CLAIMED" : "LOCKED",
                Optional.empty(), 0, false, pending ? Optional.of(SOURCE.key(run.id())) : Optional.empty(),
                reward.items().stream().map(ItemPreviewOutput::of).toList(), List.of(), reward.contents());
        String description = definition.description().getString();
        SectionPageOutput page = new SectionPageOutput(0, 0, false);
        return new QuestOutput(SOURCE.key(id.toString()), definition.label(), definition.chapter(), description, completed ? 100 : 0,
                completed, accepted && payment.payable() ? QuestSubmission.SUBMIT : QuestSubmission.NONE, false, pending,
                this.tasks(definition, payment, completed), List.of(preview), page, page, page, SpeechContent.of(definition.label()), SOURCE, lifecycle);
    }

    @Override
    public CompletionStage<QuestSubmitOutput> submit(ExecutionContext context, QuestSubmitInput input, Optional<String> revision) {
        this.check(context);
        if (input.checkmarkOnly()) throw new AgentException(AgentFailure.UNSUPPORTED);
        Identifier id = this.id(input.questId());
        CommissionDataStorage.Entry entry = this.entry(context, id);
        if (revision.filter(value -> !value.equals(entry.revision())).isPresent())
            throw new AgentException(AgentFailure.STALE_QUEST_LIST);
        CommissionRun run = entry.run().filter(value -> value.state() == CommissionRun.State.ACCEPTED)
                .orElseThrow(() -> new AgentException(AgentFailure.COMMISSION_STATE));
        return CompletableFuture.completedFuture(this.access.read(context, player -> {
            this.checkLive(context);
            CommerceCosts.Prepared payment = this.payment(player, id, run.definition().value());
            if (!payment.payable()) throw new AgentException(AgentFailure.INSUFFICIENT_COST);
            Quote quote = new Quote(UUID.randomUUID().toString(), id, entry.revision(), run.id(), payment, System.currentTimeMillis() + 120000);
            if (this.quotes.size() >= 1024 && !this.quotes.containsKey(context.player()))
                throw new AgentException(AgentFailure.BUSY);
            this.quotes.put(context.player(), quote);
            return new QuestSubmitOutput(input.questId(), run.definition().value().label(), this.tasks(run.definition().value(), payment, false), false, false, SOURCE,
                    new QuestLifecycleOutput(entry.revision(), Component.translatable("system.commission.state.accepted"), false, false,
                            Optional.of(new PaymentQuote(quote.id(), run.definition().value().label(), payment.getData())), Optional.empty()));
        }));
    }

    @Override
    public CompletionStage<QuestOutput> change(ExecutionContext context, QuestLifecycleInput input) {
        this.check(context);
        context.requirePlayerAction(input);
        Identifier id = this.id(input.questId());
        if (input.operation().equals("deliver")) return this.deliver(context, input, id)
                .thenCompose(_ -> this.detail(context, QuestDetailInput.byId(input.questId()), Optional.empty()));
        if (!Set.of("accept", "abandon").contains(input.operation()))
            throw new AgentException(AgentFailure.INVALID_INPUT);
        if (!this.active.add(context.player())) throw new AgentException(AgentFailure.BUSY);
        try {
            CommissionDataStorage.Entry previous = this.entry(context, id);
            if (!previous.revision().equals(input.revision())) throw new AgentException(AgentFailure.STALE_REFERENCE);
            CommissionDefinition definition = this.access.read(context, player -> {
                this.checkLive(context);
                CommissionDefinition value = this.definition(id, previous);
                if (input.operation().equals("accept")) {
                    if (!this.allowed(context.player(), id)) throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
                    value = this.registries.lookupOrThrow(SystemResourceKeys.COMMISSION).getOptional(id)
                            .orElseThrow(() -> new AgentException(AgentFailure.QUEST_UNAVAILABLE));
                    CatalogAccess.require(player, value);
                }
                return value;
            });
            context.store().update(context.player(), data -> {
                context.check();
                CommissionDataStorage storage = ModuleData.get(data, CommissionSystemModule.STORAGE);
                if (!storage.entry(id).equals(previous)) throw new AgentException(AgentFailure.STALE_REFERENCE);
                if (input.operation().equals("accept")) {
                    if (previous.run().filter(CommissionRun::occupied).isPresent())
                        throw new AgentException(AgentFailure.COMMISSION_STATE);
                    if (!previous.available(definition.limit(), System.currentTimeMillis(), BusinessCalendar.zone()))
                        throw new AgentException(AgentFailure.COMMISSION_LIMIT);
                    if (!definition.conditions().activity().open())
                        throw new AgentException(AgentFailure.ACTIVITY_CLOSED);
                    if (storage.occupied() >= this.capacity(context.player()))
                        throw new AgentException(AgentFailure.COMMISSION_CAPACITY);
                    storage.set(id, new CommissionRun(UUID.randomUUID().toString(), new PersistHolder<>(id, definition), CommissionRun.State.ACCEPTED, System.currentTimeMillis()));
                } else {
                    CommissionRun run = previous.run().filter(value -> value.state() == CommissionRun.State.ACCEPTED)
                            .orElseThrow(() -> new AgentException(AgentFailure.COMMISSION_STATE));
                    storage.set(id, run.state(CommissionRun.State.ABANDONED));
                }
                return null;
            });
        } finally {
            this.active.remove(context.player());
        }
        return this.detail(context, QuestDetailInput.byId(input.questId()), Optional.empty());
    }

    private CompletionStage<Void> deliver(ExecutionContext context, QuestLifecycleInput input, Identifier id) {
        Quote quote = this.quotes.get(context.player());
        if (quote == null || !quote.id().equals(input.quote().orElse("")) || !quote.definition().equals(id)
                || !quote.revision().equals(input.revision()) || quote.expires() <= System.currentTimeMillis())
            throw new AgentException(AgentFailure.STALE_QUOTE);
        if (!this.active.add(context.player())) throw new AgentException(AgentFailure.BUSY);
        Settlement settlement = new Settlement(context, id, quote.run());
        try {
            if (!this.quotes.remove(context.player(), quote)) throw new AgentException(AgentFailure.STALE_QUOTE);
            CommissionRun accepted = context.store().update(context.player(), data -> {
                context.check();
                CommissionDataStorage storage = ModuleData.get(data, CommissionSystemModule.STORAGE);
                CommissionDataStorage.Entry entry = storage.entry(id);
                CommissionRun run = entry.run().orElseThrow(() -> new AgentException(AgentFailure.COMMISSION_STATE));
                if (!entry.revision().equals(quote.revision()) || !run.id().equals(quote.run()) || run.state() != CommissionRun.State.ACCEPTED)
                    throw new AgentException(AgentFailure.STALE_REFERENCE);
                Delivery held = new Delivery("commission:" + run.id(), new PersistHolder<>(id, run.definition().value().reward()));
                held.state = "PAYMENT_PENDING";
                ModuleData.get(data, SpaceSystemModule.STORAGE).putDelivery(held.id, held);
                storage.set(id, run.state(CommissionRun.State.PREPARED));
                return run;
            });
            this.pending.put(context.player(), settlement);
            try {
                this.game.onPlayer(context, player -> {
                    this.checkLive(context);
                    SpaceTransfer transfer = new SpaceTransfer(player);
                    accepted.definition().value().reward().stacks().forEach(stack ->
                            SpaceContentType.of(stack.content()).prepareReward(stack.content(), stack.amount(), transfer));
                    quote.payment().pay(player, settlement.touched);
                    settlement.completedAt = System.currentTimeMillis();
                    return true;
                }).whenComplete((_, error) -> {
                    if (error != null)
                        settlement.error = error instanceof RuntimeException runtime ? runtime : new CompletionException(error);
                    settlement.ready = true;
                    this.settle(settlement);
                });
            } catch (RuntimeException error) {
                settlement.error = error;
                settlement.ready = true;
                this.settle(settlement);
            }
            return settlement.result;
        } catch (RuntimeException error) {
            this.active.remove(context.player());
            throw error;
        }
    }

    private void settle(Settlement settlement) {
        if (this.closed || !settlement.ready || !settlement.saving.compareAndSet(false, true)) return;
        this.engine.maintenance(() -> settlement.store.update(settlement.player, data -> {
            CommissionDataStorage storage = ModuleData.get(data, CommissionSystemModule.STORAGE);
            CommissionRun run = storage.entry(settlement.definition).run().orElseThrow();
            if (!run.id().equals(settlement.run)) throw new AgentException(AgentFailure.STALE_REFERENCE);
            if (settlement.error == null)
                storage.complete(settlement.definition, settlement.run, settlement.completedAt, settlement.zone);
            else {
                SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
                if (settlement.touched.get()) space.setDeliveryState("commission:" + run.id(), "PAYMENT_UNKNOWN");
                else space.removeDelivery("commission:" + run.id());
                storage.set(settlement.definition, run.state(settlement.touched.get() ? CommissionRun.State.UNKNOWN : CommissionRun.State.ACCEPTED));
            }
            return null;
        })).whenComplete((_, error) -> {
            if (error == null || AgentException.failure(error) == AgentFailure.CANCELLED) {
                this.pending.remove(settlement.player, settlement);
                this.active.remove(settlement.player);
                if (error != null) settlement.result.completeExceptionally(error);
                else if (settlement.error == null) settlement.result.complete(null);
                else
                    settlement.result.completeExceptionally(new AgentException(settlement.touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(settlement.error), settlement.error));
            } else {
                SystemMod.LOGGER.warn("Commission settlement failed; retrying storage only: player={}, run={}", settlement.player, settlement.run, error);
                settlement.retryAt = System.currentTimeMillis() + 5000;
                settlement.saving.set(false);
                settlement.result.completeExceptionally(new AgentException(AgentFailure.EFFECT_UNKNOWN, error));
            }
        });
    }

    @Override
    public CompletionStage<QuestClaimOutput> claim(ExecutionContext context, QuestClaimInput input) {
        this.check(context);
        if (input.choice().isPresent()) throw new AgentException(AgentFailure.INVALID_SELECTION);
        String runId = QuestKey.parse(input.claimRef()).localId();
        if (!context.available(Identifier.parse("system:deliver")))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            context.check();
            CommissionDataStorage storage = ModuleData.get(data, CommissionSystemModule.STORAGE);
            String deliveryId = "commission:" + runId;
            SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
            Optional<Map.Entry<Identifier, CommissionDataStorage.Entry>> found = storage.entries().entrySet().stream().filter(entry -> entry.getValue().run().filter(run -> run.id().equals(runId)).isPresent()).findFirst();
            Delivery held = space.getDelivery(deliveryId);
            if (held == null || !held.stored) {
                Map.Entry<Identifier, CommissionDataStorage.Entry> entry = found.orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
                CommissionRun run = entry.getValue().run().orElseThrow();
                if (run.state() != CommissionRun.State.COMPLETED) throw new AgentException(AgentFailure.CLAIM_REJECTED);
                if (held == null)
                    space.putDelivery(deliveryId, new Delivery(deliveryId, new PersistHolder<>(entry.getKey(), run.definition().value().reward())));
                else space.setDeliveryState(deliveryId, "PENDING");
                storage.set(entry.getKey(), run.state(CommissionRun.State.CLAIMED));
            }
            // A claim only transfers the snapshotted reward into space; all content types retain their own withdrawal flow.
            return new QuestClaimOutput("CLAIMED", Optional.empty(), Optional.of(runId), Optional.of(true));
        }));
    }

    @Override
    public Optional<QuestPinOutput> tracking(ServerPlayer player, String localId) {
        Identifier id = Identifier.tryParse(localId);
        if (id == null) return Optional.empty();
        return this.engine.playerStore(player.getUUID()).readLoaded(player.getUUID(), data -> ModuleData.get(data, CommissionSystemModule.STORAGE).entry(id))
                .flatMap(entry -> entry.run().filter(run -> run.state() != CommissionRun.State.ABANDONED).map(run -> new QuestPinOutput(SOURCE.key(localId), run.definition().value().label(),
                        run.definition().value().chapter(), run.completed() ? 100 : 0, run.completed(),
                        this.tasks(run.definition().value(), this.payment(player, id, run.definition().value()), run.completed()), 0, SOURCE)));
    }

    public CompletionStage<List<CommissionRun>> orders(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> ModuleData.get(data, CommissionSystemModule.STORAGE).entries().values().stream()
                .flatMap(entry -> entry.run().stream()).filter(run -> run.state() == CommissionRun.State.UNKNOWN || run.state() == CommissionRun.State.PREPARED).toList()));
    }

    public CompletionStage<Void> resolve(UUID player, String runId, boolean consumed, String reviewer, String reason) {
        if (reason.isBlank() || reason.length() > 512) throw new AgentException(AgentFailure.INVALID_INPUT);
        if (!this.active.add(player)) throw new AgentException(AgentFailure.BUSY);
        AgentStore store = this.engine.playerStore(player);
        ZoneId zone = BusinessCalendar.zone();
        return this.engine.maintenance(() -> store.update(player, data -> {
            CommissionDataStorage storage = ModuleData.get(data, CommissionSystemModule.STORAGE);
            Map.Entry<Identifier, CommissionDataStorage.Entry> entry = storage.entries().entrySet().stream().filter(value -> value.getValue().run().filter(run -> run.id().equals(runId)).isPresent())
                    .findFirst().orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
            CommissionRun run = entry.getValue().run().orElseThrow();
            if (run.state() != CommissionRun.State.UNKNOWN && run.state() != CommissionRun.State.PREPARED)
                throw new AgentException(AgentFailure.COMMISSION_STATE);
            if (consumed) storage.complete(entry.getKey(), runId, System.currentTimeMillis(), zone);
            else {
                ModuleData.get(data, SpaceSystemModule.STORAGE).removeDelivery("commission:" + run.id());
                storage.set(entry.getKey(), run.state(CommissionRun.State.ACCEPTED));
            }
            return (Void) null;
        })).whenComplete((_, _) -> this.active.remove(player)).thenAccept(_ -> SystemMod.LOGGER.warn(
                "Commission reviewed: player={}, run={}, consumed={}, reviewer={}, reason={}", player, runId, consumed, reviewer, reason));
    }

    @Override
    public Session login(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return new Session() {
            @Override
            public void initialize(PlayerData data) {
                Settlement pending = Commissions.this.pending.get(uuid);
                ModuleData.get(data, CommissionSystemModule.STORAGE).recover(pending == null ? Set.of() : Set.of(pending.run));
            }

            @Override
            public void close() {
                Commissions.this.quotes.remove(uuid);
            }
        };
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        this.quotes.values().removeIf(quote -> quote.expires() <= now);
        this.pending.values().stream().filter(settlement -> settlement.retryAt <= now).forEach(this::settle);
    }

    @Override
    public void close() {
        this.closed = true;
        this.quotes.clear();
        this.pending.values().forEach(settlement -> settlement.result.completeExceptionally(new AgentException(AgentFailure.CANCELLED)));
        this.pending.clear();
        this.active.clear();
    }
}
