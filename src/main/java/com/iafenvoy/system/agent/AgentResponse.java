package com.iafenvoy.system.agent;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Response kinds distinguish replies, notifications and navigation without reserving request IDs.
 */
public interface AgentResponse {
    Codec<AgentResponse> CODEC = Types.REGISTRY.codec();

    DispatchRegistry.Type<? extends AgentResponse> type();

    record Reply(String requestId, AgentResult result) implements AgentResponse {
        @Override
        public DispatchRegistry.Type<Reply> type() {
            return Types.REPLY;
        }
    }

    record Notice(AgentResult result) implements AgentResponse {
        @Override
        public DispatchRegistry.Type<Notice> type() {
            return Types.NOTICE;
        }
    }

    record Open(View view, Optional<String> requestId) implements AgentResponse {
        public Open(View view) {
            this(view, Optional.empty());
        }

        @Override
        public DispatchRegistry.Type<Open> type() {
            return Types.OPEN;
        }
    }

    record Binding(Change change, AgentResult result) implements AgentResponse {
        @Override
        public DispatchRegistry.Type<Binding> type() {
            return Types.BINDING;
        }
    }

    enum View {
        CURRENT, SIMPLE, FULL;
        private static final Map<String, View> VALUES = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));
        public static final Codec<View> CODEC = Codec.stringResolver(Enum::name, VALUES::get);
    }

    enum Change {
        BOUND, REVOKED, INVALIDATED;
        private static final Map<String, Change> VALUES = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));
        public static final Codec<Change> CODEC = Codec.stringResolver(Enum::name, VALUES::get);
    }

    final class Types {
        public static final DispatchRegistry<AgentResponse> REGISTRY = new DispatchRegistry<>("type", AgentResponse::type);
        public static final DispatchRegistry.Type<Reply> REPLY = REGISTRY.register(id("reply"), RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.string(0, 36).fieldOf("requestId").forGetter(Reply::requestId),
                AgentResult.CODEC.fieldOf("result").forGetter(Reply::result)
        ).apply(i, Reply::new)));
        public static final DispatchRegistry.Type<Notice> NOTICE = REGISTRY.register(id("notice"),
                AgentResult.CODEC.fieldOf("result").xmap(Notice::new, Notice::result));
        public static final DispatchRegistry.Type<Open> OPEN = REGISTRY.register(id("open"), RecordCodecBuilder.mapCodec(i -> i.group(
                View.CODEC.fieldOf("view").forGetter(Open::view),
                Codec.string(1, 36).optionalFieldOf("requestId").forGetter(Open::requestId)
        ).apply(i, Open::new)));
        public static final DispatchRegistry.Type<Binding> BINDING = REGISTRY.register(id("binding"), RecordCodecBuilder.mapCodec(i -> i.group(
                Change.CODEC.fieldOf("change").forGetter(Binding::change),
                AgentResult.CODEC.fieldOf("result").forGetter(Binding::result)
        ).apply(i, Binding::new)));

        private Types() {
        }

        private static Identifier id(String path) {
            return Identifier.fromNamespaceAndPath("system", path);
        }
    }
}
