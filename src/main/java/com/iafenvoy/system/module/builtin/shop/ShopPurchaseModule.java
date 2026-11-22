package com.iafenvoy.system.module.builtin.shop;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.input.ShopPurchaseInput;
import com.iafenvoy.system.data.output.PurchaseReceiptOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

/**
 * Executes only an explicitly confirmed shop purchase.
 */
public final class ShopPurchaseModule extends AbstractAgentModule<ShopPurchaseInput> {
    public ShopPurchaseModule(ToolRegistry.Tool<ShopPurchaseInput, PurchaseReceiptOutput, ExecutionContext> purchase) {
        super(ShopPurchaseInput.CODEC, "Explicit action confirmation only; never select from natural language. Use system:shop_quote to preview.", List.of(purchase), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<ShopPurchaseInput> routing() {
        return ModuleRouting.<ShopPurchaseInput>defaults()
                .button(a -> new ShopPurchaseInput(a.required("quote", "quoteRef")))
                .structuredOnly()
                .semantic(RoutingProfile.hints(
                        List.of("确认付款", "现在支付并兑换", "直接扣除材料购买", "确认购买这个商品"),
                        List.of("Confirm payment", "Pay and complete the exchange", "Take the materials and buy it", "Confirm this purchase")));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, ShopPurchaseInput input) throws Exception {
        PurchaseReceiptOutput receipt = context.tool(BuiltinTools.SHOP_PURCHASE, "purchase", input);
        return SystemResults.data(receipt.trade() == ShopTrade.BUYBACK ? "system.result.shop_sold" : "system.result.shop_purchased", receipt);
    }
}
