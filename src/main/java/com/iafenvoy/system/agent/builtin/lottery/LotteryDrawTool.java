package com.iafenvoy.system.agent.builtin.lottery;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.LotteryDrawInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceTransactions;
import com.iafenvoy.system.agent.commerce.PurchaseType;

import java.util.concurrent.CompletionStage;

/**
 * One tool settles one confirmed draw; payment and inbox writes share the shop's lifecycle.
 */
public record LotteryDrawTool(
        CommerceTransactions transactions) implements ToolRegistry.Handler<LotteryDrawInput, PurchaseReceiptOutput> {
    public ToolRegistry.Tool<LotteryDrawInput, PurchaseReceiptOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.LOTTERY_DRAW, true, true, false, this);
    }

    @Override
    public CompletionStage<PurchaseReceiptOutput> execute(ExecutionContext context, LotteryDrawInput input) {
        return this.transactions.execute(context, input.quoteRef(), PurchaseType.LOTTERY);
    }
}
