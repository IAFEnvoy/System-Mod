package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.agent.builtin.input.ShopQuoteInput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Keeps shop browsing and quoting separate from the confirmed payment operation.
 */
public final class ShopModule extends BuiltinModule<ShopQueryInput> {
    public ShopModule() {
        super(ResultTypes.SHOP_ACTION, "Browse the system shop and prices.",
                ShopQueryInput.CODEC, List.of(), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<ShopQueryInput> routing() {
        return ModuleRouting.<ShopQueryInput>defaults()
                .command(AgentCommand.argument("[query]", text -> new ShopQueryInput(text, 0), "shop", "商城"))
                .semantic(RoutingProfile.read(text -> new ShopQueryInput("", 0),
                        List.of("打开系统商城", "看看商店卖什么", "列出所有兑换商品", "查看系统商品和价格"),
                        List.of("Open the system shop", "Show all available offers", "What does the store sell?", "Browse the shop and prices")))
                .shortcut("system.action.view_shop", new ShopQueryInput("", 0));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ShopQueryInput input) {
        String query = input.query().toLowerCase(Locale.ROOT);
        int offset = input.offset();
        CommerceCatalog catalog = context.catalog();
        List<CommerceCatalog.Offer> matches = catalog.offers().stream().filter(offer -> offer.id().contains(query)
                || offer.label().toLowerCase(Locale.ROOT).contains(query)
                || offer.items().stream().anyMatch(item -> item.id().contains(query))).toList();
        List<AgentResult.Card> cards = new ArrayList<>();
        for (CommerceCatalog.Offer offer : matches.stream().skip(offset).limit(8).toList()) {
            boolean available = !offer.price().type().equals("SYSTEM_COIN") || context.coinPayment().available();
            cards.add(new AgentResult.Card("system.result.shop_offer", ResultTypes.SHOP_OFFER.of(new ResultTypes.ShopOffer(Optional.of(offer.id()), offer.label(),
                    offer.items(), offer.price(), Optional.of(available))), available ? List.of(
                    new AgentResult.Action("system.action.shop_quote", ResultTypes.QUOTE_ACTION, new ShopQuoteInput(offer.id(), Optional.of(catalog.revision())))) : List.of()));
        }
        if (matches.size() - offset > 8)
            cards.add(new AgentResult.Card("system.result.page", ResultTypes.EMPTY.of(EmptyInput.INSTANCE), List.of(
                    new AgentResult.Action("system.action.next", ResultTypes.SHOP_ACTION, new ShopQueryInput(query, offset + 8)))));
        return new AgentResult("SUCCEEDED", cards.isEmpty() ? "system.result.shop_empty" : "system.result.shop", "", cards);
    }
}
