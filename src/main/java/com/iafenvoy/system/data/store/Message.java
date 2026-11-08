package com.iafenvoy.system.data.store;

import com.iafenvoy.system.agent.AgentResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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

    StreamCodec<RegistryFriendlyByteBuf, Message> STREAM_CODEC = ByteBufCodecs.BYTE.<RegistryFriendlyByteBuf>cast()
            .dispatch(Message::roleId, Message::streamCodec);

    String requestId();

    long time();

    String role();

    byte roleId();

    /**
     * The history page strips request IDs so a client cannot replay stored requests.
     */
    Message withRequestId(String requestId);

    static StreamCodec<? super RegistryFriendlyByteBuf, ? extends Message> streamCodec(byte role) {
        return switch (role) {
            case User.ROLE_ID -> User.STREAM_CODEC;
            case Assistant.ROLE_ID -> Assistant.STREAM_CODEC;
            default -> throw new IllegalArgumentException("Unknown conversation role id " + role);
        };
    }

    record User(String requestId, long time, String text) implements Message {
        static final String ROLE = "user";
        static final byte ROLE_ID = 0;
        static final MapCodec<User> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("requestId").forGetter(User::requestId),
                Codec.LONG.fieldOf("time").forGetter(User::time),
                Codec.STRING.fieldOf("text").forGetter(User::text)
        ).apply(i, User::new));
        static final StreamCodec<ByteBuf, User> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), User::requestId,
                ByteBufCodecs.VAR_LONG, User::time,
                ByteBufCodecs.STRING_UTF8, User::text, User::new);

        @Override
        public String role() {
            return ROLE;
        }

        @Override
        public byte roleId() {
            return ROLE_ID;
        }

        @Override
        public Message withRequestId(String requestId) {
            return new User(requestId, this.time, this.text);
        }
    }

    record Assistant(String requestId, long time, AgentResult result) implements Message {
        static final String ROLE = "assistant";
        static final byte ROLE_ID = 1;
        static final MapCodec<Assistant> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("requestId").forGetter(Assistant::requestId),
                Codec.LONG.fieldOf("time").forGetter(Assistant::time),
                AgentResult.CODEC.fieldOf("result").forGetter(Assistant::result)
        ).apply(i, Assistant::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Assistant> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), Assistant::requestId,
                ByteBufCodecs.VAR_LONG, Assistant::time,
                ByteBufCodecs.fromCodecWithRegistriesTrusted(AgentResult.CODEC), Assistant::result, Assistant::new);

        @Override
        public String role() {
            return ROLE;
        }

        @Override
        public byte roleId() {
            return ROLE_ID;
        }

        @Override
        public Message withRequestId(String requestId) {
            return new Assistant(requestId, this.time, this.result);
        }
    }
}
