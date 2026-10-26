package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.input.ShopPurchaseInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;

/**
 * Executes only an explicitly confirmed shop purchase.
 */
public final class ShopPurchaseModule extends BuiltinModule<ShopPurchaseInput> {
    public ShopPurchaseModule(ToolRegistry.Tool<ShopPurchaseInput, PurchaseReceiptOutput> purchase) {
        super(ResultTypes.PURCHASE_ACTION, "Explicit action confirmation only; never select from natural language. Use system:shop_quote to preview.",
                ShopPurchaseInput.CODEC, List.of(purchase), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<ShopPurchaseInput> routing() {
        return ModuleRouting.<ShopPurchaseInput>defaults()
                .structuredOnly()
                .semantic(RoutingProfile.hints(
                        List.of("确认付款", "现在支付并兑换", "直接扣除材料购买", "确认购买这个商品"),
                        List.of("Confirm payment", "Pay and complete the exchange", "Take the materials and buy it", "Confirm this purchase")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ShopPurchaseInput input) throws Exception {
        PurchaseReceiptOutput receipt = context.tool(BuiltinTools.SHOP_PURCHASE, "purchase", input);
        return new AgentResult("SUCCEEDED", "system.result.shop_purchased", "", List.of(new AgentResult.Card("system.result.reward", ResultTypes.PURCHASE.of(receipt), List.of(
                new AgentResult.Action("system.action.deliver", ResultTypes.DELIVER_ACTION, new EntitlementInput(receipt.entitlementId()))))));
    }
}
