package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.StarterGiftsInput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;

import java.util.List;

/**
 * Listing gifts is read-only; claims remain a separate explicit operation.
 */
public final class StarterGiftQueryModule extends BuiltinModule<StarterGiftsInput> {
    public StarterGiftQueryModule(ToolRegistry.Tool<EmptyInput, StarterGiftOutput> query) {
        super(StarterGiftsInput.CODEC, "List all available starter gift rewards without exposing quest names.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<StarterGiftsInput> routing() {
        return ModuleRouting.<StarterGiftsInput>defaults()
                .button(unused -> StarterGiftsInput.INSTANCE).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, StarterGiftsInput input) throws Exception {
        return StarterGiftModule.preview(context.tool(BuiltinTools.STARTER_GIFTS, "list", EmptyInput.INSTANCE));
    }
}
