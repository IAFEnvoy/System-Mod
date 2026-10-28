package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.registry.SystemActionTypes;

import java.util.List;

/**
 * Listing gifts is read-only; claims remain a separate explicit operation.
 */
public final class StarterGiftQueryModule extends BuiltinModule<EmptyInput> {
    public StarterGiftQueryModule(ToolRegistry.Tool<EmptyInput, StarterGiftOutput> query) {
        super(SystemActionTypes.STARTER_GIFTS_ACTION.get(), "List all available starter gift rewards without exposing quest names.",
                EmptyInput.CODEC, List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<EmptyInput> routing() {
        return ModuleRouting.<EmptyInput>defaults()
                .button(_ -> EmptyInput.INSTANCE).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, EmptyInput input) throws Exception {
        return StarterGiftModule.preview(context.tool(BuiltinTools.STARTER_GIFTS, "list", input));
    }
}
