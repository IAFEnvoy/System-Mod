package com.iafenvoy.system.agent;

import com.google.gson.JsonObject;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Modules are trusted Java extensions. This interface is not a sandbox.
 */
public interface AgentModule<I extends AgentAction> {
    Descriptor descriptor();

    MapCodec<I> inputCodec();

    List<ToolRegistry.Tool<?, ?>> tools();

    default ModuleRouting<I> routing() {
        return ModuleRouting.defaults();
    }

    AgentResult execute(ExecutionContext context, I input) throws Exception;

    // Descriptions guide routing; tools and entry flags constrain execution, and version guards event resume.
    record Descriptor(ResourceLocation id, int version, String description, JsonObject schema,
                      Set<String> tools, Set<String> optionalTools, boolean interactive, boolean event,
                      ExecutionPolicy policy) {
        public Descriptor {
            Objects.requireNonNull(id);
            if (version < 1) throw new IllegalArgumentException("Invalid module version");
            tools = Set.copyOf(tools);
            optionalTools = Set.copyOf(optionalTools);
            if (tools.stream().anyMatch(optionalTools::contains))
                throw new IllegalArgumentException("A tool cannot be both required and optional");
            schema = schema.deepCopy();
        }
    }
}
