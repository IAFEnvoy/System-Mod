package com.iafenvoy.system.agent.builtin.shop;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.ShopPurchaseInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.registry.SystemActionTypes;

import java.util.List;

/**
 * Executes only an explicitly confirmed shop purchase.
 */
public final class ShopPurchaseModule extends BuiltinModule<ShopPurchaseInput> {
    public ShopPurchaseModule(ToolRegistry.Tool<ShopPurchaseInput, PurchaseReceiptOutput> purchase) {
        super(SystemActionTypes.PURCHASE_ACTION.get(), "Explicit action confirmation only; never select from natural language. Use system:shop_quote to preview.",
                ShopPurchaseInput.CODEC, List.of(purchase), true, false, ExecutionPolicy.quick(1, 1));
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
    public AgentResult execute(ExecutionContext context, ShopPurchaseInput input) throws Exception {
        PurchaseReceiptOutput receipt = context.tool(BuiltinTools.SHOP_PURCHASE, "purchase", input);
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.shop_purchased", "", List.of(CardUpdate.show(receipt)));
    }
}
