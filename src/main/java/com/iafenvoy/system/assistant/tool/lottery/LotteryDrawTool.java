package com.iafenvoy.system.assistant.tool.lottery;

import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.data.input.LotteryDrawInput;
import com.iafenvoy.system.data.output.PurchaseReceiptOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletionStage;

/**
 * One tool settles a confirmed draw batch; payment and space writes share the shop's lifecycle.
 */
public record LotteryDrawTool(
        CommerceTransactions transactions) implements ToolRegistry.Handler<LotteryDrawInput, PurchaseReceiptOutput, ExecutionContext> {
    public ToolRegistry.Tool<LotteryDrawInput, PurchaseReceiptOutput, ExecutionContext> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.LOTTERY_DRAW, true, true, false, this);
    }

    @Override
    public CompletionStage<PurchaseReceiptOutput> execute(ExecutionContext context, LotteryDrawInput input) {
        return this.transactions.execute(context, input.quoteRef(), PurchaseType.LOTTERY);
    }
}
