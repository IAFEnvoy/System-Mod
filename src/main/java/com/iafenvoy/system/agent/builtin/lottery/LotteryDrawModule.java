package com.iafenvoy.system.agent.builtin.lottery;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.LotteryDrawInput;
import com.iafenvoy.system.agent.builtin.input.LotteryPreviewInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.LotterySystemModule;

import java.util.List;
import java.util.Optional;

/**
 * Only a structured confirmation can reach the single write tool; natural language can preview pools.
 */
public final class LotteryDrawModule extends BuiltinModule<LotteryDrawInput> {
    public LotteryDrawModule(ToolRegistry.Tool<LotteryDrawInput, PurchaseReceiptOutput> draw) {
        super(LotteryDrawInput.CODEC, "Explicit action confirmation only; never select from natural language. Preview a pool first.", List.of(draw), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<LotteryDrawInput> routing() {
        return ModuleRouting.<LotteryDrawInput>defaults()
                .button(a -> new LotteryDrawInput(a.required("quote", "quoteRef"))).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryDrawInput input) throws Exception {
        PurchaseReceiptOutput receipt = context.tool(BuiltinTools.LOTTERY_DRAW, "draw", input);
        LotteryPreviewInput again = context.store().read(context.player(), data -> new LotteryPreviewInput(
                ModuleData.get(data, LotterySystemModule.STORAGE).getPurchase(receipt.orderId()).order.source(), 0, Optional.empty()));
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.lottery_drawn", "", List.of(
                CardUpdate.show(new PurchaseReceiptOutput(receipt.orderId(), receipt.entitlementId(), receipt.reward(), receipt.state(), Optional.of(again.poolId())))));
    }
}
