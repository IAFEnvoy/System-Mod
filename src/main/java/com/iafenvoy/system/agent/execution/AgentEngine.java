package com.iafenvoy.system.agent.execution;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentLifecycle;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.chat.AiAccess;
import com.iafenvoy.system.agent.chat.ChatSupport;
import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.model.ModelGateway;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.module.ModuleRegistry;
import com.iafenvoy.system.agent.routing.HybridRouter;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.agent.storage.AgentJournal;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.History;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.data.store.PlayerData;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Agent execution engine; persistence, inference and workflow waits run off the game thread.
 */
public final class AgentEngine implements AutoCloseable {
    private final AgentStore store;
    private final Function<PlayerData, AgentJournal> journal;
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
    private final Set<UUID> maintenanceOwners = ConcurrentHashMap.newKeySet();
    // Incarnations invalidate every queued task and storage handle after revoke.
    private final Map<UUID, Long> incarnations = new ConcurrentHashMap<>();
    private final Set<UUID> revoking = ConcurrentHashMap.newKeySet();
    private final List<AgentLifecycle> lifecycle = new CopyOnWriteArrayList<>();
    private final ConversationService conversations = new ConversationService(this);
    private final ChatSupport chat = new ChatSupport();
    private volatile Function<UUID, AiAccess.Permissions> permissions = player -> AiAccess.DEFAULT.permissions(player, false);
    private BiPredicate<UUID, Identifier> moduleAccess = (_, _) -> true;

    public AgentEngine(AgentStore store, ModelClient model, Function<PlayerData, AgentJournal> journal) {
        this.store = Objects.requireNonNull(store);
        this.journal = Objects.requireNonNull(journal);
        this.model = new ModelGateway(model);
        this.modelSlots = new Semaphore(AgentConfig.INSTANCE.get().concurrentModels(), true);
        this.router = new HybridRouter(this);
    }

    public SystemPrompts prompts() {
        return this.prompts;
    }

    public ChatSupport chat() {
        return this.chat;
    }

    public AiAccess.Permissions permissions(UUID player) {
        return this.permissions.apply(player).withModelConfigured(this.model.configured());
    }

    /** The game bridge supplies host identity; workers never inspect client classes or online player lists. */
    public void permissions(Function<UUID, AiAccess.Permissions> permissions) {
        this.permissions = Objects.requireNonNull(permissions);
    }

    public void prompts(SystemPrompts value) {
        this.prompts = Objects.requireNonNull(value);
    }

    public AgentStore store() {
        return this.store;
    }

    AgentJournal journal(PlayerData data) {
        return this.journal.apply(data);
    }

    /**
     * Creates a model-only background task with the same player epoch, quotas and cancellation checks as chat.
     */
    public ExecutionContext background(UUID player, long incarnation, ExecutionPolicy policy) {
        AgentStore store = this.playerStore(player, incarnation);
        long epoch = store.read(player, data -> {
            if (!data.bound()) throw new AgentException(AgentFailure.NOT_BOUND);
            return data.conversation.memoryEpoch;
        });
        return new ExecutionContext(this, player, UUID.randomUUID().toString(), null,
                System.currentTimeMillis(), epoch, policy, store, incarnation);
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
        this.lifecycle.forEach(listener -> listener.disconnected(player));
        this.playerLanguages.remove(player);
        this.router.clear(player);
    }

    public long incarnation(UUID player) {
        return this.incarnations.getOrDefault(player, 0L);
    }

    public boolean current(UUID player, long incarnation) {
        return !this.revoking.contains(player) && this.incarnation(player) == incarnation;
    }

    public void lifecycle(AgentLifecycle listener) {
        this.lifecycle.add(Objects.requireNonNull(listener));
    }

    /**
     * Check the lifecycle inside the underlying transaction lock, including late tool callbacks.
     */
    public AgentStore playerStore(UUID player) {
        return this.playerStore(player, this.incarnation(player));
    }

    AgentStore playerStore(UUID player, long incarnation) {
        return new AgentStore() {
            @Override
            public <T> Codec<T> codec(Codec<T> codec) {
                return AgentEngine.this.store.codec(codec);
            }

            private void check(UUID requested) {
                if (!player.equals(requested) || !AgentEngine.this.current(player, incarnation))
                    throw new AgentException(AgentFailure.CANCELLED);
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

    public <T> CompletionStage<T> invalidatePlayer(UUID player, Callable<T> cleanup) {
        if (!this.maintenanceOwners.add(player)) return CompletableFuture.failedFuture(new AgentException(AgentFailure.BUSY));
        // Invalidate old callbacks before deleting business data; token accounting has a separate owner.
        this.revoking.add(player);
        this.incarnations.merge(player, 1L, Long::sum);
        this.cancel(player);
        this.disableRules(player);
        this.router.clear(player);
        return this.maintenance(() -> {
            this.lifecycle.forEach(listener -> listener.revoked(player));
            return cleanup.call();
        }).whenComplete((ignored, error) -> {
            this.revoking.remove(player);
            this.maintenanceOwners.remove(player);
        });
    }

    public ModelGateway model() {
        return this.model;
    }

    public void onModelUsage(ModelClient.UsageListener listener) {
        this.model.onUsage(listener);
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

    public boolean moduleEnabled(UUID player, Identifier owner) {
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
        this.lifecycle.forEach(listener -> listener.accessChanged(player));
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

    public CompletionStage<AgentResult> preferences(UUID player, boolean history, ConversationMode mode) {
        return this.conversations.preferences(player, history, mode);
    }

    public CompletionStage<History> history(UUID player) {
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
            if (!this.available(context.player(), descriptor.id())) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            if (context.isEvent() ? !descriptor.event() : !descriptor.interactive())
                throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            if (!context.isEvent() && context.routeSource().automatic() && !this.modules.modelSelectable(descriptor.id()))
                throw new AgentException(AgentFailure.CONFIRM_REQUIRED);
            if ((context.routeSource() == RouteSource.SEMANTIC || context.routeSource() == RouteSource.ROUTE_CACHE)
                    && !this.modules.semanticSelectable(descriptor.id())) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
            context.select(call.owner(), descriptor, policy == null ? this.policy(call.module()) : policy);
            context.ensureMemoryCurrent();
            return call.execute(context);
        } catch (Exception error) {
            AgentResult failure = AgentResult.error(AgentException.failure(error));
            return context.hasCompletedSteps() ? failure.partial() : failure;
        }
    }

    public <T> CompletionStage<T> maintenance(Callable<T> work) {
        if (this.closed()) return CompletableFuture.failedFuture(new AgentException(AgentFailure.CANCELLED));
        return this.maintenance.submit(work);
    }

    public <T> CompletionStage<T> personalMaintenance(UUID player, Callable<T> work) {
        if (!this.maintenanceOwners.add(player)) return CompletableFuture.failedFuture(new AgentException(AgentFailure.BUSY));
        return this.maintenance(work).whenComplete((ignored, _) -> this.maintenanceOwners.remove(player));
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
        this.lifecycle.clear();
    }
}
