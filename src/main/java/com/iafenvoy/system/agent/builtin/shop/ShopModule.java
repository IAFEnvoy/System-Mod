package com.iafenvoy.system.agent.builtin.shop;

import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.builtin.output.ShopCatalogOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;
import java.util.Locale;

/**
 * Keeps shop browsing and quoting separate: the shelf lists offers without pricing them, and one cell click
 * opens the server-owned quote.
 */
public final class ShopModule extends BuiltinModule<ShopQueryInput> {
    private static final int MAX_OFFERS = 128;

    public ShopModule() {
        super(ShopQueryInput.CODEC, "Browse the system shop and prices.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
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
    public AgentResult execute(ExecutionContext context, ShopQueryInput input) {
        String query = input.query().toLowerCase(Locale.ROOT);
        CommerceCatalog catalog = context.availableCatalog();
        List<CommerceCatalog.Offer> matches = catalog.offers().stream().filter(offer -> offer.id().toString().contains(query)
                || offer.label().toLowerCase(Locale.ROOT).contains(query)
                || offer.items().stream().anyMatch(item -> item.item().getRegisteredName().contains(query))).toList();
        // Prices are evaluated only for the offer the player actually opens.
        List<ShopCatalogOutput.Offer> offers = matches.stream().limit(MAX_OFFERS)
                .map(offer -> new ShopCatalogOutput.Offer(offer.id().toString(), offer.label(),
                        offer.items().stream().map(ItemPreviewOutput::of).toList())).toList();
        return AgentResult.data("system.result.shop",
                new ShopCatalogOutput(catalog.revision(), query, offers, matches.size() > MAX_OFFERS));
    }
}
