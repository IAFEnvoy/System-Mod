package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Actions describe intent; only the client controller executes local actions or sends server actions.
 */
public sealed interface CardAction permits CardAction.Local, CardAction.Server {
    Identifier id();

    Map<String, String> arguments();

    record Local(Identifier id, Map<String, String> arguments) implements CardAction {
        public Local {
            arguments = Map.copyOf(arguments);
        }
    }

    record Server(Identifier id, Map<String, String> arguments) implements CardAction {
        public static final Codec<Server> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("action").forGetter(Server::id),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("arguments", Map.of()).forGetter(Server::arguments)
        ).apply(i, Server::new));
        public static final StreamCodec<ByteBuf, Server> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Server::id,
                ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(HashMap::new,
                        ByteBufCodecs.stringUtf8(512), ByteBufCodecs.stringUtf8(8192), 64),
                Server::arguments, Server::new);

        public Server {
            arguments = Map.copyOf(arguments);
        }
    }
}
