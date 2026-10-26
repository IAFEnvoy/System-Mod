package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.ShopQuotes;
import com.iafenvoy.system.agent.commerce.SystemCoinPayment;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.model.ModelGateway;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.routing.HybridRouter;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.config.file.SemanticConfig;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Agent execution engine; persistence, inference and workflow waits run off the game thread.
 */
public final class AgentEngine implements AutoCloseable {
    private final AgentConfig settings;
    private final AgentStore store;
    private final ModelGateway model;
    private final ToolRegistry tools = new ToolRegistry();
    private final ModuleRegistry modules = new ModuleRegistry(this.tools);
    private final InteractiveExecutor interactive = new InteractiveExecutor(this);
    private final EventExecutor events = new EventExecutor(this);
    private final TaskExecutor maintenance = new TaskExecutor("agent-store", 2, 32);
    private final Semaphore modelSlots;
    private final HybridRouter router;
    private volatile SystemPrompts prompts = SystemPrompts.empty();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile CommerceCatalog catalog;
    private volatile SystemCoinPayment coinPayment = SystemCoinPayment.DISABLED;
    private final ShopQuotes shopQuotes = new ShopQuotes();
    private final Set<UUID> maintenanceOwners = ConcurrentHashMap.newKeySet();
    // Incarnations invalidate every queued task and storage handle after revoke.
    private final Map<UUID, Long> incarnations = new ConcurrentHashMap<>();
    private final Set<UUID> revoking = ConcurrentHashMap.newKeySet();
    private final List<Consumer<UUID>> revokeListeners = new CopyOnWriteArrayList<>();
    private final ConversationService conversations = new ConversationService(this);

    public AgentEngine(AgentConfig settings, SemanticConfig semantic, CommerceCatalog catalog, AgentStore store, ModelClient model) {
        this.settings = settings;
        this.store = store;
        this.model = new ModelGateway(model);
        this.catalog = Objects.requireNonNull(catalog);
        this.modelSlots = new Semaphore(settings.concurrentModels(), true);
        this.router = new HybridRouter(this, semantic);
    }

    public AgentConfig settings() {
        return this.settings;
    }

    public SystemPrompts prompts() {
        return this.prompts;
    }

    public void prompts(SystemPrompts value) {
        this.prompts = Objects.requireNonNull(value);
    }

    public AgentStore store() {
        return this.store;
    }

    public ModuleRegistry modules() {
        return this.modules;
    }

    HybridRouter router() {
        return this.router;
    }

    public void start() {
        for (String id : this.settings.policies().keySet())
            if (this.modules.get(Identifier.tryParse(id)) == null)
                throw new IllegalArgumentException("Unknown policy module " + id);
        this.router.start();
    }

    public RoutingDiagnostics routingDiagnostics() {
        return this.router.diagnostics();
    }

    public String language(UUID player) {
        return this.playerLanguages.getOrDefault(player, "zh_cn");
    }

