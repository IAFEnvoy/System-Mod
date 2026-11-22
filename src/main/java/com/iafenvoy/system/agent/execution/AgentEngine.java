package com.iafenvoy.system.agent.execution;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.assistant.chat.AiAccess;
import com.iafenvoy.assistant.chat.ConversationMode;
import com.iafenvoy.assistant.execution.AgentLifecycle;
import com.iafenvoy.assistant.execution.EventExecutor;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.execution.ModelExecutor;
import com.iafenvoy.assistant.execution.TaskExecutor;
import com.iafenvoy.assistant.execution.ToolExecutor;
import com.iafenvoy.assistant.execution.WorkflowExecutor;
import com.iafenvoy.assistant.model.ModelGateway;
import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.module.ModuleRegistry;
import com.iafenvoy.assistant.registry.ModuleScope;
import com.iafenvoy.assistant.routing.HybridRouter;
import com.iafenvoy.assistant.routing.ModelRoute;
import com.iafenvoy.assistant.routing.RoutingDiagnostics;
import com.iafenvoy.assistant.routing.SemanticRouter;
import com.iafenvoy.assistant.storage.AgentJournal;
import com.iafenvoy.assistant.storage.AgentStore;
import com.iafenvoy.assistant.storage.EventTask;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.chat.ChatSupport;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.storage.ConversationMemory;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.config.file.SemanticConfig;
import com.iafenvoy.system.data.History;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Agent execution engine; persistence, inference and workflow waits run off the game thread.
 */
public final class AgentEngine implements AutoCloseable, EventExecutor.Host<PlayerData, ExecutionContext, CardUpdate> {
    private final AgentStore<PlayerData> store;
    private final Function<PlayerData, AgentJournal<CardUpdate>> journal;
    private final ModelGateway model;
    private final ToolExecutor<PlayerData, ExecutionContext, CardUpdate> toolExecutor = new ToolExecutor<>(this::journal);
    private final ToolRegistry<ExecutionContext> tools = new ToolRegistry<>(codec -> codec instanceof SchemaMapCodec<?>);
    private final ModuleRegistry<ExecutionContext, CardUpdate> modules = new ModuleRegistry<>(this.tools, SystemMod.MOD_ID);
    private final WorkflowExecutor<ExecutionContext, CardUpdate> workflows = new WorkflowExecutor<>(this.modules, this::policy);
    private final InteractiveExecutor interactive = new InteractiveExecutor(this);
    private final EventExecutor<PlayerData, ExecutionContext, CardUpdate> events = new EventExecutor<>(this, "agent-event", 2, 64);
    private final TaskExecutor maintenance = new TaskExecutor("agent-store", 2, 32);
    private final ModelExecutor modelExecutor;
    private final HybridRouter<ExecutionContext, CardUpdate> router;
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
    private volatile Function<UUID, AiAccess.Permissions> permissions = player -> AiAccess.DEFAULT.permissions(player, false, () -> AgentConfig.INSTANCE.get().modelCallsPerPlayerDay());
    private BiPredicate<UUID, Identifier> moduleAccess = (_, _) -> true;

