package com.iafenvoy.system.agent.store;

import com.iafenvoy.system.agent.AgentResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One stored chat turn. Each role owns its payload type, so callers and the codec exchange real objects instead of
 * stringified JSON; {@code role} remains the serialized discriminator and the only place a new role is added.
 */
public sealed interface Message permits Message.User, Message.Assistant {
    Codec<Message> CODEC = Codec.STRING.partialDispatch("role", message -> DataResult.success(message.role()),
            role -> switch (role) {
                case User.ROLE -> DataResult.<MapCodec<? extends Message>>success(User.CODEC);
                case Assistant.ROLE -> DataResult.<MapCodec<? extends Message>>success(Assistant.CODEC);
                default -> DataResult.error(() -> "Unknown conversation role " + role);
            });

    String requestId();

    long time();

    String role();

    /**
     * The history page strips request IDs so a client cannot replay stored requests.
     */
    Message withRequestId(String requestId);

    record User(String requestId, long time, String text) implements Message {
        static final String ROLE = "user";
        static final MapCodec<User> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("requestId").forGetter(User::requestId),
                Codec.LONG.fieldOf("time").forGetter(User::time),
                Codec.STRING.fieldOf("text").forGetter(User::text)
        ).apply(i, User::new));

        @Override
        public String role() {
            return ROLE;
        }

        @Override
        public Message withRequestId(String requestId) {
            return new User(requestId, this.time, this.text);
        }
    }

    record Assistant(String requestId, long time, AgentResult result) implements Message {
        static final String ROLE = "assistant";
        static final MapCodec<Assistant> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("requestId").forGetter(Assistant::requestId),
                Codec.LONG.fieldOf("time").forGetter(Assistant::time),
                AgentResult.CODEC.fieldOf("result").forGetter(Assistant::result)
        ).apply(i, Assistant::new));

        @Override
        public String role() {
            return ROLE;
        }

        @Override
        public Message withRequestId(String requestId) {
            return new Assistant(requestId, this.time, this.result);
        }
    }
}
