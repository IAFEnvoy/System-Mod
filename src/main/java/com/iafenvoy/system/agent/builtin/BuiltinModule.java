package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A workflow owns its tool list; the descriptor derives the exact authorization set from that list.
 */
public abstract class BuiltinModule<I> implements AgentModule<I> {
    private final Descriptor descriptor;
    private final TypedPayload.Type<I> inputType;
    private final List<ToolRegistry.Tool<?, ?>> tools;

    protected BuiltinModule(TypedPayload.Type<I> inputType, String description, SchemaMapCodec<I> inputCodec, List<ToolRegistry.Tool<?, ?>> tools, boolean interactive, boolean event, ExecutionPolicy policy) {
        this.inputType = inputType;
        this.tools = List.copyOf(tools);
        this.descriptor = new Descriptor(inputType.id(), 1, description, inputCodec.schema(), this.tools.stream().map(ToolRegistry.Tool::id).collect(Collectors.toSet()), interactive, event, policy);
    }

    @Override
    public final TypedPayload.Type<I> inputType() {
        return this.inputType;
    }

    @Override
    public final Descriptor descriptor() {
        return this.descriptor;
    }

    @Override
    public final List<ToolRegistry.Tool<?, ?>> tools() {
        return this.tools;
    }
}
