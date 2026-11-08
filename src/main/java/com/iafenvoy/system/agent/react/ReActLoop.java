package com.iafenvoy.system.agent.react;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared decide -> act -> observe loop. A round is one decision attempt, including final synthesis and errors.
 */
public final class ReActLoop {
    public static final Set<Failure> MODEL_FAILURES = Set.of(AgentFailure.BUDGET_EXCEEDED, AgentFailure.MODEL_INVALID, AgentFailure.MODEL_TIMEOUT, AgentFailure.MODEL_UNAVAILABLE, AgentFailure.RATE_LIMITED, AgentFailure.BUSY);
    private static final Set<Failure> ALLOWED_FAILURES = Set.of(AgentFailure.BUDGET_EXCEEDED, AgentFailure.RATE_LIMITED, AgentFailure.BUSY);

    private ReActLoop() {
    }

    public static <A, O, R> R run(ReActContext<O> context, MapCodec<A> answerCodec,
                                  Completion<A, R> completion, Fallback<R> fallback) throws Exception {
        Map<String, MapCodec<? extends Step<A, O>>> types = new LinkedHashMap<>();
        types.put("answer", answerCodec.xmap(Final<A, O>::new, Final::answer));
        for (ReActTool<?, O> tool : context.tools()) types.put(tool.id(), choice(tool));
        Codec<Step<A, O>> codec = Codec.STRING.partialDispatch("type", step -> DataResult.success(step.type()),
                id -> types.containsKey(id) ? DataResult.success(types.get(id)) : DataResult.error(() -> "Unavailable ReAct tool " + id));
        boolean finalOnly = false;
        Failure failure = AgentFailure.ITERATION_LIMIT;
        context.check();
        while (context.canContinue()) {
            context.check();
            finalOnly |= context.shouldFinish();
            try {
                Step<A, O> step = finalOnly
                        ? new Final<>(context.decide(answerCodec.codec(), true)) : context.decide(codec, false);
                if (step instanceof Final<A, O>(A answer1)) {
                    R result = completion.finish(answer1);
                    context.check();
                    return result;
                }
                if (!context.observations().isEmpty() && context.shortOnTime()) {
                    finalOnly = true;
                    continue;
                }
                if (!step.perform(context)) finalOnly = true;
            } catch (AgentException error) {
                // Authority/cancellation wins over recovery, and no failed tool is automatically reissued.
                context.check();
                failure = error.failure();
                if (context.observations().isEmpty() || !context.recoverable(failure)) throw error;
                if (finalOnly || ALLOWED_FAILURES.contains(failure))
                    break;
                finalOnly = true;
            }
        }
        context.check();
        return fallback.finish(failure);
    }

    private static <A, I, O> MapCodec<Step<A, O>> choice(ReActTool<I, O> tool) {
        return tool.inputCodec().codec().fieldOf("input").xmap(input -> new Call<>(tool, input), step -> {
            if (!(step instanceof Call<?, ?, ?>(ReActTool<?, ?> tool1, Object input1)) || tool1 != tool)
                throw new IllegalArgumentException("Incorrect ReAct tool codec");
            @SuppressWarnings("unchecked") I input = (I) input1;
            return input;
        });
    }

    private sealed interface Step<A, O> permits Final, Call {
        String type();

        default boolean perform(ReActContext<O> context) throws Exception {
            throw new AgentException(AgentFailure.MODEL_INVALID);
        }
    }

    private record Final<A, O>(A answer) implements Step<A, O> {
        @Override
        public String type() {
            return "answer";
        }
    }

    private record Call<A, I, O>(ReActTool<I, O> tool, I input) implements Step<A, O> {
        @Override
        public String type() {
            return this.tool.id();
        }

        @Override
        public boolean perform(ReActContext<O> context) throws Exception {
            return context.perform(this.tool, this.input);
        }
    }

    @FunctionalInterface
    public interface Completion<A, R> {
        R finish(A answer);
    }

    @FunctionalInterface
    public interface Fallback<R> {
        R finish(Failure reason);
    }
}
