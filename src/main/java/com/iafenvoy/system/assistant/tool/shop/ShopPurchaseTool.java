package com.iafenvoy.system.assistant.tool.shop;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.data.input.ShopPurchaseInput;
import com.iafenvoy.system.data.output.PurchaseReceiptOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.builtin.BuiltinTools;

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
