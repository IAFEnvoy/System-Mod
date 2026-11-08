package com.iafenvoy.system.module.builtin.chat;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.chat.ChatActions;
import com.iafenvoy.system.agent.chat.ChatObservation;
import com.iafenvoy.system.agent.chat.ChatSupport;
import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.react.ReActContext;
import com.iafenvoy.system.agent.react.ReActLoop;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolContext;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.FreeChatInput;
import com.iafenvoy.system.data.output.ChatOutput;
import com.iafenvoy.system.data.store.Message;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A low-confidence fallback shares normal admission, cancellation, history and usage accounting.
 */
public final class FreeChatModule extends AbstractAgentModule<FreeChatInput> {
    private final ChatSupport chat;

    public FreeChatModule(ChatSupport chat) {
        super(FreeChatInput.CODEC, "Free conversation, optionally using enabled workflows in full agent mode.",
                List.of(), Set.of(BuiltinTools.QUEST_GUIDE.id()), true, false,
                new ExecutionPolicy(6, 16, 32, 6, 32768, 2048, 131072, 90000, 30000, 6));
        this.chat = chat;
    }

    @Override
    public ModuleRouting<FreeChatInput> routing() {
        return ModuleRouting.<FreeChatInput>defaults().structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, FreeChatInput input) throws Exception {
        context.requireFreeChat();
        if (input.message().isBlank()) throw new AgentException(AgentFailure.INVALID_INPUT);
        List<Turn> history = context.store().read(context.player(), data -> {
            List<Turn> selected = new ArrayList<>();
            int bytes = 0;
            if (data.saveHistory) for (Message message : data.conversation.history.reversed()) {
                if (message.requestId().equals(context.taskId())) continue;
                Turn turn = switch (message) {
                    case Message.User user -> new Turn("user", user.text());
                    case Message.Assistant assistant -> {
                        String text = assistant.result().cards().stream().flatMap(card -> card.data().stream())
                                .filter(ChatOutput.class::isInstance).map(ChatOutput.class::cast).map(ChatOutput::answer)
                                .findFirst().orElse("");
                        yield new Turn("assistant", text);
                    }
                };
                if (turn.text().isBlank()) continue;
                bytes += turn.text().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > 16384 || selected.size() >= 20) break;
                selected.add(turn);
            }
            return List.copyOf(selected.reversed());
        });
        Input question = new Input(input.message(), history);
        if (context.mode() == ConversationMode.FREE) {
            ToolContext tools = context.chatTools(this.chat.tools(context));
            ReActContext<ChatObservation> loop = new ReActContext<>(context, this.chat.prompt(context),
                    CodecIO.write(Input.CODEC.codec(), question), ChatObservation.codec(tools.tools()), ChatActions.bind(context, tools),
                    () -> {
                        context.requireFreeChat();
                        tools.check();
                    }, ReActLoop.MODEL_FAILURES, 1000);
            return ReActLoop.run(loop, Answer.CODEC, answer -> finish(answer, loop.observations()), failure -> {
                List<CardUpdate> cards = cards(loop.observations());
                cards.addAll(AgentResult.error(failure).cards());
                return new AgentResult(loop.observations().isEmpty() ? AgentResult.Status.FAILED : AgentResult.Status.PARTIALLY_SUCCEEDED,
                        failure.key(), "", cards);
            });
        }
        Answer answer = context.model(this.chat.prompt(context), Input.CODEC, question, Answer.CODEC.codec());
        context.requireFreeChat();
        return finish(answer, List.of());
    }

    private static AgentResult finish(Answer answer, List<ChatObservation> observations) {
        if (answer.text().isBlank()) throw new AgentException(AgentFailure.MODEL_INVALID);
        List<CardUpdate> cards = cards(observations);
        cards.addAll(AgentResult.data("system.result.chat", new ChatOutput(answer.text())).cards());
        AgentResult result = new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.chat", "", cards);
        return observations.stream().anyMatch(value -> value instanceof ChatObservation.Workflow(
                AgentResult result1
        ) && result1.isFailure())
                ? result.partial() : result;
    }

    private static List<CardUpdate> cards(List<ChatObservation> observations) {
        List<CardUpdate> cards = new ArrayList<>();
        for (ChatObservation observation : observations) switch (observation) {
            case ChatObservation.Workflow workflow -> cards.addAll(workflow.result().cards());
            case ChatObservation.Tool tool -> {
                if (tool.result().result() instanceof CardData card) cards.add(CardUpdate.show(card));
            }
        }
        return cards;
    }

    private record Turn(String role, String text) {
        private static final Codec<Turn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(Turn::role), Codec.STRING.fieldOf("text").forGetter(Turn::text)
        ).apply(i, Turn::new));
    }

    private record Input(String message, List<Turn> history) {
        private static final MapCodec<Input> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("message").forGetter(Input::message), Turn.CODEC.listOf().fieldOf("history").forGetter(Input::history)
        ).apply(i, Input::new));
    }

    private record Answer(String text) {
        private static final MapCodec<Answer> CODEC = Codec.STRING.fieldOf("text").xmap(Answer::new, Answer::text);
    }
}
