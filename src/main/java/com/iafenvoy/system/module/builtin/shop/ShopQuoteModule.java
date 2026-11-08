package com.iafenvoy.system.module.builtin.shop;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.input.ShopQuoteInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.data.output.ShopQuoteOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Keeps shop browsing and quoting separate from the confirmed payment operation.
 */
public final class ShopQuoteModule extends AbstractAgentModule<ShopQuoteInput> {
    private final CommerceCatalogs catalogs;

    public ShopQuoteModule(CommerceCatalogs catalogs, ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(ShopQuoteInput.CODEC, "Preview ONE shop purchase; player must then explicitly select the returned confirmation action.", List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
        this.catalogs = catalogs;
    }

    @Override
    public ModuleRouting<ShopQuoteInput> routing() {
        return ModuleRouting.<ShopQuoteInput>defaults()
                .button(a -> new ShopQuoteInput(a.identifier("offer", "offerId"), a.optional("revision", "catalogRevision")))
                .command(AgentCommand.identifier("offer_id", id -> new ShopQuoteInput(id, Optional.empty()), "buy", "兑换"))
                .semantic(RoutingProfile.hints(
                        List.of("我要兑换火把", "购买这个商品", "查看兑换这个的价格", "拿圆石换一些火把"),
                        List.of("I want to exchange for torches", "Buy this shop offer", "Preview this purchase", "Trade cobblestone for torches")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ShopQuoteInput input) throws Exception {
        CommerceCatalog catalog = this.catalogs.available(context.player());
        if (!input.catalogRevision().orElse(catalog.revision()).equals(catalog.revision()))
            throw new AgentException("STALE_QUOTE");
        Identifier id = input.offerId();
        Holder<ShopOfferEntry> offer = catalog.offers().stream().filter(value -> HolderHelper.id(value).equals(id)).findFirst()
                .orElseThrow(() -> new AgentException("STALE_QUOTE"));
        CommercePreviewOutput price = context.tool(BuiltinTools.COMMERCE_PREVIEW, "quote",
                new CommercePreviewInput(List.of(id), List.of(), true, catalog.revision())).getFirst();
        if (!price.available()) throw new AgentException("INVALID_COST");
        return AgentResult.data("system.result.shop_confirm", new ShopQuoteOutput(price.quoteRef().orElseThrow(), offer.value().label(), offer.value().items(), price.price()));
    }
}
