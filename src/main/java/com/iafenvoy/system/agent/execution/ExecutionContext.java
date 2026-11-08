package com.iafenvoy.system.agent.execution;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.chat.AiAccess;
import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.iafenvoy.system.agent.storage.AgentJournal;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.agent.tool.ToolContext;
import com.iafenvoy.system.agent.tool.ToolPriority;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.data.store.EventTask;
import com.iafenvoy.system.data.store.State;
import com.iafenvoy.system.data.store.Step;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-task authority and budget shared by routing, model calls and tool execution.
 */
public final class ExecutionContext {
    private final AgentEngine engine;
    private final AgentStore store;
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
    private Identifier owner;
    private Optional<Identifier> eventOwner = Optional.empty();
    private ExecutionPolicy policy;
    private long expires;
    private int models, tools, steps, writes, tokens;
    private int toolContexts;
    private int actions;
    private String stepPrefix = "";
    private final Set<String> authorizedExternalTools = new HashSet<>();
    private final Set<ToolRegistry.Type<?, ?>> chatReadTools = Collections.newSetFromMap(new IdentityHashMap<>());

    ExecutionContext(AgentEngine engine, UUID player, String taskId, String eventKey,
                     long started, long epoch, ExecutionPolicy initial, AgentStore store, long incarnation) {
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
            EventTask task = this.engine.journal(data).getTask(eventKey);
            this.eventOwner = task.owner;
            this.models = task.modelCalls;
            this.tools = task.toolCalls;
            this.steps = task.workflowSteps;
            this.writes = task.writeSteps;
            this.tokens = task.tokens;
            this.expires = task.expires;
            return null;
        });
    }

    void select(Identifier owner, AgentModule.Descriptor descriptor, ExecutionPolicy value) {
        if (this.isEvent() && !this.eventOwner.equals(Optional.ofNullable(owner)))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        this.owner = owner;
        this.module = descriptor;
        this.policy = value;
        this.authorizedExternalTools.clear();
        // Routing time remains charged, but selecting a slow module may use its own deadline.
        this.expires = this.started + value.ttlMillis();
        if (this.models > value.modelCalls() || this.tokens > value.totalTokens())
            throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
    }

    public UUID player() {
        return this.player;
    }

    public String taskId() {
        return this.taskId;
    }

    public boolean isEvent() {
        return this.eventKey != null;
    }

    public boolean routed() {
        return this.routeSource.automatic();
    }

    public RouteSource routeSource() {
        return this.routeSource;
    }

    void routeSource(RouteSource source) {
        this.routeSource = source;
    }

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

    public String promptRevision() {
        return this.prompts.revision();
    }

    public long incarnation() {
        return this.incarnation;
    }

    public long memoryEpoch() {
        return this.memoryEpoch;
    }

    public AgentStore store() {
        return this.store;
    }

    public List<String> commandUsages() {
        return this.engine.modules().commands().usages(this::available);
    }

    public List<AgentResult.Action> shortcuts() {
        return this.engine.modules().shortcuts(this::available);
    }

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

    public boolean externalServicesAllowed() {
        return this.mode().usesAi();
    }

    public ConversationMode mode() {
        this.ensureMemoryCurrent();
        return this.permissions().effective(this.store.read(this.player, data -> data.mode));
    }

    public AiAccess.Permissions permissions() {
        return this.engine.permissions(this.player);
    }

    public boolean freeChatAllowed() {
        return this.mode().chats();
    }

    public void requireFreeChat() {
        this.requireExternalServices();
        if (!this.mode().chats())
            throw new AgentException(AgentFailure.FREE_CHAT_DISABLED);
    }

    public List<AgentModule<?>> chatActions() {
        this.requireFreeChat();
        if (this.isEvent() || this.mode() != ConversationMode.FREE) return List.of();
        return this.engine.modules().descriptors().stream()
                .filter(descriptor -> this.engine.modules().modelSelectable(descriptor.id()) && this.available(descriptor.id()))
                .<AgentModule<?>>map(descriptor -> this.engine.module(this.player, descriptor.id())).filter(Objects::nonNull).toList();
    }

    /**
     * AI-selected workflows share the same task and counters, with a narrower policy and isolated step IDs.
     * Execute inline on the agent worker: resubmitting would wait on this player's own interactive lock.
     */
    public AgentResult action(AgentAction input) {
        this.requireFreeChat();
        if (this.isEvent() || this.mode() != ConversationMode.FREE || !this.engine.modules().modelSelectable(input.id()))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        AgentModule<?> target = this.engine.module(this.player, input.id());
        if (target == null || !this.available(input.id())) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (++this.steps > this.policy.steps()) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
        AgentModule.Descriptor previousModule = this.module;
        Identifier previousOwner = this.owner;
        ExecutionPolicy previousPolicy = this.policy;
        RouteSource previousSource = this.routeSource;
        long previousExpires = this.expires;
        String previousPrefix = this.stepPrefix;
        Set<String> previousAuthorized = Set.copyOf(this.authorizedExternalTools);
        ExecutionPolicy child = this.engine.policy(target);
        ExecutionPolicy limited = new ExecutionPolicy(
                Math.min(previousPolicy.modelCalls(), this.models + child.modelCalls()),
                Math.min(previousPolicy.toolCalls(), this.tools + child.toolCalls()),
                Math.min(previousPolicy.steps(), this.steps + child.steps()),
                Math.min(previousPolicy.writeSteps(), this.writes + child.writeSteps()),
                Math.min(previousPolicy.inputBytes(), child.inputBytes()),
                Math.min(previousPolicy.outputTokens(), child.outputTokens()),
                Math.min(previousPolicy.totalTokens(), this.tokens + child.totalTokens()),
                Math.min(previousExpires, System.currentTimeMillis() + child.ttlMillis()) - this.started,
                Math.min(previousPolicy.modelTimeoutMillis(), child.modelTimeoutMillis()),
                Math.min(previousPolicy.reactIterations(), child.reactIterations()));
        AgentResult result;
        try {
            this.routeSource = RouteSource.LLM;
            this.stepPrefix = previousPrefix + "action/" + this.actions++ + "/";
            result = this.engine.execute(this, input, limited);
        } finally {
            this.module = previousModule;
            this.owner = previousOwner;
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
            if (!this.authorizedExternalTools.contains(type.id())) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
            if (!this.toolAvailable(type)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        }
    }

    public long remainingMillis() {
        return Math.max(0, this.expires - System.currentTimeMillis());
    }

    public long modelTimeoutMillis() {
        return Math.min(this.policy.modelTimeoutMillis(), this.remainingMillis());
    }

    /**
     * Billable network tools obey the same external-service opt-out as inference, before each call.
     */
    public void requireExternalServices() {
        this.ensureMemoryCurrent();
        AiAccess.Permissions permissions = this.permissions();
        if (!permissions.allowAi() || permissions.maxMode() == ConversationMode.OFFLINE)
            throw new AgentException(AgentFailure.AI_FORBIDDEN);
        if (!permissions.modelConfigured()) throw new AgentException(AgentFailure.MODEL_NOT_CONFIGURED);
        if (!this.store.read(this.player, data -> data.mode.usesAi())) throw new AgentException(AgentFailure.MODEL_DISABLED);
        if (permissions.modelCallsPerDay() == 0)
            throw new AgentException(AgentFailure.RATE_LIMITED);
    }

    public int remainingModelCalls() {
        return Math.max(0, this.policy.modelCalls() - this.models);
    }

    public int remainingToolCalls() {
        return Math.max(0, this.policy.toolCalls() - this.tools);
    }

    public int remainingSteps() {
        return Math.max(0, this.policy.steps() - this.steps);
    }

    public int reactIterations() {
        return this.policy.reactIterations();
    }

    /**
     * General reasoning gets only registry-advertised reads; paid external tools are never advertised here.
     */
    public ToolContext reasoningTools() {
        this.ensureMemoryCurrent();
        if (this.isEvent()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return new ToolContext(this, this.engine.tools().reasoning(owner -> this.engine.moduleEnabled(this.player, owner)),
                "react/" + this.toolContexts++);
    }

    /** Free chat owns an explicit read scope; a provider cannot export a write or a disabled feature. */
    public ToolContext chatTools(Collection<ToolRegistry.Type<?, ?>> additions) {
        this.requireFreeChat();
        if (this.isEvent() || this.mode() != ConversationMode.FREE) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        Map<String, ToolRegistry.ScopedTool<?, ?>> allowed = this.engine.tools()
                .chat(owner -> this.engine.moduleEnabled(this.player, owner), additions);
        allowed.values().forEach(tool -> this.chatReadTools.add(tool.tool().type()));
        return new ToolContext(this, allowed, "chat/" + this.toolContexts++);
    }

    public ToolContext tools(ToolRegistry.Type<?, ?>... types) {
        Map<String, ToolRegistry.ScopedTool<?, ?>> allowed = new LinkedHashMap<>();
        for (ToolRegistry.Type<?, ?> type : types) this.addTool(allowed, type);
        return new ToolContext(this, allowed, "react/" + this.toolContexts++);
    }

    private <I, O> void addTool(Map<String, ToolRegistry.ScopedTool<?, ?>> allowed, ToolRegistry.Type<I, O> type) {
        this.ensureMemoryCurrent();
        if (!this.toolAvailable(type)) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        ToolRegistry.ScopedTool<I, O> scoped = this.engine.tools().scope(type, this.owner, owner -> this.engine.moduleEnabled(this.player, owner));
        if (scoped.tool().write() && this.module.optionalTools().contains(type.id()))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        allowed.put(type.id(), scoped);
    }

    public void checkToolOwner(ToolRegistry.ScopedTool<?, ?> scoped) {
        this.ensureMemoryCurrent();
        if (!this.engine.moduleEnabled(this.player, scoped.owner()) || !this.engine.toolAvailable(this.player, scoped.tool().id()))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    public <I, O> O scopedTool(ToolRegistry.ScopedTool<I, O> scoped, String stepId, I input) throws Exception {
        this.checkToolOwner(scoped);
        // A scope is a subset of this workflow or an explicitly advertised read, never an alternate write route.
        if (!this.toolAvailable(scoped.tool().type()) && (scoped.tool().write()
                || scoped.tool().reasoningDescription().isBlank() && !this.chatReadTools.contains(scoped.tool().type())))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        O result = this.executeTool(scoped.tool(), stepId, input);
        this.checkToolOwner(scoped);
        return result;
    }

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
        if (this.module != null && (!this.engine.moduleEnabled(this.player, this.owner) || !this.available(this.module.id())))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    private void persist() {
        if (!this.isEvent()) return;
        this.store().update(this.player, data -> {
            this.engine.journal(data).updateTaskUsage(this.eventKey,
                    this.models, this.tools, this.steps, this.writes, this.tokens);
            return null;
        });
    }

    public <I, O> O model(String instruction, MapCodec<I> inputCodec, I input, Codec<O> outputCodec) throws Exception {
        // Enforce consent, quotas and concurrency here; the gateway owns HTTP and actual usage accounting.
        this.requireExternalServices();
        this.engine.model().checkConfigured();
        if (++this.steps > this.policy.steps() || this.models >= this.policy.modelCalls())
            throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
        String content = CodecIO.write(ModelInput.codec(inputCodec), new ModelInput<>(input, new RequestContext(this.language)));
        // Reserve input bytes conservatively as tokens; this execution budget is separate from billed usage.
        long inputBytes = (long) instruction.getBytes(StandardCharsets.UTF_8).length + content.getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > this.policy.inputBytes() || this.tokens + inputBytes + this.policy.outputTokens() > this.policy.totalTokens())
            throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
        int estimated = (int) inputBytes;
        long wait = this.modelTimeoutMillis();
        if (!this.engine.modelSlots().tryAcquire(wait, TimeUnit.MILLISECONDS)) throw new AgentException(AgentFailure.BUSY);
        try {
            this.check();
            this.store().update(this.player, data -> {
                AiAccess.Permissions permissions = this.permissions();
                if (!permissions.allowAi() || permissions.maxMode() == ConversationMode.OFFLINE)
                    throw new AgentException(AgentFailure.AI_FORBIDDEN);
                if (!permissions.modelConfigured()) throw new AgentException(AgentFailure.MODEL_NOT_CONFIGURED);
                if (!data.mode.usesAi() || (!this.isEvent() && data.conversation.memoryEpoch != this.memoryEpoch))
                    throw new AgentException(AgentFailure.MODEL_DISABLED);
                String day = LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString();
                if (!day.equals(data.modelQuota.usageDate)) {
                    data.modelQuota.usageDate = day;
                    data.modelQuota.usedModelCalls = 0;
                }
                if (data.modelQuota.usedModelCalls >= permissions.modelCallsPerDay())
                    throw new AgentException(AgentFailure.RATE_LIMITED);
                data.modelQuota.usedModelCalls++;
                return null;
            });
            this.models++;
            this.tokens += estimated + this.policy.outputTokens();
            this.persist();
            ModelClient.Reply reply = this.engine.model().complete(this.player, List.of(
                            new ModelClient.Message(SystemMod.MOD_ID, instruction), new ModelClient.Message("user", content)),
                    this.policy.outputTokens(), Duration.ofMillis(this.modelTimeoutMillis()));
            this.tokens = (int) Math.min(Integer.MAX_VALUE, (long) this.tokens + Math.max(0L, (long) reply.totalTokens() - estimated - this.policy.outputTokens()));
            this.persist();
            this.check();
            this.requireExternalServices();
            if (this.tokens > this.policy.totalTokens()) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
            try {
                return CodecIO.parse(outputCodec, reply.value());
            } catch (RuntimeException error) {
                throw new AgentException(AgentFailure.MODEL_INVALID);
            }
        } finally {
            this.engine.modelSlots().release();
        }
    }

    private record RequestContext(String playerLanguage) {
        static final Codec<RequestContext> CODEC = Codec.STRING.fieldOf("playerLanguage")
                .xmap(RequestContext::new, RequestContext::playerLanguage).codec();
    }

    private record ModelInput<I>(I input, RequestContext context) {
        static <I> Codec<ModelInput<I>> codec(MapCodec<I> inputCodec) {
            return RecordCodecBuilder.create(i -> i.group(
                    inputCodec.forGetter(ModelInput<I>::input),
                    RequestContext.CODEC.fieldOf("requestContext").forGetter(ModelInput<I>::context)
            ).apply(i, ModelInput<I>::new));
        }
    }

    public <I, O> O tool(ToolRegistry.Type<I, O> type, String stepId, I input) throws Exception {
        this.check();
        this.ensureMemoryCurrent();
        if (!stepId.matches("[a-zA-Z0-9_./-]+")) throw new AgentException(AgentFailure.INVALID_INPUT);
        String id = type.id();
        if (this.module == null || !this.module.tools().contains(id) && !this.module.optionalTools().contains(id))
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        ToolRegistry.Tool<I, O> tool = this.engine.tool(this.player, type, this.owner);
        if (this.module.optionalTools().contains(id) && tool.write()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return this.executeTool(tool, stepId, input);
    }

    private <I, O> O executeTool(ToolRegistry.Tool<I, O> tool, String stepId, I input) throws Exception {
        ToolRegistry.Type<I, O> type = tool.type();
        String id = type.id();
        this.ensureMemoryCurrent();
        if (tool.priority() == ToolPriority.PAID) this.requireExternalServices();
        if (!this.engine.toolAvailable(this.player, id)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if ((this.routeSource == RouteSource.SEMANTIC || this.routeSource == RouteSource.ROUTE_CACHE) && tool.write())
            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        if (this.isEvent() && !tool.allowEvent()) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        String operationKey = this.taskId + ":" + this.stepPrefix + stepId;
        String encodedInput = tool.write() ? CodecIO.write(type.inputCodec().codec(), input) : null;
        String fingerprint = id + ":" + encodedInput;
        // Replay completed writes by task/step; an unfinished non-idempotent write has an unknown effect.
        if (tool.write()) {
            Dynamic<?> previous = this.store().read(this.player, data -> {
                Step receipt = this.engine.journal(data).getOperation(operationKey);
                if (receipt == null) return null;
                if (!fingerprint.equals(receipt.fingerprint)) throw new AgentException(AgentFailure.INVALID_INPUT);
                if (receipt.state == State.DONE) return receipt.result;
                if (!tool.idempotent()) throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
                return null;
            });
            if (previous != null) {
                this.completedLocalSteps.add(stepId);
                return type.resultCodec().parse(previous).getOrThrow(_ -> new AgentException(AgentFailure.INVALID_INPUT));
            }
        }
        if (++this.steps > this.policy.steps() || ++this.tools > this.policy.toolCalls() || (tool.write() && ++this.writes > this.policy.writeSteps()))
            throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
        this.persist();
        // Persist RUNNING before invoking a write so a crash cannot silently turn it into a fresh operation.
        if (tool.write()) this.store().update(this.player, data -> {
            AgentJournal storage = this.engine.journal(data);
            storage.assertOperationCount(operationKey);
            Step receipt = new Step();
            receipt.fingerprint = fingerprint;
            storage.putOperation(operationKey, receipt);
            return null;
        });
        try {
            O result = tool.handler().execute(this, input).toCompletableFuture()
                    .get(this.remainingMillis(), TimeUnit.MILLISECONDS);
            if (!tool.write()) {
                this.ensureMemoryCurrent();
                if (!this.engine.toolAvailable(this.player, id)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            }
            if (tool.write()) this.store().update(this.player, data -> {
                this.engine.journal(data).completeOperation(operationKey,
                        new Dynamic<>(JsonOps.INSTANCE, type.resultCodec().encodeStart(JsonOps.INSTANCE, result).getOrThrow()));
                return null;
            });
            if (tool.write()) this.completedLocalSteps.add(stepId);
            return result;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AgentException error) throw error;
            if (tool.write()) throw new AgentException(AgentFailure.EFFECT_UNKNOWN, e);
            throw new AgentException(AgentFailure.TOOL_FAILED, e);
        } catch (TimeoutException e) {
            this.cancel();
            throw new AgentException(tool.write() ? AgentFailure.EFFECT_UNKNOWN : AgentFailure.EXPIRED, e);
        }
    }

    public void ensureMemoryCurrent() {
        this.check();
        if (!this.isEvent() && this.store().read(this.player, data -> data.conversation.memoryEpoch) != this.memoryEpoch)
            throw new AgentException(AgentFailure.CANCELLED);
    }

    public int modelCalls() {
        return this.models;
    }

    public boolean hasCompletedSteps() {
        if (!this.completedLocalSteps.isEmpty()) return true;
        // A resumed event may fail before reaching a write already completed in an earlier run.
        return this.isEvent() && this.store().read(this.player, data -> this.engine.journal(data).hasCompletedOperation(this.taskId));
    }
}
