package com.iafenvoy.system.module.builtin.chat;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.chat.ChatSupport;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.data.input.FreeChatInput;
import com.iafenvoy.system.data.output.ChatOutput;
import com.iafenvoy.system.data.store.Message;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A low-confidence fallback shares normal admission, cancellation, history and usage accounting.
 */
public final class FreeChatModule extends AbstractAgentModule<FreeChatInput> {
    private final ChatSupport chat;

    public FreeChatModule(ChatSupport chat) {
        super(FreeChatInput.CODEC, "Free conversation when no local capability confidently matches. No tools are available yet.",
                List.of(), true, false, new ExecutionPolicy(1, 0, 3, 0, 32768, 2048, 65536, 60000, 45000, 0));
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
        Answer answer = context.model(this.chat.prompt(context), Input.CODEC, new Input(input.message(), history), Answer.CODEC.codec());
        context.requireFreeChat();
        if (answer.text().isBlank()) throw new AgentException(AgentFailure.MODEL_INVALID);
        return AgentResult.data("system.result.chat", new ChatOutput(answer.text()));
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
