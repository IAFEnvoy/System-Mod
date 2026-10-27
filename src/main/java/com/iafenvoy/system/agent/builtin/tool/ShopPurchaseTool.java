package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.ShopPurchaseInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.commerce.CommerceTransactions;
import com.iafenvoy.system.agent.commerce.PurchaseType;

import java.util.concurrent.CompletionStage;

/**
 * One tool confirms one shop purchase through the shared transaction journal.
 */
public record ShopPurchaseTool(
        CommerceTransactions transactions) implements ToolRegistry.Handler<ShopPurchaseInput, PurchaseReceiptOutput> {
    public ToolRegistry.Tool<ShopPurchaseInput, PurchaseReceiptOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.SHOP_PURCHASE, true, true, false, this);
    }

    @Override
    public CompletionStage<PurchaseReceiptOutput> execute(ExecutionContext context, ShopPurchaseInput input) {
        return this.transactions.execute(context, input.quoteRef(), PurchaseType.SHOP);
    }
}
