package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.registry.SystemRegistries;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A workflow owns its tool list; the descriptor derives the exact authorization set from that list.
 */
public abstract class BuiltinModule<I extends AgentAction> implements AgentModule<I> {
    private final Descriptor descriptor;
    private final SchemaMapCodec<I> inputCodec;
    private final List<ToolRegistry.Tool<?, ?>> tools;

    protected BuiltinModule(SchemaMapCodec<I> inputCodec, String description, List<ToolRegistry.Tool<?, ?>> tools, boolean interactive, boolean event, ExecutionPolicy policy) {
        this.inputCodec = inputCodec;
        this.tools = List.copyOf(tools);
        this.descriptor = new Descriptor(Objects.requireNonNull(SystemRegistries.ACTION_TYPE.getKey(inputCodec), "Unregistered Agent action codec"), 1, description, inputCodec.schema(), this.tools.stream().map(ToolRegistry.Tool::id).collect(Collectors.toSet()), interactive, event, policy);
    }

    @Override
    public final SchemaMapCodec<I> inputCodec() {
        return this.inputCodec;
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