    public AgentEngine(AgentStore<PlayerData> store, ModelClient model, Function<PlayerData, AgentJournal<CardUpdate>> journal) {
        this.store = Objects.requireNonNull(store);
        this.journal = Objects.requireNonNull(journal);
        this.model = new ModelGateway(model);
        this.modelExecutor = new ModelExecutor(this.model, AgentConfig.INSTANCE.get().concurrentModels(), SystemMod.MOD_ID);
        this.router = new HybridRouter<>(this.modules, new SemanticRouter(SemanticConfig.INSTANCE::get,
                () -> AgentEngine.class.getResourceAsStream("/assets/system/routing/lexicon.json")),
                (context, text, hints, capabilities, codec) -> {
                    List<ConversationMemory.Entry> history = context.store().read(context.player(),
                            data -> ConversationMemory.recent(data, context.taskId(), 1600));
                    String prompt = context.prompt(SystemPrompts.ROUTING, Map.of("capabilities",
                            CodecIO.write(ModelRoute.Capability.CATALOGUE, capabilities)));
                    return context.model(prompt, ModelRoute.Input.codec(ConversationMemory.Entry.CODEC),
                            new ModelRoute.Input<>(text, history, hints), codec);
                }, error -> switch (AgentException.failure(error)) {
                    case AgentFailure.MODEL_NOT_CONFIGURED, AgentFailure.MODEL_DISABLED, AgentFailure.AI_FORBIDDEN -> true;
                    default -> false;
                });
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

    /**
     * The game bridge supplies host identity; workers never inspect client classes or online player lists.
     */
    public void permissions(Function<UUID, AiAccess.Permissions> permissions) {
        this.permissions = Objects.requireNonNull(permissions);
    }

    public void prompts(SystemPrompts value) {
        this.prompts = Objects.requireNonNull(value);
    }

    public AgentStore<PlayerData> store() {
        return this.store;
    }

    @Override
    public AgentJournal<CardUpdate> journal(PlayerData data) {
        return this.journal.apply(data);
    }

    /**
     * Creates a model-only background task with the same player epoch, quotas and cancellation checks as chat.
     */
    public ExecutionContext background(UUID player, long incarnation, ExecutionPolicy policy) {
        AgentStore<PlayerData> store = this.playerStore(player, incarnation);
        long epoch = store.read(player, data -> {
            if (!data.bound()) throw new AgentException(AgentFailure.NOT_BOUND);
            return data.conversation.memoryEpoch;
        });
        return new ExecutionContext(this, player, UUID.randomUUID().toString(), null,
                System.currentTimeMillis(), epoch, policy, store, incarnation);
    }

    public ModuleRegistry<ExecutionContext, CardUpdate> modules() {
        return this.modules;
    }

    HybridRouter<ExecutionContext, CardUpdate> router() {
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
        this.lifecycle.forEach(listener -> listener.event(player, AgentLifecycle.EventType.DISCONNECTED));
        this.playerLanguages.remove(player);
        this.router.clear(player);
    }

    public long incarnation(UUID player) {
        return this.incarnations.getOrDefault(player, 0L);
    }

    public boolean current(UUID player, long incarnation) {
        return !this.revoking.contains(player) && this.incarnation(player) == incarnation;
    }

    public void lifecycle(@NotNull AgentLifecycle listener) {
        this.lifecycle.add(listener);
    }

    /**
     * Check the lifecycle inside the underlying transaction lock, including late tool callbacks.
     */
    public AgentStore<PlayerData> playerStore(UUID player) {
        return this.playerStore(player, this.incarnation(player));
    }

    @Override
    public AgentStore<PlayerData> playerStore(UUID player, long incarnation) {
        return new AgentStore<>() {
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
            public <T> Optional<T> readLoaded(UUID requested, Function<PlayerData, T> query) {
                return AgentEngine.this.store.readLoaded(requested, data -> {
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
        if (!this.maintenanceOwners.add(player))
            return CompletableFuture.failedFuture(new AgentException(AgentFailure.BUSY));
        // Invalidate old callbacks before deleting business data; token accounting has a separate owner.
        this.revoking.add(player);
        this.incarnations.merge(player, 1L, Long::sum);
        this.cancel(player);
        this.disableRules(player);
        this.router.clear(player);
        return this.maintenance(() -> {
            this.lifecycle.forEach(listener -> listener.event(player, AgentLifecycle.EventType.REVOKED));
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

    public ToolRegistry<ExecutionContext> tools() {
        return this.tools;
    }

    ToolExecutor<PlayerData, ExecutionContext, CardUpdate> toolExecutor() {
        return this.toolExecutor;
    }

    ModelExecutor modelExecutor() {
        return this.modelExecutor;
    }

    @Override
    public boolean closed() {
        return this.closed.get();
    }

    public void registerModule(AgentModule<?, ExecutionContext, CardUpdate> module) {
        this.modules.register(module);
    }

    public void registerModule(Identifier owner, AgentModule<?, ExecutionContext, CardUpdate> module) {
        this.modules.register(owner, module);
    }

    public void registerGroup(ModuleScope.Group scope, AgentModule<?, ExecutionContext, CardUpdate> module) {
        this.modules.registerGroup(scope, module);
    }

    public void moduleAccess(BiPredicate<UUID, Identifier> moduleAccess) {
        this.moduleAccess = Objects.requireNonNull(moduleAccess);
    }

    public boolean available(UUID player, Identifier workflow) {
        AgentModule<?, ExecutionContext, CardUpdate> module = this.module(player, workflow);
        // Recheck exported tool dependencies for routing, in-flight work and queued game-thread operations.
        return module != null && module.descriptor().tools().stream().allMatch(tool -> this.toolAvailable(player, tool));
    }

    public AgentModule<?, ExecutionContext, CardUpdate> module(UUID player, Identifier workflow) {
        return this.modules.get(workflow, owner -> this.moduleAccess.test(player, owner));
    }

    public boolean moduleEnabled(UUID player, Holder<SystemModule> owner) {
        return this.moduleEnabled(player, HolderHelper.id(owner));
    }

    public boolean moduleEnabled(UUID player, Identifier owner) {
        return owner == null || this.moduleAccess.test(player, owner);
    }

    public boolean toolAvailable(UUID player, String tool) {
        return this.tools.available(tool, owner -> this.moduleAccess.test(player, owner));
    }

    public void moduleAccessChanged(UUID player) {
        this.router.clear(player);
        this.lifecycle.forEach(listener -> listener.event(player, AgentLifecycle.EventType.ACCESS_CHANGED));
    }

    public Collection<AgentModule.Descriptor> descriptors() {
        return this.modules.descriptors();
    }

    @Override
    public ExecutionPolicy policy(AgentModule<?, ExecutionContext, CardUpdate> module) {
        return AgentConfig.INSTANCE.get().policies().getOrDefault(module.descriptor().id().toString(), module.descriptor().policy());
    }

    public CompletionStage<AgentResult<CardUpdate>> submit(UUID player, String requestId, AgentAction input, String text) {
        return this.interactive.submit(player, requestId, input, text);
    }

    public CompletionStage<AgentResult<CardUpdate>> forget(UUID player) {
        return this.conversations.forget(player);
    }

    public CompletionStage<AgentResult<CardUpdate>> preferences(UUID player, boolean history, ConversationMode mode) {
        return this.conversations.preferences(player, history, mode);
    }

    public CompletionStage<History> history(UUID player) {
        return this.conversations.history(player);
    }

    // All entry points share module selection, authority checks and partial-write reporting.
    AgentResult<CardUpdate> execute(ExecutionContext context, AgentAction input) {
        return this.execute(context, input, null);
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, AgentAction input, ExecutionPolicy policy) {
        try {
            return this.workflows.execute(context, input, owner -> this.moduleAccess.test(context.player(), owner), policy);
        } catch (Exception error) {
            SystemMod.LOGGER.warn("Agent workflow failed: task={}, workflow={}, source={}, failure={}",
                    context.taskId(), input.id(), context.routeSource(), AgentException.failure(error).name(), error);
            AgentResult<CardUpdate> failure = SystemResults.error(AgentException.failure(error));
            return context.hasCompletedSteps() ? failure.partial() : failure;
        }
    }

    public <T> CompletionStage<T> maintenance(Callable<T> work) {
        if (this.closed()) return CompletableFuture.failedFuture(new AgentException(AgentFailure.CANCELLED));
        return this.maintenance.submit(work);
    }

    public <T> CompletionStage<T> personalMaintenance(UUID player, Callable<T> work) {
        if (!this.maintenanceOwners.add(player))
            return CompletableFuture.failedFuture(new AgentException(AgentFailure.BUSY));
        return this.maintenance(work).whenComplete((ignored, _) -> this.maintenanceOwners.remove(player));
    }

    public void cancel(UUID player) {
        this.interactive.cancel(player);
    }

    @Override
    public void checkBinding(PlayerData data) {
        if (!data.bound()) throw new AgentException(AgentFailure.NOT_BOUND);
    }

    @Override
    public ExecutionContext eventContext(UUID player, String key, EventTask<CardUpdate> task, AgentStore<PlayerData> store, long incarnation) {
        return new ExecutionContext(this, player, task.id, key, task.created, 0, task.policy, store, incarnation);
    }

    @Override
    public AgentResult<CardUpdate> duplicateEvent() {
        return SystemResults.ok("system.result.already");
    }

    @Override
    public AgentResult<CardUpdate> eventFailure(Throwable error) {
        return SystemResults.error(AgentException.failure(error));
    }

    public CompletionStage<AgentResult<CardUpdate>> trigger(UUID player, String key, String ruleId, AgentAction input) {
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
