package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.StarterGiftInput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.registry.SystemActionTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit gift confirmation completes only the recognized click tasks and claims their original rewards.
 */
public final class StarterGiftModule extends BuiltinModule<StarterGiftInput> {
    public StarterGiftModule(ToolRegistry.Tool<StarterGiftInput, StarterGiftOutput> claim) {
        super(SystemActionTypes.STARTER_GIFT_ACTION.get(), "Claim the displayed starter gifts only after explicit player confirmation.",
                StarterGiftInput.CODEC, List.of(claim), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<StarterGiftInput> routing() {
        return ModuleRouting.<StarterGiftInput>defaults().structuredOnly();
    }

    public static AgentResult preview(StarterGiftOutput gift) {
        List<AgentResult.Action> actions = new ArrayList<>();
        List<String> references = gift.gifts().stream().flatMap(entry -> entry.claimRef().stream()).toList();
        if (!references.isEmpty()) actions.add(new AgentResult.Action("system.action.claim_starter_gift",
                SystemActionTypes.STARTER_GIFT_ACTION.get(), new StarterGiftInput(references)));
        actions.add(new AgentResult.Action("system.action.refresh_starter_gift", SystemActionTypes.STARTER_GIFTS_ACTION.get(), EmptyInput.INSTANCE));
        String key = gift.gifts().isEmpty() ? "system.result.starter_gift_unavailable" : "system.result.starter_gift";
        return new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key, gift, actions)));
    }

    @Override
    public AgentResult execute(ExecutionContext context, StarterGiftInput input) throws Exception {
        AgentResult remaining = preview(context.tool(BuiltinTools.STARTER_GIFT, "claim", input));
        return remaining.withPresentation("system.result.starter_gift_updated", "", remaining.cards(), true);
    }
}
