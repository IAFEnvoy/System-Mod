package com.iafenvoy.system.module.builtin.lottery;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.LotteryDrawInput;
import com.iafenvoy.system.data.output.PurchaseReceiptOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

/**
 * Only a structured confirmation can reach the single write tool; natural language can preview pools.
 */
public final class LotteryDrawModule extends AbstractAgentModule<LotteryDrawInput> {
    public LotteryDrawModule(ToolRegistry.Tool<LotteryDrawInput, PurchaseReceiptOutput, ExecutionContext> draw) {
        super(LotteryDrawInput.CODEC, "Explicit action confirmation only; never select from natural language. Preview a pool first.", List.of(draw), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<LotteryDrawInput> routing() {
        return ModuleRouting.<LotteryDrawInput>defaults()
                .button(a -> new LotteryDrawInput(a.required("quote", "quoteRef"))).structuredOnly();
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, LotteryDrawInput input) throws Exception {
        PurchaseReceiptOutput receipt = context.tool(BuiltinTools.LOTTERY_DRAW, "draw", input);
        return SystemResults.data("system.result.lottery_drawn", receipt);
    }
}
