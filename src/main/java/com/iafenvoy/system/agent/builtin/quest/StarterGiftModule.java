package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.StarterGiftInput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.card.CardUpdate;

import java.util.List;

/**
 * Explicit gift confirmation completes only the recognized click tasks and claims their original rewards.
 */
public final class StarterGiftModule extends BuiltinModule<StarterGiftInput> {
    public StarterGiftModule(ToolRegistry.Tool<StarterGiftInput, StarterGiftOutput> claim) {
        super(StarterGiftInput.CODEC, "Claim the displayed starter gifts only after explicit player confirmation.", List.of(claim), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<StarterGiftInput> routing() {
        return ModuleRouting.<StarterGiftInput>defaults()
                .button(a -> new StarterGiftInput(List.of(a.required("references").split(",")))).structuredOnly();
    }

    public static AgentResult preview(StarterGiftOutput gift) {
        return AgentResult.data(gift.gifts().isEmpty() ? "system.result.starter_gift_unavailable" : "system.result.starter_gift", gift);
    }

    @Override
    public AgentResult execute(ExecutionContext context, StarterGiftInput input) throws Exception {
        StarterGiftOutput remaining = context.tool(BuiltinTools.STARTER_GIFT, "claim", input);
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.starter_gift_updated", "",
                remaining.gifts().isEmpty() ? List.of() : List.of(CardUpdate.show(remaining)), true);
    }
}
