package com.iafenvoy.system.agent;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.module.core.CoreSystemModule;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.util.codec.CodecIO;
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
    private ExecutionPolicy policy;
    private long expires;
    private int models, tools, steps, writes, tokens;

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
            PlayerData.EventTask task = ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.get(eventKey);
            this.models = task.modelCalls;
            this.tools = task.toolCalls;
            this.steps = task.workflowSteps;
            this.writes = task.writeSteps;
            this.tokens = task.tokens;
            this.expires = task.expires;
            return null;
        });
    }

    void select(AgentModule.Descriptor descriptor, ExecutionPolicy value) {
        this.module = descriptor;
        this.policy = value;
        // Routing time remains charged, but selecting a slow module may use its own deadline.
        this.expires = this.started + value.ttlMillis();
        if (this.models > value.modelCalls() || this.tokens > value.totalTokens())
            throw new AgentException("BUDGET_EXCEEDED");
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

    public CommerceCatalog catalog() {
        return this.engine.catalog();
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

    public long remainingMillis() {
        return Math.max(0, this.expires - System.currentTimeMillis());
    }

    public void cancel() {
        this.cancelled.set(true);
    }

    public boolean active() {
        return !this.cancelled.get() && !this.engine.closed() && this.engine.current(this.player, this.incarnation) && this.remainingMillis() > 0;
    }

    public void check() {
        if (this.cancelled.get() || this.engine.closed() || !this.engine.current(this.player, this.incarnation))
            throw new AgentException("CANCELLED");
        if (this.remainingMillis() == 0) throw new AgentException("EXPIRED");
        if (this.module != null && !this.available(this.module.id())) throw new AgentException("CAPABILITY_UNAVAILABLE");
    }

    private void persist() {
        if (!this.isEvent()) return;
        this.store().update(this.player, data -> {
            PlayerData.EventTask task = ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.get(this.eventKey);
            task.modelCalls = this.models;
            task.toolCalls = this.tools;
            task.workflowSteps = this.steps;
            task.writeSteps = this.writes;
            task.tokens = this.tokens;
            return null;
        });
    }

    public <I, O> O model(String instruction, MapCodec<I> inputCodec, I input, Codec<O> outputCodec) throws Exception {
        // Enforce consent, quotas and concurrency here; the gateway owns HTTP and actual usage accounting.
        this.check();
        if (++this.steps > this.policy.steps() || this.models >= this.policy.modelCalls())
            throw new AgentException("BUDGET_EXCEEDED");
        String content = CodecIO.write(ModelInput.codec(inputCodec), new ModelInput<>(input, new RequestContext(this.language)));
        // Reserve input bytes conservatively as tokens; this execution budget is separate from billed usage.
        long inputBytes = (long) instruction.getBytes(StandardCharsets.UTF_8).length + content.getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > this.policy.inputBytes() || this.tokens + inputBytes + this.policy.outputTokens() > this.policy.totalTokens())
            throw new AgentException("BUDGET_EXCEEDED");
        int estimated = (int) inputBytes;
        long wait = Math.min(this.policy.modelTimeoutMillis(), this.remainingMillis());
        if (!this.engine.modelSlots().tryAcquire(wait, TimeUnit.MILLISECONDS)) throw new AgentException("BUSY");
        try {
            this.check();
            this.store().update(this.player, data -> {
                if (!data.externalModel || (!this.isEvent() && data.conversation.memoryEpoch != this.memoryEpoch))
                    throw new AgentException("MODEL_DISABLED");
                String day = LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString();
                if (!day.equals(data.modelQuota.usageDate)) {
                    data.modelQuota.usageDate = day;
                    data.modelQuota.usedModelCalls = 0;
                }
                if (data.modelQuota.usedModelCalls >= AgentConfig.INSTANCE.get().modelCallsPerPlayerDay())
                    throw new AgentException("RATE_LIMITED");
                data.modelQuota.usedModelCalls++;
                return null;
            });
            this.models++;
            this.tokens += estimated + this.policy.outputTokens();
            this.persist();
            ModelClient.Reply reply = this.engine.model().complete(this.player, List.of(
                            new ModelClient.Message(SystemMod.MOD_ID, instruction), new ModelClient.Message("user", content)),
                    this.policy.outputTokens(), Duration.ofMillis(Math.min(this.policy.modelTimeoutMillis(), this.remainingMillis())));
            this.tokens = (int) Math.min(Integer.MAX_VALUE, (long) this.tokens + Math.max(0L, (long) reply.totalTokens() - estimated - this.policy.outputTokens()));
            this.persist();
            this.check();
            if (this.tokens > this.policy.totalTokens()) throw new AgentException("BUDGET_EXCEEDED");
            try {
                return CodecIO.parse(outputCodec, reply.value());
            } catch (RuntimeException error) {
                throw new AgentException("MODEL_INVALID");
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
        if (!stepId.matches("[a-zA-Z0-9_./-]+")) throw new AgentException("INVALID_INPUT");
        String id = type.id();
        if (this.module == null || !this.module.tools().contains(id)) throw new AgentException("NOT_AUTHORIZED");
        ToolRegistry.Tool<I, O> tool = this.engine.tools().require(type);
        if ((this.routeSource == RouteSource.SEMANTIC || this.routeSource == RouteSource.ROUTE_CACHE) && tool.write())
            throw new AgentException("NOT_AUTHORIZED");
        if (this.isEvent() && !tool.allowEvent()) throw new AgentException("NOT_AUTHORIZED");
        String operationKey = this.taskId + ":" + stepId;
        String encodedInput = tool.write() ? CodecIO.write(type.inputCodec().codec(), input) : null;
        String fingerprint = id + ":" + encodedInput;
        // Replay completed writes by task/step; an unfinished non-idempotent write has an unknown effect.
        if (tool.write()) {
            Dynamic<?> previous = this.store().read(this.player, data -> {
                PlayerData.Step receipt = ModuleData.get(data, CoreSystemModule.JOURNAL).operations.get(operationKey);
                if (receipt == null) return null;
                if (!fingerprint.equals(receipt.fingerprint)) throw new AgentException("INVALID_INPUT");
                if ("DONE".equals(receipt.state)) return receipt.result;
                if (!tool.idempotent()) throw new AgentException("EFFECT_UNKNOWN");
                return null;
            });
            if (previous != null) {
                this.completedLocalSteps.add(stepId);
                return type.resultCodec().parse(previous).getOrThrow(_ -> new AgentException("INVALID_INPUT"));
            }
        }
        if (++this.steps > this.policy.steps() || ++this.tools > this.policy.toolCalls() || (tool.write() && ++this.writes > this.policy.writeSteps()))
            throw new AgentException("BUDGET_EXCEEDED");
        this.persist();
        // Persist RUNNING before invoking a write so a crash cannot silently turn it into a fresh operation.
        if (tool.write()) this.store().update(this.player, data -> {
            if (ModuleData.get(data, CoreSystemModule.JOURNAL).operations.size() >= 8192 && !ModuleData.get(data, CoreSystemModule.JOURNAL).operations.containsKey(operationKey))
                throw new AgentException("STORAGE_FULL");
            PlayerData.Step receipt = new PlayerData.Step();
            receipt.fingerprint = fingerprint;
            ModuleData.get(data, CoreSystemModule.JOURNAL).operations.put(operationKey, receipt);
            return null;
        });
        try {
            O result = tool.handler().execute(this, input).toCompletableFuture()
                    .get(this.remainingMillis(), TimeUnit.MILLISECONDS);
            if (tool.write()) this.store().update(this.player, data -> {
                PlayerData.Step receipt = ModuleData.get(data, CoreSystemModule.JOURNAL).operations.get(operationKey);
                receipt.state = "DONE";
                receipt.result = new Dynamic<>(JsonOps.INSTANCE, type.resultCodec().encodeStart(JsonOps.INSTANCE, result).getOrThrow());
                return null;
            });
            if (tool.write()) this.completedLocalSteps.add(stepId);
            return result;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AgentException error) throw error;
            if (tool.write()) throw new AgentException("EFFECT_UNKNOWN");
            throw new AgentException("TOOL_FAILED");
        } catch (TimeoutException e) {
            this.cancel();
            throw new AgentException(tool.write() ? "EFFECT_UNKNOWN" : "EXPIRED");
        }
    }

    public void ensureMemoryCurrent() {
        this.check();
        if (!this.isEvent() && this.store().read(this.player, data -> data.conversation.memoryEpoch) != this.memoryEpoch)
            throw new AgentException("CANCELLED");
    }

    public int modelCalls() {
        return this.models;
    }

    public boolean hasCompletedSteps() {
        if (!this.completedLocalSteps.isEmpty()) return true;
        // A resumed event may fail before reaching a write already completed in an earlier run.
        return this.isEvent() && this.store().read(this.player, data -> ModuleData.get(data, CoreSystemModule.JOURNAL).hasCompletedOperation(this.taskId));
    }
}
