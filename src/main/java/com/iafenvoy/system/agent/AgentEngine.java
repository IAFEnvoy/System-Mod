package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.CommerceQuotes;
import com.iafenvoy.system.agent.database.AgentDatabases;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.model.ModelGateway;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.routing.HybridRouter;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.agent.store.SystemBindings;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.search.DeepSeekSearchClient;
import com.iafenvoy.system.search.SearchGateway;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Agent execution engine; persistence, inference and workflow waits run off the game thread.
 */
public final class AgentEngine implements AutoCloseable {
    private final AgentStore store;
    private final AgentDatabases databases;
    private final ModelGateway model;
    private final SearchGateway search = new SearchGateway(new DeepSeekSearchClient());
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
    private final CommerceCatalog catalog;
    private final Map<Identifier, CatalogModule<?>> catalogs = new LinkedHashMap<>();
    private final CommerceQuotes commerceQuotes = new CommerceQuotes();
    private final Set<UUID> maintenanceOwners = ConcurrentHashMap.newKeySet();
    // Incarnations invalidate every queued task and storage handle after revoke.
    private final Map<UUID, Long> incarnations = new ConcurrentHashMap<>();
    private final Set<UUID> revoking = ConcurrentHashMap.newKeySet();
    private final List<Consumer<UUID>> revokeListeners = new CopyOnWriteArrayList<>();
    private final ConversationService conversations = new ConversationService(this);
    private final QuestNarration questNarration;
    private BiPredicate<UUID, Identifier> moduleAccess = (_, _) -> true;

