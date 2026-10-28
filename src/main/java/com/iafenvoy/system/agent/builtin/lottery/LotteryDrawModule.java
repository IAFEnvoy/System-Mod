package com.iafenvoy.system.agent.builtin.lottery;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.input.LotteryDrawInput;
import com.iafenvoy.system.agent.builtin.input.LotteryPreviewInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.module.lottery.LotterySystemModule;
import com.iafenvoy.system.module.ModuleData;

import java.util.List;
import java.util.Optional;

/**
 * Only a structured confirmation can reach the single write tool; natural language can preview pools.
 */
public final class LotteryDrawModule extends BuiltinModule<LotteryDrawInput> {
    public LotteryDrawModule(ToolRegistry.Tool<LotteryDrawInput, PurchaseReceiptOutput> draw) {
        super(ResultTypes.LOTTERY_DRAW_ACTION, "Explicit action confirmation only; never select from natural language. Preview a pool first.",
                LotteryDrawInput.CODEC, List.of(draw), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<LotteryDrawInput> routing() {
        return ModuleRouting.<LotteryDrawInput>defaults().structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryDrawInput input) throws Exception {
        PurchaseReceiptOutput receipt = context.tool(BuiltinTools.LOTTERY_DRAW, "draw", input);
        LotteryPreviewInput again = context.store().read(context.player(), data -> new LotteryPreviewInput(
                ModuleData.get(data, LotterySystemModule.STORAGE).purchases.get(receipt.orderId()).order.source(), 0, Optional.empty()));
        return new AgentResult("SUCCEEDED", "system.result.lottery_drawn", "", List.of(
                new AgentResult.Card("system.result.reward", ResultTypes.PURCHASE.of(receipt), List.of(
                        new AgentResult.Action("system.action.deliver", ResultTypes.DELIVER_ACTION, new EntitlementInput(receipt.entitlementId())),
                        new AgentResult.Action("system.action.lottery_again", ResultTypes.LOTTERY_PREVIEW_ACTION, again)))));
    }
}