    public void language(UUID player, String language) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT).replace('-', '_');
        this.playerLanguages.put(player, normalized.matches("[a-z]{2,3}_[a-z0-9]{2,8}") ? normalized : "zh_cn");
    }

    public void disconnected(UUID player) {
        this.playerLanguages.remove(player);
        this.router.clear(player);
    }

    public long incarnation(UUID player) {
        return this.incarnations.getOrDefault(player, 0L);
    }

    public boolean current(UUID player, long incarnation) {
        return !this.revoking.contains(player) && this.incarnation(player) == incarnation;
    }

    public void onRevoke(Consumer<UUID> listener) {
        this.revokeListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Check the lifecycle inside the underlying transaction lock, including late tool callbacks.
     */
    public AgentStore playerStore(UUID player) {
        return this.playerStore(player, this.incarnation(player));
    }

    AgentStore playerStore(UUID player, long incarnation) {
        return new AgentStore() {
            private void check(UUID requested) {
                if (!player.equals(requested) || !AgentEngine.this.current(player, incarnation))
                    throw new AgentException("CANCELLED");
            }

            @Override
            public <T> T read(UUID requested, Function<PlayerData, T> query) {
                return AgentEngine.this.store.read(requested, data -> {
                    this.check(requested);
                    return query.apply(data);
                });
            }

            @Override
            public <T> T update(UUID requested, Function<PlayerData, T> transaction) {
                return AgentEngine.this.store.update(requested, data -> {
                    this.check(requested);
                    return transaction.apply(data);
                });
            }

            @Override
            public void delete(UUID requested) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<UUID> players() {
                return List.of(player);
            }

            @Override
            public void close() { /* The owning engine controls the shared store. */ }
        };
    }

    public CompletionStage<AgentResult> grant(UUID player, Map<String, Set<String>> baselines) {
        return this.personalMaintenance(player, () -> this.store.update(player, data -> {
            if (data.bound) return AgentResult.ok("system.result.already_bound");
            data.bound = true;
            data.baselines.putAll(baselines);
            return AgentResult.ok("system.result.bound");
        }));
    }

    public CompletionStage<AgentResult> revoke(UUID player) {
        if (!this.maintenanceOwners.add(player)) return CompletableFuture.completedFuture(AgentResult.error("BUSY"));
        // Invalidate old callbacks before deleting business data; token accounting has a separate owner.
        this.revoking.add(player);
        this.incarnations.merge(player, 1L, Long::sum);
        this.cancel(player);
        this.disableRules(player);
        this.shopQuotes.clear(player);
        this.router.clear(player);
        return this.maintenance(() -> {
            this.revokeListeners.forEach(listener -> listener.accept(player));
            this.store.delete(player);
            return AgentResult.ok("system.result.revoked");
        }).whenComplete((ignored, error) -> {
            this.revoking.remove(player);
            this.maintenanceOwners.remove(player);
        });
    }

    /**
     * Deletes the current player file without attempting to decode it, then reports a reset result.
     * This is intentionally separate from migration: malformed files are discarded explicitly by an administrator.
     */
    public CompletionStage<AgentResult> reset(UUID player) {
        return this.revoke(player).thenApply(result ->
                "SUCCEEDED".equals(result.status()) ? AgentResult.ok("system.result.reset") : result);
    }

    public ModelGateway model() {
        return this.model;
    }

    public void onModelUsage(ModelClient.UsageListener listener) {
        this.model.onUsage(listener);
    }

    public ToolRegistry tools() {
        return this.tools;
    }

    public CommerceCatalog catalog() {
        return this.catalog;
    }

    public void catalog(CommerceCatalog value) {
        this.catalog = Objects.requireNonNull(value);
    }

    public ShopQuotes shopQuotes() {
        return this.shopQuotes;
    }

    public SystemCoinPayment coinPayment() {
        return this.coinPayment;
    }

    public void coinPayment(SystemCoinPayment value) {
        this.coinPayment = Objects.requireNonNull(value);
    }

    Semaphore modelSlots() {
        return this.modelSlots;
    }

    boolean closed() {
        return this.closed.get();
    }

    public void registerModule(AgentModule<?> module) {
        this.modules.register(module);
    }

    public Collection<AgentModule.Descriptor> descriptors() {
        return this.modules.descriptors();
    }

    ExecutionPolicy policy(AgentModule<?> module) {
        return this.settings.policies().getOrDefault(module.descriptor().id().toString(), module.descriptor().policy());
    }

    public CompletionStage<AgentResult> submit(UUID player, String requestId, TypedPayload<?> input, String text) {
        return this.interactive.submit(player, requestId, input, text);
    }

    public CompletionStage<AgentResult> forget(UUID player) {
        return this.conversations.forget(player);
    }

    public CompletionStage<AgentResult> preferences(UUID player, boolean history, boolean external) {
        return this.conversations.preferences(player, history, external);
    }

    public CompletionStage<AgentResult> history(UUID player) {
        return this.conversations.history(player);
    }

    // All entry points share module selection, authority checks and partial-write reporting.
    AgentResult execute(ExecutionContext context, TypedPayload<?> input) {
        return this.execute(context, input, null);
    }

    AgentResult execute(ExecutionContext context, TypedPayload<?> input, ExecutionPolicy policy) {
        try {
            ModuleRegistry.Call<?> call = this.modules.call(input);
            AgentModule.Descriptor descriptor = call.module().descriptor();
            if (context.isEvent() ? !descriptor.event() : !descriptor.interactive())
                throw new AgentException("CAPABILITY_UNAVAILABLE");
            if (!context.isEvent() && context.routeSource() != RouteSource.STRUCTURED && !this.modules.modelSelectable(descriptor.id()))
                throw new AgentException("CONFIRM_REQUIRED");
            if ((context.routeSource() == RouteSource.SEMANTIC || context.routeSource() == RouteSource.ROUTE_CACHE)
                    && !this.modules.readOnly(descriptor.id())) throw new AgentException("NOT_AUTHORIZED");
            context.select(descriptor, policy == null ? this.policy(call.module()) : policy);
            context.ensureMemoryCurrent();
            return call.execute(context);
        } catch (Exception error) {
            AgentResult failure = AgentResult.error(errorCode(error));
            return context.hasCompletedSteps() ? new AgentResult("PARTIALLY_SUCCEEDED", failure.messageKey(), "", List.of()) : failure;
        }
    }

    public <T> CompletionStage<T> maintenance(Callable<T> work) {
        if (this.closed()) return CompletableFuture.failedFuture(new AgentException("CANCELLED"));
        return this.maintenance.submit(work);
    }

    CompletionStage<AgentResult> personalMaintenance(UUID player, Callable<AgentResult> work) {
        if (!this.maintenanceOwners.add(player)) return CompletableFuture.completedFuture(AgentResult.error("BUSY"));
        return this.maintenance(work).whenComplete((ignored, error) -> this.maintenanceOwners.remove(player));
    }

    public void cancel(UUID player) {
        this.interactive.cancel(player);
    }

    public CompletionStage<AgentResult> trigger(UUID player, String key, String ruleId, TypedPayload<?> input) {
        return this.events.trigger(player, key, ruleId, input);
    }

    public void resume(UUID player) {
        this.events.resume(player);
    }

    public void enableRules(UUID player, Set<String> rules) {
        this.events.enableRules(player, rules);
    }

    public void disableRules(UUID player) {
        this.events.disableRules(player);
    }

    public static String errorCode(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause())
            if (current instanceof AgentException known) return known.code();
        return "MODULE_FAILED";
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.interactive.close();
        this.events.close();
        this.maintenance.close();
        this.router.close();
        this.playerLanguages.clear();
        this.model.close();
        this.store.close();
    }
}