    public AgentEngine(CommerceCatalog catalog, AgentStore store, ModelClient model, AgentDatabases databases) {
        this.store = store;
        this.databases = Objects.requireNonNull(databases);
        this.model = new ModelGateway(model);
        this.questNarration = new QuestNarration(this);
        this.onRevoke(this.questNarration::cancel);
        this.onRevoke(this.databases::deletePlayer);
        this.catalog = Objects.requireNonNull(catalog);
        this.modelSlots = new Semaphore(AgentConfig.INSTANCE.get().concurrentModels(), true);
        this.router = new HybridRouter(this);
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

    public AgentDatabases databases() {
        return this.databases;
    }

    public ModuleRegistry modules() {
        return this.modules;
    }

    HybridRouter router() {
        return this.router;
    }

    public void start() {
        for (String id : AgentConfig.INSTANCE.get().policies().keySet())
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
        this.questNarration.cancel(player);
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
            public boolean updateLoaded(UUID requested, Predicate<PlayerData> transaction) {
                return AgentEngine.this.store.updateLoaded(requested, data -> {
                    this.check(requested);
                    return transaction.test(data);
                });
            }

            @Override
            public void flush(UUID requested) {
                this.check(requested);
                // Flush never loads missing data; deletion and flush share the underlying player lock.
                AgentEngine.this.store.flush(requested);
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

    public CompletionStage<AgentResult> grant(UUID player, Identifier system, Consumer<PlayerData> initialize) {
        Objects.requireNonNull(system);
        return this.personalMaintenance(player, () -> this.store.update(player, data -> {
            // Check and assign under the same player transaction, including concurrent administrator requests.
            if (data.bound()) return data.systems.current().filter(system::equals).isPresent()
                    ? AgentResult.ok("system.result.already_bound") : AgentResult.error("SYSTEM_ALREADY_BOUND");
            data.systems = SystemBindings.single(system);
            initialize.accept(data);
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
        this.commerceQuotes.clear(player);
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
                result.isSuccess() ? AgentResult.ok("system.result.reset") : result);
    }

    public ModelGateway model() {
        return this.model;
    }

    public QuestNarration questNarration() {
        return this.questNarration;
    }

    public void onModelUsage(ModelClient.UsageListener listener) {
        this.model.onUsage(listener);
    }

    public SearchGateway search() {
        return this.search;
    }

    public long requestTimeoutMillis(UUID player) {
        long timeout = this.modules.descriptors().stream().filter(AgentModule.Descriptor::interactive).map(AgentModule.Descriptor::id)
                .filter(id -> this.available(player, id)).map(this.modules::get).filter(Objects::nonNull)
                .mapToLong(value -> this.policy(value).ttlMillis()).max().orElse(50000);
        // Include a transport margin so a configured slow workflow is not discarded by the client.
        return Math.max(60000, timeout + 10000);
    }

    public ToolRegistry tools() {
        return this.tools;
    }

    public CommerceCatalog catalog() {
        return this.catalog;
    }

    public void registerCatalog(Identifier owner, CatalogModule<?> module) {
        if (this.catalogs.putIfAbsent(owner, module) != null)
            throw new IllegalArgumentException("Duplicate module catalog " + owner);
    }

    public CommerceCatalog availableCatalog(UUID player) {
        // Resolve enabled definitions on every use, including confirmations issued before module changes.
        return this.catalog.available(this.catalogs.entrySet().stream().filter(entry -> this.moduleEnabled(player, entry.getKey())).map(Map.Entry::getValue).toList());
    }

    public CommerceQuotes commerceQuotes() {
        return this.commerceQuotes;
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

    public void registerModule(Identifier owner, AgentModule<?> module) {
        this.modules.register(owner, module);
    }

    public void moduleAccess(BiPredicate<UUID, Identifier> moduleAccess) {
        this.moduleAccess = Objects.requireNonNull(moduleAccess);
    }

    public boolean available(UUID player, Identifier workflow) {
        AgentModule<?> module = this.module(player, workflow);
        // Recheck exported tool dependencies for routing, in-flight work and queued game-thread operations.
        return module != null && module.descriptor().tools().stream().allMatch(tool -> this.toolAvailable(player, tool));
    }

    public AgentModule<?> module(UUID player, Identifier workflow) {
        return this.modules.get(workflow, owner -> this.moduleAccess.test(player, owner));
    }

    boolean moduleEnabled(UUID player, Identifier owner) {
        return owner == null || this.moduleAccess.test(player, owner);
    }

    public boolean toolAvailable(UUID player, String tool) {
        return this.tools.available(tool, owner -> this.moduleAccess.test(player, owner));
    }

    <I, O> ToolRegistry.Tool<I, O> tool(UUID player, ToolRegistry.Type<I, O> type, Identifier owner) {
        return this.tools.require(type, owner, exporting -> this.moduleAccess.test(player, exporting));
    }

    public void moduleAccessChanged(UUID player) {
        this.router.clear(player);
        this.commerceQuotes.clear(player);
    }

    public Collection<AgentModule.Descriptor> descriptors() {
        return this.modules.descriptors();
    }

    ExecutionPolicy policy(AgentModule<?> module) {
        return AgentConfig.INSTANCE.get().policies().getOrDefault(module.descriptor().id().toString(), module.descriptor().policy());
    }

    public CompletionStage<AgentResult> submit(UUID player, String requestId, AgentAction input, String text) {
        return this.interactive.submit(player, requestId, input, text);
    }

    public CompletionStage<AgentResult> forget(UUID player) {
        return this.conversations.forget(player);
    }

    public CompletionStage<AgentResult> preferences(UUID player, boolean history, boolean external) {
        return this.conversations.preferences(player, history, external);
    }

    public CompletionStage<ResultTypes.History> history(UUID player) {
        return this.conversations.history(player);
    }

    // All entry points share module selection, authority checks and partial-write reporting.
    AgentResult execute(ExecutionContext context, AgentAction input) {
        return this.execute(context, input, null);
    }

    AgentResult execute(ExecutionContext context, AgentAction input, ExecutionPolicy policy) {
        try {
            ModuleRegistry.Call<?> call = this.modules.call(input, owner -> this.moduleAccess.test(context.player(), owner));
            AgentModule.Descriptor descriptor = call.module().descriptor();
            if (!this.available(context.player(), descriptor.id())) throw new AgentException("CAPABILITY_UNAVAILABLE");
            if (context.isEvent() ? !descriptor.event() : !descriptor.interactive())
                throw new AgentException("CAPABILITY_UNAVAILABLE");
            if (!context.isEvent() && context.routeSource().automatic() && !this.modules.modelSelectable(descriptor.id()))
                throw new AgentException("CONFIRM_REQUIRED");
            if ((context.routeSource() == RouteSource.SEMANTIC || context.routeSource() == RouteSource.ROUTE_CACHE)
                    && !this.modules.semanticSelectable(descriptor.id())) throw new AgentException("NOT_AUTHORIZED");
            context.select(call.owner(), descriptor, policy == null ? this.policy(call.module()) : policy);
            context.ensureMemoryCurrent();
            return call.execute(context);
        } catch (Exception error) {
            AgentResult failure = AgentResult.error(errorCode(error));
            return context.hasCompletedSteps() ? failure.partial() : failure;
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

    public CompletionStage<AgentResult> trigger(UUID player, String key, String ruleId, AgentAction input) {
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
        this.questNarration.close();
        this.maintenance.close();
        this.router.close();
        this.playerLanguages.clear();
        this.model.close();
        this.search.close();
        this.store.close();
        this.databases.close();
    }
}
