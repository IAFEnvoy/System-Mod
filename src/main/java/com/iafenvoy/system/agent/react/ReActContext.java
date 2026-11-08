package com.iafenvoy.system.agent.react;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.mojang.serialization.Codec;
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
    private final Map<String, ReActTool.Description> descriptions;
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
        this.instruction = Objects.requireNonNull(instruction);
        this.question = Objects.requireNonNull(question);
        this.inputCodec = Input.codec(observationCodec);
        this.guard = Objects.requireNonNull(guard);
        this.recoverable = Set.copyOf(recoverable);
        this.toolReserveMillis = toolReserveMillis;
        this.maxIterations = execution.reactIterations();
        Map<String, ReActTool<?, O>> bindings = new LinkedHashMap<>();
        Map<String, ReActTool.Description> descriptions = new LinkedHashMap<>();
        for (ReActTool<?, O> tool : tools) {
            if (bindings.putIfAbsent(tool.id(), tool) != null)
                throw new IllegalArgumentException("Duplicate ReAct tool " + tool.id());
            descriptions.put(tool.id(), tool.describe());
        }
        this.tools = Collections.unmodifiableMap(bindings);
        this.descriptions = Collections.unmodifiableMap(descriptions);
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

    <S> S decide(Codec<S> codec, boolean finalOnly) throws Exception {
        int remaining = this.maxIterations - this.iterations;
        this.iterations++;
        return this.execution.model(this.instruction, this.inputCodec,
                new Input<>(this.question, this.observations(), this.descriptions, remaining,
                        this.execution.remainingToolCalls(), finalOnly), codec);
    }

    <I> boolean perform(ReActTool<I, O> tool, I input) throws Exception {
        if (this.tools.get(tool.id()) != tool) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        // Failed attempts are remembered too; a loop never silently retries an already paid operation.
        if (!this.attempted.add(tool.id() + ":" + tool.repetitionKey().apply(input))) return false;
        O observation = tool.handler().execute(input);
        this.check();
        this.observations.add(Objects.requireNonNull(observation));
        return true;
    }

    private record Input<O>(String question, List<O> observations, Map<String, ReActTool.Description> tools,
                            int remainingIterations, int remainingTools, boolean finalOnly) {
        static <O> MapCodec<Input<O>> codec(Codec<O> observationCodec) {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.fieldOf("question").forGetter(Input<O>::question),
                    observationCodec.listOf().fieldOf("observations").forGetter(Input<O>::observations),
                    ReActTool.Description.CATALOGUE.fieldOf("tools").forGetter(Input<O>::tools),
                    Codec.INT.fieldOf("remainingIterations").forGetter(Input<O>::remainingIterations),
                    Codec.INT.fieldOf("remainingTools").forGetter(Input<O>::remainingTools),
                    Codec.BOOL.fieldOf("finalOnly").forGetter(Input<O>::finalOnly)
            ).apply(i, Input<O>::new));
        }
    }
}
