package com.iafenvoy.system.agent.chat;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.tool.ToolObservation;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Collection;
import java.util.Map;

/**
 * Workflow cards and raw typed tool results share one loop without turning evidence into prose or JSON strings.
 */
public sealed interface ChatObservation {
    String kind();

    static Codec<ChatObservation> codec(Collection<ToolRegistry.Tool<?, ?>> tools) {
        Map<String, MapCodec<? extends ChatObservation>> types = Map.of(
                "workflow", Workflow.CODEC,
                "tool", ToolObservation.codec(tools).fieldOf("result").xmap(Tool::new, Tool::result));
        return Codec.STRING.partialDispatch("kind", value -> DataResult.success(value.kind()),
                kind -> types.containsKey(kind) ? DataResult.success(types.get(kind)) : DataResult.error(() -> "Unknown chat observation " + kind));
    }

    record Workflow(AgentResult result) implements ChatObservation {
        public static final MapCodec<Workflow> CODEC = AgentResult.CODEC.fieldOf("result").xmap(Workflow::new, Workflow::result);

        @Override
        public String kind() {
            return "workflow";
        }
    }

    record Tool(ToolObservation<?, ?> result) implements ChatObservation {
        @Override
        public String kind() {
            return "tool";
        }
    }
}
