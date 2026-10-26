package com.iafenvoy.system.agent;

import com.google.gson.JsonObject;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Modules are trusted Java extensions. This interface is not a sandbox.
 */
public interface AgentModule<I> {
    Descriptor descriptor();

    TypedPayload.Type<I> inputType();

    List<ToolRegistry.Tool<?, ?>> tools();

    default ModuleRouting<I> routing() {
        return ModuleRouting.defaults();
    }

    AgentResult execute(ExecutionContext context, I input) throws Exception;

    // Descriptions guide routing; tools and entry flags constrain execution, and version guards event resume.
    record Descriptor(Identifier id, int version, String description, JsonObject schema,
                      Set<String> tools, boolean interactive, boolean event, ExecutionPolicy policy) {
        public Descriptor {
            Objects.requireNonNull(id);
            if (version < 1) throw new IllegalArgumentException("Invalid module version");
            tools = Set.copyOf(tools);
            schema = schema.deepCopy();
        }
    }
}
