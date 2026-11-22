package com.iafenvoy.system.module.builtin.knowledge;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.react.ReActContext;
import com.iafenvoy.assistant.react.ReActTool;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolContext;
import com.iafenvoy.assistant.tool.ToolObservation;
import com.iafenvoy.assistant.tool.ToolPriority;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.react.SystemReAct;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.data.TextCardData;
import com.iafenvoy.system.data.input.ReasoningInput;
import com.iafenvoy.system.data.output.DatabaseQueryOutput;
import com.iafenvoy.system.data.output.KnowledgeAnswerOutput;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-capability local reads use the shared loop; simple commands and knowledge questions keep their fast paths.
 */
public final class ReasoningModule extends AbstractAgentModule<ReasoningInput> {
    public ReasoningModule() {
        super(ReasoningInput.CODEC, "Combine multiple local read operations to answer the current request, such as finding a quest, "
                        + "reading its requirements and looking up item sources. Use ONLY when several capabilities must be combined. "
                        + "Prefer dedicated capabilities for single operations and system:question for knowledge questions. "
                        + "Cannot search the web, submit, claim, sign in, pay, draw or change game state.",
                List.of(), Set.of(BuiltinTools.QUEST_GUIDE.id()), true, false,
                new ExecutionPolicy(4, 8, 14, 0, 65536, 1536, 262144, 60000, 12000, 3));
    }

    @Override
    public ModuleRouting<ReasoningInput> routing() {
        return ModuleRouting.<ReasoningInput>defaults()
                .priority(ToolPriority.PAID)
                .command(AgentCommand.argument("<request>", ReasoningInput::new, "reason", "分析"));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, ReasoningInput input) throws Exception {
        ToolContext<ExecutionContext> tools = context.reasoningTools();
        List<CardData> results = new ArrayList<>();
        List<ReActTool<?, ToolObservation<?, ?>>> bindings = new ArrayList<>();
        for (ToolRegistry.Tool<?, ?, ExecutionContext> tool : tools.tools())
            bindings.add(binding(tools, tool, results));
        Set<Failure> recoverable = new HashSet<>(SystemReAct.MODEL_FAILURES);
        recoverable.addAll(Set.of(AgentFailure.TOOL_FAILED, AgentFailure.INVALID_INPUT, AgentFailure.QUEST_UNAVAILABLE, AgentFailure.UNKNOWN_ITEM));
        ReActContext<ToolObservation<?, ?>> loop = SystemReAct.context(context, context.prompt(SystemPrompts.REACT), input.request(),
                ToolObservation.codec(tools.tools()), bindings, tools::check, 1000);
        return SystemReAct.run(loop, Answer.CODEC, recoverable, answer -> {
            if (answer.text().isBlank()) throw new AgentException(AgentFailure.MODEL_INVALID);
            return SystemResults.data("system.result.reasoning", new KnowledgeAnswerOutput(answer.text(), List.of()));
        }, reason -> {
            SystemMod.LOGGER.warn("Reasoning incomplete: reason={}, iterations={}, modelCalls={}, remainingMillis={}",
                    reason, loop.iterations(), context.modelCalls(), context.remainingMillis());
            List<CardData> cards = new ArrayList<>();
            cards.add(new TextCardData(Component.translatable("system.result.reasoning_incomplete")));
            cards.addAll(results);
            return SystemResults.of("system.result.reasoning", cards.toArray(CardData[]::new)).partial();
        });
    }

    private static <I, O> ReActTool<I, ToolObservation<?, ?>> binding(ToolContext<ExecutionContext> context, ToolRegistry.Tool<I, O, ExecutionContext> tool, List<CardData> results) {
        SchemaMapCodec<I> codec = (SchemaMapCodec<I>) tool.type().inputCodec();
        return new ReActTool<>(tool.id(), tool.reasoningDescription(), codec, codec.schema(), input -> {
            O result = context.call(tool.type(), input);
            if (result instanceof CardData card) results.add(card);
            else if (result instanceof DatabaseQueryOutput(
                    List<DatabaseQueryOutput.Source> sources
            ) && !sources.isEmpty())
                results.add(new KnowledgeAnswerOutput("", sources));
            return new ToolObservation<>(tool.type(), input, result);
        });
    }

    private record Answer(String text) {
        static final SchemaMapCodec<Answer> CODEC = SchemaCodecs.record((i, f) -> i.group(
                f.required("text", SchemaCodecs.STRING).forGetter(Answer::text)
        ).apply(i, Answer::new));
    }
}
