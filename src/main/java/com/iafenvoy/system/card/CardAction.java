package com.iafenvoy.system.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Actions describe intent; only the client controller executes local actions or sends server actions.
 */
public sealed interface CardAction permits CardAction.Local, CardAction.Server {
    ResourceLocation id();

    Map<String, String> arguments();

    record Local(ResourceLocation id, Map<String, String> arguments) implements CardAction {
        public Local {
            arguments = Map.copyOf(arguments);
        }
    }

    record Server(ResourceLocation id, Map<String, String> arguments) implements CardAction {
        public static final Codec<Server> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("action").forGetter(Server::id),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("arguments", Map.of()).forGetter(Server::arguments)
        ).apply(i, Server::new));

        public Server {
            arguments = Map.copyOf(arguments);
        }
    }
}
