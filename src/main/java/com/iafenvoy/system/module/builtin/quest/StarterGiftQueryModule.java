package com.iafenvoy.system.module.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.input.StarterGiftsInput;
import com.iafenvoy.system.data.output.StarterGiftOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

/**
 * Listing gifts is read-only; claims remain a separate explicit operation.
 */
public final class StarterGiftQueryModule extends AbstractAgentModule<StarterGiftsInput> {
    public StarterGiftQueryModule(ToolRegistry.Tool<EmptyInput, StarterGiftOutput> query) {
        super(StarterGiftsInput.CODEC, "List all available starter gift rewards without exposing quest names.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<StarterGiftsInput> routing() {
        return ModuleRouting.<StarterGiftsInput>defaults()
                .button(_ -> StarterGiftsInput.INSTANCE).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, StarterGiftsInput input) throws Exception {
        return StarterGiftModule.preview(context.tool(BuiltinTools.STARTER_GIFTS, "list", EmptyInput.INSTANCE));
    }
}
