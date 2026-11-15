package com.iafenvoy.system.agent.react;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/**
 * One loop owns one prompt, allowlist and evidence trace; ExecutionContext remains the shared hard budget.
 */
public final class ReActContext<O> {
    private final ExecutionContext execution;
    private final String instruction;
    private final String question;
    private final Map<String, ReActTool<?, O>> tools;
    private final MapCodec<Input<O>> inputCodec;
    private final Runnable guard;
    private final Set<Failure> recoverable;
    private final long toolReserveMillis;
    private final int maxIterations;
    private final List<O> observations = new ArrayList<>();
    private final Set<String> attempted = new HashSet<>();
    private int iterations;

    public ReActContext(ExecutionContext execution, String instruction, String question, Codec<O> observationCodec,
                        List<ReActTool<?, O>> tools, Runnable guard, Set<? extends Failure> recoverable, long toolReserveMillis) {
        this.execution = Objects.requireNonNull(execution);
        this.question = Objects.requireNonNull(question);
        this.inputCodec = Input.codec(observationCodec);
        this.guard = Objects.requireNonNull(guard);
        this.recoverable = Set.copyOf(recoverable);
        this.toolReserveMillis = toolReserveMillis;
        this.maxIterations = execution.reactIterations();
        Map<String, ReActTool<?, O>> bindings = new LinkedHashMap<>();
        for (ReActTool<?, O> tool : tools.stream().sorted(Comparator
                .comparing((ReActTool<?, O> value) -> value.priority()).thenComparing(ReActTool::id)).toList()) {
            if (bindings.putIfAbsent(tool.id(), tool) != null)
                throw new IllegalArgumentException("Duplicate ReAct tool " + tool.id());
        }
        this.tools = Collections.unmodifiableMap(bindings);
        this.instruction = Objects.requireNonNull(instruction) + "\n\n" + execution.prompt(SystemPrompts.TOOL_CALL);
    }

    public List<O> observations() {
        return List.copyOf(this.observations);
    }

    public int iterations() {
        return this.iterations;
    }

    Collection<ReActTool<?, O>> tools() {
        return this.tools.values();
    }

    void check() {
        this.execution.ensureMemoryCurrent();
        this.guard.run();
    }

    boolean canContinue() {
        return this.iterations < this.maxIterations && this.execution.remainingModelCalls() > 0;
    }

    boolean shouldFinish() {
        return this.maxIterations - this.iterations <= 1 || this.execution.remainingModelCalls() <= 1
                || this.execution.remainingToolCalls() == 0 || this.execution.remainingSteps() <= 2 || this.tools.isEmpty()
                || !this.observations.isEmpty() && this.shortOnTime();
    }

    boolean shortOnTime() {
        return this.execution.remainingMillis() <= this.execution.modelTimeoutMillis() + this.toolReserveMillis;
    }

    boolean recoverable(Failure failure) {
        return this.recoverable.contains(failure);
    }

    <S> S decide(Codec<S> codec, SchemaMapCodec<?> answerCodec, boolean finalOnly) throws Exception {
        // Native function names cannot contain ':' or '.'. Aliases are unique within this request.
        Map<String, ReActTool<?, O>> aliases = new LinkedHashMap<>();
        List<ModelClient.Function> functions = new ArrayList<>();
        if (!finalOnly) for (ReActTool<?, O> tool : this.tools.values()) {
            String alias = "tool_" + aliases.size();
            aliases.put(alias, tool);
            functions.add(tool.function(alias));
        }
        functions.add(new ModelClient.Function("answer", "Finish with the final answer from available evidence. Does not perform a game action.", answerCodec.schema()));
        int remaining = this.maxIterations - this.iterations;
        this.iterations++;
        try {
            return this.execution.model(this.instruction, this.inputCodec,
                    new Input<>(this.question, this.observations(), remaining,
                            this.execution.remainingToolCalls(), finalOnly), functions, reply -> {
                        if (reply.toolCalls().size() != 1) throw new IllegalArgumentException("Expected exactly one function call");
                        ModelClient.ToolCall call = reply.toolCalls().getFirst();
                        JsonObject arguments = JsonParser.parseString(call.arguments()).getAsJsonObject();
                        JsonObject decision;
                        if (call.name().equals("answer")) {
                            decision = arguments;
                            if (!finalOnly) decision.addProperty("type", "answer");
                        } else {
                            ReActTool<?, O> tool = aliases.get(call.name());
                            if (tool == null) throw new IllegalArgumentException("Unavailable function " + call.name());
                            decision = new JsonObject();
                            decision.addProperty("type", tool.id());
                            decision.add("input", arguments);
                        }
                        return this.execution.store().codec(codec).parse(JsonOps.INSTANCE, decision).getOrThrow();
                    });
        } catch (Exception error) {
            SystemMod.LOGGER.warn("ReAct decision failed: task={}, iteration={}, finalOnly={}, observations={}, modelCalls={}, remainingMillis={}, failure={}",
                    this.execution.taskId(), this.iterations, finalOnly, this.observations.size(), this.execution.modelCalls(),
                    this.execution.remainingMillis(), AgentException.failure(error).name(), error);
            throw error;
        }
    }

    <I> boolean perform(ReActTool<I, O> tool, I input) throws Exception {
        if (this.tools.get(tool.id()) != tool) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        // Failed attempts are remembered too; a loop never silently retries an already paid operation.
        if (!this.attempted.add(tool.id() + ":" + tool.repetitionKey().apply(input))) return false;
        O observation;
        try {
            observation = tool.handler().execute(input);
        } catch (Exception error) {
            SystemMod.LOGGER.warn("ReAct tool failed: task={}, iteration={}, tool={}, observations={}, failure={}",
                    this.execution.taskId(), this.iterations, tool.id(), this.observations.size(), AgentException.failure(error).name(), error);
            throw error;
        }
        this.check();
        this.observations.add(Objects.requireNonNull(observation));
        return true;
    }

    private record Input<O>(String question, List<O> observations,
                            int remainingIterations, int remainingTools, boolean finalOnly) {
        static <O> MapCodec<Input<O>> codec(Codec<O> observationCodec) {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.fieldOf("question").forGetter(Input<O>::question),
                    observationCodec.listOf().fieldOf("observations").forGetter(Input<O>::observations),
                    Codec.INT.fieldOf("remainingIterations").forGetter(Input<O>::remainingIterations),
                    Codec.INT.fieldOf("remainingTools").forGetter(Input<O>::remainingTools),
                    Codec.BOOL.fieldOf("finalOnly").forGetter(Input<O>::finalOnly)
            ).apply(i, Input<O>::new));
        }
    }
}
