package com.iafenvoy.system.agent.execution;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.assistant.chat.AiAccess;
import com.iafenvoy.assistant.chat.ConversationMode;
import com.iafenvoy.assistant.execution.*;
import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.react.ReActContext;
import com.iafenvoy.assistant.registry.ModuleScope;
import com.iafenvoy.assistant.routing.HybridRouter;
import com.iafenvoy.assistant.routing.RouteSource;
import com.iafenvoy.assistant.storage.AgentStore;
import com.iafenvoy.assistant.storage.EventTask;
import com.iafenvoy.assistant.tool.ToolContext;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.assistant.util.CodecIO;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.FreeChatInput;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.data.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Per-task authority and budget shared by routing, model calls and tool execution.
 */
public final class ExecutionContext implements ReActContext.Execution, WorkflowExecutor.Context, ToolExecutor.Context, HybridRouter.Context, ToolContext.Execution<ExecutionContext> {
    private final AgentEngine engine;
    private final AgentStore<PlayerData> store;
    private final long incarnation;
    private final UUID player;
    private final String taskId;
    private final String eventKey;
    private final long started;
    private final long memoryEpoch;
    private final String language;
    private final SystemPrompts prompts;
    private RouteSource routeSource;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Set<String> completedLocalSteps = new HashSet<>();
    private AgentModule.Descriptor module;
    private ModuleScope scope = ModuleScope.Global.INSTANCE;
    private ModuleScope eventScope = ModuleScope.Global.INSTANCE;
    private ExecutionPolicy policy;
    private long expires;
    private final ExecutionBudget budget = new ExecutionBudget();
    private int toolContexts;
    private int actions;
    private String stepPrefix = "";
    private final Set<String> authorizedExternalTools = new HashSet<>();
    private final Set<ToolRegistry.Type<?, ?>> chatReadTools = Collections.newSetFromMap(new IdentityHashMap<>());

    ExecutionContext(AgentEngine engine, UUID player, String taskId, String eventKey,
                     long started, long epoch, ExecutionPolicy initial, AgentStore<PlayerData> store, long incarnation) {
        this.engine = engine;
        this.player = player;
        this.taskId = taskId;
        this.eventKey = eventKey;
        this.incarnation = incarnation;
        this.store = store;
        this.language = engine.language(player);
        this.prompts = engine.prompts();
        this.routeSource = eventKey == null ? RouteSource.STRUCTURED : RouteSource.EVENT;
        this.started = started;
        this.memoryEpoch = epoch;
        this.policy = initial;
        this.expires = started + initial.ttlMillis();
        if (eventKey != null) store.read(player, data -> {
            EventTask<CardUpdate> task = this.engine.journal(data).getTask(eventKey);
            this.eventScope = task.scope;
            this.budget.restore(new ExecutionBudget.Usage(task.modelCalls, task.toolCalls, task.workflowSteps,
                    task.writeSteps, task.tokens));
            this.expires = task.expires;
            return null;
        });
    }

    @Override
    public void select(ModuleScope scope, AgentModule.Descriptor descriptor, ExecutionPolicy value) {
        if (this.isEvent() && !this.eventScope.equals(scope))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        this.scope = scope;
        this.module = descriptor;
        this.policy = value;
        this.authorizedExternalTools.clear();
        // Routing time remains charged, but selecting a slow module may use its own deadline.
        this.expires = this.started + value.ttlMillis();
        ExecutionBudget.Usage used = this.budget.usage();
        if (used.models() > value.modelCalls() || used.tokens() > value.totalTokens())
            throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
    }

    @Override
    public UUID player() {
        return this.player;
    }

    @Override
    public String taskId() {
        return this.taskId;
    }

    @Override
    public boolean isEvent() {
        return this.eventKey != null;
    }

    public boolean routed() {
        return this.routeSource.automatic();
    }

    @Override
    public RouteSource routeSource() {
        return this.routeSource;
    }

    /**
     * Destructive confirmations require the selected player action, never a nested model tool or event.
     */
    public void requirePlayerAction(AgentAction action) {
        this.check();
        if (this.isEvent() || this.routeSource != RouteSource.STRUCTURED || this.module == null || !this.module.id().equals(action.id()))
            throw new AgentException(AgentFailure.CONFIRM_REQUIRED);
    }

    /**
     * Explicit player text is also sufficient for reversible inventory deposits, unlike destructive confirmations.
     */
    public void requirePlayerRequest(AgentAction action) {
        this.check();
        if (this.isEvent() || this.routeSource != RouteSource.STRUCTURED && this.routeSource != RouteSource.COMMAND
                || this.module == null || !this.module.id().equals(action.id()))
            throw new AgentException(AgentFailure.CONFIRM_REQUIRED);
    }

