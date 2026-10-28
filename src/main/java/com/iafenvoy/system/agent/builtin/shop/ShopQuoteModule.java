package com.iafenvoy.system.agent.builtin.shop;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.input.ShopPurchaseInput;
import com.iafenvoy.system.agent.builtin.input.ShopQuoteInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.registry.SystemActionTypes;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Keeps shop browsing and quoting separate from the confirmed payment operation.
 */
public final class ShopQuoteModule extends BuiltinModule<ShopQuoteInput> {
    public ShopQuoteModule(ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(SystemActionTypes.QUOTE_ACTION.get(), "Preview ONE shop purchase; player must then explicitly select the returned confirmation action.",
                ShopQuoteInput.CODEC, List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<ShopQuoteInput> routing() {
        return ModuleRouting.<ShopQuoteInput>defaults()
                .command(AgentCommand.argument("<offer_id>", text -> new ShopQuoteInput(CommandArguments.identifier(text), Optional.empty()), "buy", "兑换"))
                .semantic(RoutingProfile.hints(
                        List.of("我要兑换火把", "购买这个商品", "查看兑换这个的价格", "拿圆石换一些火把"),
                        List.of("I want to exchange for torches", "Buy this shop offer", "Preview this purchase", "Trade cobblestone for torches")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ShopQuoteInput input) throws Exception {
        CommerceCatalog catalog = context.catalog();
        if (!input.catalogRevision().orElse(catalog.revision()).equals(catalog.revision()))
            throw new AgentException("STALE_QUOTE");
        Identifier id = input.offerId();
        CommerceCatalog.Offer offer = catalog.offers().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new AgentException("STALE_QUOTE"));
        CommercePreviewOutput price = context.tool(BuiltinTools.COMMERCE_PREVIEW, "quote",
                new CommercePreviewInput(List.of(id), List.of(), true, catalog.revision())).getFirst();
        if (!price.available()) throw new AgentException("INVALID_COST");
        return new AgentResult("SUCCEEDED", "system.result.shop_confirm", "", List.of(new AgentResult.Card("system.result.shop_offer",
                new ResultTypes.ShopOffer(Optional.empty(), offer.label(), offer.items(), price.price(), Optional.empty()), List.of(
                new AgentResult.Action("system.action.shop_confirm", SystemActionTypes.PURCHASE_ACTION.get(), new ShopPurchaseInput(price.quoteRef().orElseThrow()))))));
    }
}
