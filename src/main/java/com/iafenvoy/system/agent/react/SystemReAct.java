package com.iafenvoy.system.agent.react;

import com.iafenvoy.assistant.AssistantException;
import com.iafenvoy.assistant.react.ReActContext;
import com.iafenvoy.assistant.react.ReActLoop;
import com.iafenvoy.assistant.react.ReActTool;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Connects the shared loop to System prompts and Saturn failure policy.
 */
public final class SystemReAct {
    public static final Set<Failure> MODEL_FAILURES = Set.of(AgentFailure.BUDGET_EXCEEDED, AgentFailure.MODEL_INVALID,
            AgentFailure.MODEL_TIMEOUT, AgentFailure.MODEL_UNAVAILABLE, AgentFailure.RATE_LIMITED, AgentFailure.BUSY);
    private static final Set<Failure> TERMINAL_FAILURES = Set.of(AgentFailure.BUDGET_EXCEEDED, AgentFailure.RATE_LIMITED, AgentFailure.BUSY);

    private SystemReAct() {
    }

    public static <O> ReActContext<O> context(ExecutionContext execution, String instruction, String question,
                                              Codec<O> observations, List<ReActTool<?, O>> tools, Runnable guard, long toolReserveMillis) {
        return new ReActContext<>(execution, instruction + "\n\n" + execution.prompt(SystemPrompts.TOOL_CALL),
                question, observations, tools, guard, toolReserveMillis);
    }

    public static <A, O, R> R run(ReActContext<O> context, SchemaMapCodec<A> answer, Set<? extends Failure> recoverable,
                                  Function<A, R> completion, Function<Failure, R> fallback) throws Exception {
        Set<Failure> failures = Set.copyOf(recoverable);
        return ReActLoop.run(context, answer, answer.schema(), AgentFailure.ITERATION_LIMIT,
                error -> {
                    if (!(error instanceof AgentException || error instanceof AssistantException)) return null;
                    Failure failure = AgentException.failure(error);
                    return failures.contains(failure) ? failure : null;
                },
                TERMINAL_FAILURES::contains, completion, fallback);
    }
}