    void routeSource(RouteSource source) {
        this.routeSource = source;
    }

    @Override
    public String language() {
        return this.language;
    }

    public String prompt(String id) {
        return this.prompt(id, Map.of());
    }

    public String prompt(String id, Map<String, String> variables) {
        Map<String, String> values = new HashMap<>(variables);
        // Language comes from this execution's player metadata, never a caller-supplied override.
        values.put("player_language", this.language);
        return this.prompts.render(id, values);
    }

    @Override
    public String promptRevision() {
        return this.prompts.revision();
    }

    /**
     * Optional contributions use this execution's snapshot, even if resources reload while it runs.
     */
    public Optional<String> findPrompt(Identifier id) {
        return this.prompts.find(id.toString(), Map.of("player_language", this.language));
    }

    @Override
    public long incarnation() {
        return this.incarnation;
    }

    @Override
    public long memoryEpoch() {
        return this.memoryEpoch;
    }

    public AgentStore<PlayerData> store() {
        return this.store;
    }

    public List<String> commandUsages() {
        return this.engine.modules().commands().usages(this::available);
    }

    public List<AgentResult.Action> shortcuts() {
        return this.engine.modules().shortcuts(this::available);
    }

    @Override
    public boolean available(Identifier workflow) {
        return this.engine.available(this.player, workflow);
    }

    /**
     * Optional dependencies remain owned by their exporting modules and never disable local question answering.
     */
    public boolean toolAvailable(ToolRegistry.Type<?, ?> type) {
        return this.module != null && (this.module.tools().contains(type.id()) || this.module.optionalTools().contains(type.id()))
                && this.engine.toolAvailable(this.player, type.id());
    }

    @Override
    public AgentAction chatAction(String text) {
        return new FreeChatInput(text);
    }

    @Override
    public boolean externalServicesAllowed() {
        return this.mode().usesAi();
    }

    @Override
    public ConversationMode mode() {
        this.ensureMemoryCurrent();
        return this.permissions().effective(this.store.read(this.player, data -> data.mode));
    }

    public AiAccess.Permissions permissions() {
        return this.engine.permissions(this.player);
    }

    @Override
    public boolean freeChatAllowed() {
        return this.mode().chats();
    }

    public void requireFreeChat() {
        this.requireExternalServices();
        if (!this.mode().chats())
            throw new AgentException(AgentFailure.FREE_CHAT_DISABLED);
    }

    public List<AgentModule<?, ExecutionContext, CardUpdate>> chatActions() {
        this.requireFreeChat();
        if (this.isEvent()) return List.of();
        return this.engine.modules().descriptors().stream()
                .filter(descriptor -> this.engine.modules().modelSelectable(descriptor.id()) && this.available(descriptor.id()))
                .<AgentModule<?, ExecutionContext, CardUpdate>>map(descriptor -> this.engine.module(this.player, descriptor.id())).filter(Objects::nonNull).toList();
    }

    /**
     * AI-selected workflows share the same task and counters, with a narrower policy and isolated step IDs.
     * Execute inline on the agent worker: resubmitting would wait on this player's own interactive lock.
     */
    public AgentResult<CardUpdate> action(AgentAction input) {
        this.requireFreeChat();
        if (this.isEvent() || !this.engine.modules().modelSelectable(input.id()))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        AgentModule<?, ExecutionContext, CardUpdate> target = this.engine.module(this.player, input.id());
        if (target == null || !this.available(input.id()))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (!this.budget.step(this.policy.steps())) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
        AgentModule.Descriptor previousModule = this.module;
        ModuleScope previousScope = this.scope;
        ExecutionPolicy previousPolicy = this.policy;
        RouteSource previousSource = this.routeSource;
        long previousExpires = this.expires;
        String previousPrefix = this.stepPrefix;
        Set<String> previousAuthorized = Set.copyOf(this.authorizedExternalTools);
        ExecutionPolicy child = this.engine.policy(target);
        ExecutionBudget.Usage used = this.budget.usage();
        ExecutionPolicy limited = new ExecutionPolicy(
                Math.min(previousPolicy.modelCalls(), used.models() + child.modelCalls()),
                Math.min(previousPolicy.toolCalls(), used.tools() + child.toolCalls()),
                Math.min(previousPolicy.steps(), used.steps() + child.steps()),
                Math.min(previousPolicy.writeSteps(), used.writes() + child.writeSteps()),
                Math.min(previousPolicy.inputBytes(), child.inputBytes()),
                Math.min(previousPolicy.outputTokens(), child.outputTokens()),
                Math.min(previousPolicy.totalTokens(), used.tokens() + child.totalTokens()),
                Math.min(previousExpires, System.currentTimeMillis() + child.ttlMillis()) - this.started,
                Math.min(previousPolicy.modelTimeoutMillis(), child.modelTimeoutMillis()),
                Math.min(previousPolicy.reactIterations(), child.reactIterations()));
        AgentResult<CardUpdate> result;
        try {
            this.routeSource = RouteSource.LLM;
            this.stepPrefix = previousPrefix + "action/" + this.actions++ + "/";
            result = this.engine.execute(this, input, limited);
        } finally {
            this.module = previousModule;
            this.scope = previousScope;
            this.policy = previousPolicy;
            this.routeSource = previousSource;
            this.expires = previousExpires;
            this.stepPrefix = previousPrefix;
            this.authorizedExternalTools.clear();
            this.authorizedExternalTools.addAll(previousAuthorized);
        }
        this.requireFreeChat();
        return result;
    }

