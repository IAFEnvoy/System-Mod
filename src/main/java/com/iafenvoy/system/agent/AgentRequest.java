package com.iafenvoy.system.agent;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Requests carry one registered type from the client through the network to the server handler.
 */
public interface AgentRequest {
    Codec<AgentRequest> CODEC = Types.REGISTRY.codec();

    DispatchRegistry.Type<? extends AgentRequest> type();

    String text();

    record Chat(String text) implements AgentRequest {
        @Override
        public DispatchRegistry.Type<Chat> type() {
            return Types.CHAT;
        }
    }

    record Invoke(TypedPayload<?> call, String text) implements AgentRequest {
        @Override
        public DispatchRegistry.Type<Invoke> type() {
            return Types.INVOKE;
        }
    }

    enum Control implements AgentRequest {
        STATE, HISTORY, FORGET, OPEN;
        private static final Map<String, Control> VALUES = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));
        public static final Codec<Control> CODEC = Codec.stringResolver(Enum::name, VALUES::get);

        @Override
        public DispatchRegistry.Type<Control> type() {
            return Types.CONTROL;
        }

        @Override
        public String text() {
            return "";
        }
    }

    record Preferences(boolean history, boolean external) implements AgentRequest {
        @Override
        public DispatchRegistry.Type<Preferences> type() {
            return Types.PREFERENCES;
        }

        @Override
        public String text() {
            return "";
        }
    }

    final class Types {
        public static final DispatchRegistry<AgentRequest> REGISTRY = new DispatchRegistry<>("type", AgentRequest::type);
        public static final DispatchRegistry.Type<Chat> CHAT = REGISTRY.register(id("chat"),
                Codec.string(0, 2000).fieldOf("text").xmap(Chat::new, Chat::text));
        public static final DispatchRegistry.Type<Invoke> INVOKE = REGISTRY.register(id("invoke"), RecordCodecBuilder.mapCodec(i -> i.group(
                ResultTypes.ACTIONS.codec().fieldOf("call").forGetter(Invoke::call),
                Codec.string(0, 2000).optionalFieldOf("text", "").forGetter(Invoke::text)
        ).apply(i, Invoke::new)));
        public static final DispatchRegistry.Type<Control> CONTROL = REGISTRY.register(id("control"), Control.CODEC.fieldOf("action"));
        public static final DispatchRegistry.Type<Preferences> PREFERENCES = REGISTRY.register(id("preferences"), RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("history").forGetter(Preferences::history),
                Codec.BOOL.fieldOf("external").forGetter(Preferences::external)
        ).apply(i, Preferences::new)));

        private Types() {
        }

        private static Identifier id(String path) {
            return Identifier.fromNamespaceAndPath("system", path);
        }
    }
}
