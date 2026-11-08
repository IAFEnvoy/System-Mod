package com.iafenvoy.system.module.builtin.knowledge;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.react.ReActContext;
import com.iafenvoy.system.agent.react.ReActLoop;
import com.iafenvoy.system.agent.react.ReActTool;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolContext;
import com.iafenvoy.system.agent.tool.ToolObservation;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.agent.tool.ToolPriority;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.data.TextCardData;
import com.iafenvoy.system.data.input.ReasoningInput;
import com.iafenvoy.system.data.output.DatabaseQueryOutput;
import com.iafenvoy.system.data.output.KnowledgeAnswerOutput;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
    public AgentResult execute(ExecutionContext context, ReasoningInput input) throws Exception {
        ToolContext tools = context.reasoningTools();
        List<CardData> results = new ArrayList<>();
        List<ReActTool<?, ToolObservation<?, ?>>> bindings = new ArrayList<>();
        for (ToolRegistry.Tool<?, ?> tool : tools.tools()) bindings.add(binding(tools, tool, results));
        Set<Failure> recoverable = new HashSet<>(ReActLoop.MODEL_FAILURES);
        recoverable.addAll(Set.of(AgentFailure.TOOL_FAILED, AgentFailure.INVALID_INPUT, AgentFailure.QUEST_UNAVAILABLE, AgentFailure.UNKNOWN_ITEM));
        ReActContext<ToolObservation<?, ?>> loop = new ReActContext<>(context, context.prompt(SystemPrompts.REACT), input.request(),
                ToolObservation.codec(tools.tools()), bindings, tools::check, recoverable, 1000);
        return ReActLoop.run(loop, Answer.CODEC, answer -> {
            if (answer.text().isBlank()) throw new AgentException(AgentFailure.MODEL_INVALID);
            return AgentResult.data("system.result.reasoning", new KnowledgeAnswerOutput(answer.text(), List.of()));
        }, reason -> {
            SystemMod.LOGGER.warn("Reasoning incomplete: reason={}, iterations={}, modelCalls={}, remainingMillis={}",
                    reason, loop.iterations(), context.modelCalls(), context.remainingMillis());
            List<CardData> cards = new ArrayList<>();
            cards.add(new TextCardData(Component.translatable("system.result.reasoning_incomplete")));
            cards.addAll(results);
            return AgentResult.of("system.result.reasoning", cards.toArray(CardData[]::new)).partial();
        });
    }

    private static <I, O> ReActTool<I, ToolObservation<?, ?>> binding(ToolContext context, ToolRegistry.Tool<I, O> tool, List<CardData> results) {
        SchemaMapCodec<I> codec = (SchemaMapCodec<I>) tool.type().inputCodec();
        return new ReActTool<>(tool.id(), tool.reasoningDescription(), codec, input -> {
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
        static final MapCodec<Answer> CODEC = Codec.STRING.fieldOf("text").xmap(Answer::new, Answer::text);
    }
}
