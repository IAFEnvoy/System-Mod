package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Actions describe intent; only the client controller executes local actions or sends server actions.
 */
public sealed interface CardAction permits CardAction.Local, CardAction.Server, CardAction.Url {
    record Local(Identifier id, Map<String, String> arguments) implements CardAction {
        public Local {
            arguments = Map.copyOf(arguments);
        }
    }

    record Server(String value) implements CardAction {
        public static final Codec<Server> CODEC = Codec.STRING.xmap(Server::new, Server::value);
    }

    record Url(String value) implements CardAction {
    }
}
