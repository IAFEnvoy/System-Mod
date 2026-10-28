package com.iafenvoy.system.agent.builtin.shop;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.output.ShopOfferOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.data.SelectionCardData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps shop browsing and quoting separate from the confirmed payment operation.
 */
public final class ShopModule extends BuiltinModule<ShopQueryInput> {
    public ShopModule(ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(ShopQueryInput.CODEC, "Browse the system shop and prices.", List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<ShopQueryInput> routing() {
        return ModuleRouting.<ShopQueryInput>defaults()
                .button(a -> new ShopQueryInput(a.text("query"), a.number(0, "offset")))
                .command(AgentCommand.argument("[query]", text -> new ShopQueryInput(text, 0), "shop", "商城"))
                .semantic(RoutingProfile.read(text -> new ShopQueryInput("", 0),
                        List.of("打开系统商城", "看看商店卖什么", "列出所有兑换商品", "查看系统商品和价格"),
                        List.of("Open the system shop", "Show all available offers", "What does the store sell?", "Browse the shop and prices")))
                .shortcut("system.action.view_shop", new ShopQueryInput("", 0));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ShopQueryInput input) throws Exception {
        String query = input.query().toLowerCase(Locale.ROOT);
        int offset = input.offset();
        CommerceCatalog catalog = context.catalog();
        List<CommerceCatalog.Offer> matches = catalog.offers().stream().filter(offer -> offer.id().toString().contains(query)
                || offer.label().toLowerCase(Locale.ROOT).contains(query)
                || offer.items().stream().anyMatch(item -> item.item().getRegisteredName().contains(query))).toList();
        List<CardUpdate> cards = new ArrayList<>();
        List<CommerceCatalog.Offer> page = matches.stream().skip(offset).limit(8).toList();
        List<CommercePreviewOutput> prices = page.isEmpty() ? List.of() : context.tool(BuiltinTools.COMMERCE_PREVIEW, "prices",
                new CommercePreviewInput(page.stream().map(CommerceCatalog.Offer::id).toList(), List.of(), false, catalog.revision()));
        for (int index = 0; index < page.size(); index++) {
            CommerceCatalog.Offer offer = page.get(index);
            CommercePreviewOutput price = prices.get(index);
            boolean available = price.available();
            cards.add(CardUpdate.show(new ShopOfferOutput(offer.id(), offer.label(),
                    offer.items(), price.price(), available, catalog.revision())));
        }
        if (matches.size() - offset > 8)
            cards.add(CardUpdate.show(SelectionCardData.fromActions(List.of(
                    new AgentResult.Action("system.action.next", new ShopQueryInput(query, offset + 8))))));
        return new AgentResult(AgentResult.Status.SUCCEEDED, cards.isEmpty() ? "system.result.shop_empty" : "system.result.shop", "", cards);
    }
}