    /**
     * Trusted workflows authorize optional paid tools only after their own business preconditions succeed.
     */
    public void authorizeExternalTools(ToolRegistry.Type<?, ?>... types) {
        if (this.isEvent()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        this.requireExternalServices();
        for (ToolRegistry.Type<?, ?> type : types)
            if (!this.toolAvailable(type)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        for (ToolRegistry.Type<?, ?> type : types) this.authorizedExternalTools.add(type.id());
    }

    public void requireExternalTools(ToolRegistry.Type<?, ?>... types) {
        this.requireExternalServices();
        if (this.isEvent()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        for (ToolRegistry.Type<?, ?> type : types) {
            if (!this.authorizedExternalTools.contains(type.id()))
                throw new AgentException(AgentFailure.NOT_AUTHORIZED);
            if (!this.toolAvailable(type)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        }
    }

    @Override
    public long remainingMillis() {
        return Math.max(0, this.expires - System.currentTimeMillis());
    }

    @Override
    public long modelTimeoutMillis() {
        return Math.min(this.policy.modelTimeoutMillis(), this.remainingMillis());
    }

    /**
     * Billable network tools obey the same external-service opt-out as inference, before each call.
     */
    @Override
    public void requireExternalServices() {
        this.ensureMemoryCurrent();
        this.permissions().requireExternalServices(() -> this.store.read(this.player, data -> data.mode));
    }

    @Override
    public int remainingModelCalls() {
        return Math.max(0, this.policy.modelCalls() - this.budget.usage().models());
    }

    @Override
    public int remainingToolCalls() {
        return Math.max(0, this.policy.toolCalls() - this.budget.usage().tools());
    }

    @Override
    public int remainingSteps() {
        return Math.max(0, this.policy.steps() - this.budget.usage().steps());
    }

    @Override
    public int reactIterations() {
        return this.policy.reactIterations();
    }

    /**
     * General reasoning gets only registry-advertised reads; paid external tools are never advertised here.
     */
    public ToolContext<ExecutionContext> reasoningTools() {
        this.ensureMemoryCurrent();
        if (this.isEvent()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return new ToolContext<>(this, this.engine.tools().reasoning(owner -> this.engine.moduleEnabled(this.player, owner)),
                "react/" + this.toolContexts++);
    }

    /**
     * Free chat owns an explicit read scope; a provider cannot export a write or a disabled feature.
     */
    public ToolContext<ExecutionContext> chatTools(Collection<ToolRegistry.Type<?, ?>> additions) {
        this.requireFreeChat();
        if (this.isEvent()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        Map<String, ToolRegistry.ScopedTool<?, ?, ExecutionContext>> allowed = this.engine.tools()
                .chat(owner -> this.engine.moduleEnabled(this.player, owner), additions);
        allowed.values().forEach(tool -> this.chatReadTools.add(tool.tool().type()));
        return new ToolContext<>(this, allowed, "chat/" + this.toolContexts++);
    }

    public ToolContext<ExecutionContext> tools(ToolRegistry.Type<?, ?>... types) {
        Map<String, ToolRegistry.ScopedTool<?, ?, ExecutionContext>> allowed = new LinkedHashMap<>();
        for (ToolRegistry.Type<?, ?> type : types) this.addTool(allowed, type);
        return new ToolContext<>(this, allowed, "react/" + this.toolContexts++);
    }

    private <I, O> void addTool(Map<String, ToolRegistry.ScopedTool<?, ?, ExecutionContext>> allowed, ToolRegistry.Type<I, O> type) {
        this.ensureMemoryCurrent();
        if (!this.toolAvailable(type)) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        ToolRegistry.ScopedTool<I, O, ExecutionContext> scoped = this.engine.tools().scope(type, this.scope, owner -> this.engine.moduleEnabled(this.player, owner));
        if (scoped.tool().write() && this.module.optionalTools().contains(type.id()))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        allowed.put(type.id(), scoped);
    }

    @Override
    public void checkToolOwner(ToolRegistry.ScopedTool<?, ?, ExecutionContext> scoped) {
        this.ensureMemoryCurrent();
        if (!scoped.scope().available(owner -> this.engine.moduleEnabled(this.player, owner)) || !this.engine.toolAvailable(this.player, scoped.tool().id()))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    @Override
    public <I, O> O scopedTool(ToolRegistry.ScopedTool<I, O, ExecutionContext> scoped, String stepId, I input) throws Exception {
        this.checkToolOwner(scoped);
        // A scope is a subset of this workflow or an explicitly advertised read, never an alternate write route.
        if (!this.toolAvailable(scoped.tool().type()) && (scoped.tool().write()
                || scoped.tool().reasoningDescription().isBlank() && !this.chatReadTools.contains(scoped.tool().type())))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        O result = this.executeTool(scoped.tool(), stepId, input);
        this.checkToolOwner(scoped);
        return result;
    }

    @Override
    public void cancel() {
        this.cancelled.set(true);
    }

    public boolean active() {
        return !this.cancelled.get() && !this.engine.closed() && this.engine.current(this.player, this.incarnation) && this.remainingMillis() > 0;
    }

    public void check() {
        if (this.cancelled.get() || this.engine.closed() || !this.engine.current(this.player, this.incarnation))
            throw new AgentException(AgentFailure.CANCELLED);
        if (this.remainingMillis() == 0) throw new AgentException(AgentFailure.EXPIRED);
        if (this.module != null && (!this.scope.available(owner -> this.engine.moduleEnabled(this.player, owner)) || !this.available(this.module.id())))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    private void persist() {
        if (!this.isEvent()) return;
        ExecutionBudget.Usage usage = this.budget.usage();
        this.store().update(this.player, data -> {
            this.engine.journal(data).updateTaskUsage(this.eventKey,
                    usage.models(), usage.tools(), usage.steps(), usage.writes(), usage.tokens());
            return null;
        });
    }

    @Override
    public <T> Codec<T> codec(Codec<T> codec) {
        return this.store().codec(codec);
    }

    public <I, O> O model(String instruction, MapCodec<I> inputCodec, I input, Codec<O> outputCodec) throws Exception {
        return this.model(instruction, inputCodec, input, List.of(), reply -> {
            Codec<O> codec = this.store().codec(outputCodec);
            return CodecIO.parse(codec, reply.value());
        });
    }

    /**
     * Preview the same serialized request and remaining budget used by model(), without spending a call.
     */
    public <I> boolean fitsModelInput(String instruction, MapCodec<I> inputCodec, I input) {
        String content = this.modelContent(inputCodec, input);
        long bytes = ModelExecutor.inputBytes(instruction, content, List.of());
        return this.budget.fitsModelInput(bytes, this.policy.inputBytes(), this.policy.outputTokens(), this.policy.totalTokens());
    }

    private <I> String modelContent(MapCodec<I> inputCodec, I input) {
        Codec<ModelExecutor.Input<I>> codec = this.store().codec(ModelExecutor.Input.codec(inputCodec));
        return CodecIO.encode(codec, new Input<I>(input, new ModelExecutor.RequestContext(this.language)));
    }

    @Override
    public <I, O> O model(String instruction, MapCodec<I> inputCodec, I input, List<ModelClient.Function> functions,
                          Function<ModelClient.Reply, O> decoder) throws Exception {
        return this.model(instruction, inputCodec, input, functions, ModelClient.Options.DEFAULT, decoder);
    }

    public boolean supportsWebSearch() {
        return this.engine.model().supportsWebSearch();
    }

    private void checkModelOptions(ModelClient.Options options) {
        if (!options.webSearch()) return;
        if (this.isEvent() || this.authorizedExternalTools.isEmpty())
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        for (String id : this.authorizedExternalTools)
            if (!this.engine.toolAvailable(this.player, id))
                throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    public <I, O> O model(String instruction, MapCodec<I> inputCodec, I input, List<ModelClient.Function> functions,
                          ModelClient.Options options, Function<ModelClient.Reply, O> decoder) throws Exception {
        return this.engine.modelExecutor().execute(this.player, instruction, () -> this.modelContent(inputCodec, input),
                functions, options, this.budget, this.policy, this::modelTimeoutMillis, () -> {
                    this.requireExternalServices();
                    this.checkModelOptions(options);
                }, () -> {
                    this.check();
                    this.checkModelOptions(options);
                    this.reserveModelQuota();
                }, this::persist, decoder, (reply, error) ->
                        SystemMod.LOGGER.warn("Model decision failed validation: task={}, workflow={}, source={}, modelCall={}, response={}",
                                this.taskId, this.module == null ? "routing" : this.module.id(), this.routeSource, this.modelCalls(), reply, error));
    }

    private void reserveModelQuota() {
        this.store().update(this.player, data -> {
            AiAccess.Permissions permissions = this.permissions();
            permissions.requireModelAccess();
            if (!data.mode.usesAi() || (!this.isEvent() && data.conversation.memoryEpoch != this.memoryEpoch))
                throw new AgentException(AgentFailure.MODEL_DISABLED);
            String day = LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString();
            data.modelQuota.reserve(day, permissions.modelCallsPerDay());
            return null;
        });
    }

    public <I, O> O tool(ToolRegistry.Type<I, O> type, String stepId, I input) throws Exception {
        this.check();
        this.ensureMemoryCurrent();
        if (!stepId.matches("[a-zA-Z0-9_./-]+")) throw new AgentException(AgentFailure.INVALID_INPUT);
        String id = type.id();
        if (this.module == null || !this.module.tools().contains(id) && !this.module.optionalTools().contains(id))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        ToolRegistry.ScopedTool<I, O, ExecutionContext> scoped = this.engine.tools().scope(type, this.scope, owner -> this.engine.moduleEnabled(this.player, owner));
        ToolRegistry.Tool<I, O, ExecutionContext> tool = scoped.tool();
        if (this.module.optionalTools().contains(id) && tool.write())
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return this.scopedTool(scoped, stepId, input);
    }

    /**
     * Enter a real merge member's tool scope while retaining this request's limits and step journal.
     */
    public <I, O> O memberTool(Identifier member, ToolRegistry.Type<I, O> type, String stepId, I input) throws Exception {
        this.check();
        if (!stepId.matches("[a-zA-Z0-9_./-]+")) throw new AgentException(AgentFailure.INVALID_INPUT);
        if (!(this.scope instanceof ModuleScope.Group group) || !group.members().contains(member) || !this.toolAvailable(type))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        ToolRegistry.ScopedTool<I, O, ExecutionContext> selected = this.engine.tools().member(type, member, owner -> this.engine.moduleEnabled(this.player, owner));
        if (selected.tool().write() && this.module.optionalTools().contains(type.id()))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        ModuleScope previous = this.scope;
        String prefix = this.stepPrefix;
        try {
            this.scope = selected.scope();
            this.stepPrefix += "member/" + member.getNamespace() + "/" + member.getPath() + "/";
            return this.scopedTool(selected, stepId, input);
        } finally {
            this.scope = previous;
            this.stepPrefix = prefix;
        }
    }

    private <I, O> O executeTool(ToolRegistry.Tool<I, O, ExecutionContext> tool, String stepId, I input) throws Exception {
        return this.engine.toolExecutor().execute(this, tool, stepId, this.taskId + ":" + this.stepPrefix + stepId,
                input, this.store, this.budget, this.policy, this::persist);
    }

    @Override
    public boolean registeredToolAvailable(String id) {
        return this.engine.toolAvailable(this.player, id);
    }

    @Override
    public void completedWrite(String stepId) {
        this.completedLocalSteps.add(stepId);
    }

    @Override
    public boolean knownToolFailure(Throwable error) {
        return error instanceof AgentException;
    }

    @Override
    public <T> T withHistory(Supplier<T> work) {
        return this.store.read(this.player, data -> {
            this.ensureMemoryCurrent();
            return data.saveHistory ? work.get() : null;
        });
    }

    @Override
    public void ensureMemoryCurrent() {
        this.check();
        if (!this.isEvent() && this.store().read(this.player, data -> data.conversation.memoryEpoch) != this.memoryEpoch)
            throw new AgentException(AgentFailure.CANCELLED);
    }

    @Override
    public int modelCalls() {
        return this.budget.usage().models();
    }

    public boolean hasCompletedSteps() {
        if (!this.completedLocalSteps.isEmpty()) return true;
        // A resumed event may fail before reaching a write already completed in an earlier run.
        return this.isEvent() && this.store().read(this.player, data -> this.engine.journal(data).hasCompletedOperation(this.taskId));
    }
}
