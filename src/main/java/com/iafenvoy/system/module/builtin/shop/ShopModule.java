package com.iafenvoy.system.module.builtin.shop;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.input.ShopQueryInput;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.ShopCatalogOutput;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Locale;

/**
 * Keeps shop browsing and quoting separate: the shelf lists offers without pricing them, and one cell click
 * opens the server-owned quote.
 */
public final class ShopModule extends AbstractAgentModule<ShopQueryInput> {
    private final CommerceCatalogs catalogs;
    private static final int MAX_OFFERS = 128;

    public ShopModule(CommerceCatalogs catalogs) {
        super(ShopQueryInput.CODEC, "Browse the system shop and prices.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
        this.catalogs = catalogs;
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
        CommerceCatalog catalog = this.catalogs.available(context.player());
        List<Holder<ShopOfferEntry>> matches = catalog.offers().stream().filter(holder -> HolderHelper.id(holder).toString().contains(query)
                || holder.value().label().getString().toLowerCase(Locale.ROOT).contains(query)
                || holder.value().items().stream().anyMatch(item -> item.item().getRegisteredName().contains(query))).toList();
        // Prices are evaluated only for the offer the player actually opens.
        List<ShopCatalogOutput.Offer> offers = matches.stream().limit(MAX_OFFERS)
                .map(holder -> new ShopCatalogOutput.Offer(HolderHelper.id(holder).toString(), holder.value().label(),
                        holder.value().items().stream().map(ItemPreviewOutput::of).toList())).toList();
        return AgentResult.data("system.result.shop",
                new ShopCatalogOutput(catalog.revision(), query, offers, matches.size() > MAX_OFFERS));
    }
}
