package com.iafenvoy.system.module.builtin.shop;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.assistant.commerce.ShopRequest;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOffer;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.input.ShopQuoteInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.data.output.ShopOfferOutput;
import com.iafenvoy.system.data.output.ShopQuoteOutput;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps shop browsing and quoting separate from the confirmed payment operation.
 */
public final class ShopQuoteModule extends AbstractAgentModule<ShopQuoteInput> {
    private final CommerceTransactions transactions;

    public ShopQuoteModule(CommerceTransactions transactions, ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(ShopQuoteInput.CODEC, "Preview 1 to 100 batches of a shop purchase or item buyback by its advertised offer ID; the player must explicitly confirm before any payment or item collection.", List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
        this.transactions = transactions;
    }

    @Override
    public ModuleRouting<ShopQuoteInput> routing() {
        return ModuleRouting.<ShopQuoteInput>defaults()
                .button(a -> new ShopQuoteInput(a.identifier("offer", "offerId"), a.optional("revision", "catalogRevision"), a.optional("trade").map(ShopTrade::parse), a.number(1, "count")))
                .command(AgentCommand.identifierWithScope("offer_id", "count", List.of("1", "8", "16", "64"), (id, count) -> new ShopQuoteInput(id, Optional.empty(), Optional.of(ShopTrade.PURCHASE), quantity(count)), "buy", "兑换"))
                .command(AgentCommand.identifierWithScope("offer_id", "count", List.of("1", "8", "16", "64"), (id, count) -> new ShopQuoteInput(id, Optional.empty(), Optional.of(ShopTrade.BUYBACK), quantity(count)), "sell", "出售"))
                .semantic(RoutingProfile.hints(
                        List.of("我要兑换火把", "购买这个商品", "查看兑换这个的价格", "拿圆石换一些火把", "我要出售圆石", "把这些物品卖给系统"),
                        List.of("I want to exchange for torches", "Buy this shop offer", "Preview this purchase", "Trade cobblestone for torches", "Sell my cobblestone", "Sell these items to the system")));
    }

    private static int quantity(String value) {
        try {
            int count = value.isBlank() ? 1 : Integer.parseInt(value);
            if (count >= 1 && count <= 100) return count;
        } catch (NumberFormatException ignored) {
        }
        throw new AgentException(AgentFailure.INVALID_INPUT);
    }

    @Override
    public AgentResult execute(ExecutionContext context, ShopQuoteInput input) throws Exception {
        if (input.count() < 1 || input.count() > 100) throw new AgentException(AgentFailure.INVALID_INPUT);
        CommerceCatalog catalog = this.transactions.shopView(context, ShopRequest.resolve(Set.of(input.offerId()))).catalog();
        if (!input.catalogRevision().orElse(catalog.shopRevision()).equals(catalog.shopRevision()))
            throw new AgentException(AgentFailure.STALE_QUOTE);
        Identifier id = input.offerId();
        ShopOffer offer = catalog.offers().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new AgentException(AgentFailure.STALE_QUOTE));
        if (input.trade().filter(trade -> trade != offer.value().trade()).isPresent())
            throw new AgentException(AgentFailure.INVALID_INPUT);
        CommercePreviewOutput price = context.tool(BuiltinTools.COMMERCE_PREVIEW, "quote",
                new CommercePreviewInput(List.of(id), List.of(), true, catalog.shopRevision(), input.count())).getFirst();
        if (price.stockRevision().filter(offer.revision()::equals).isEmpty())
            throw new AgentException(AgentFailure.STALE_QUOTE);
        if (price.purchaseLimit().filter(limit -> limit.remaining() < input.count()).isPresent())
            throw new AgentException(AgentFailure.PURCHASE_LIMIT_REACHED);
        if (!price.conditionMet()
                || offer.value().trade() == ShopTrade.BUYBACK && !price.available())
            return AgentResult.data("system.result.shop_offer", new ShopOfferOutput(id, offer.value().label(), offer.value().items(), offer.value().contents(),
                    price.price(), false, catalog.shopRevision(), price.purchaseLimit(), price.conditionMet(), offer.value().trade()));
        if (!price.available()) throw new AgentException(AgentFailure.INVALID_COST);
        Reward reward = offer.value().reward().multiply(input.count());
        return AgentResult.data(offer.value().trade().key("confirm"), new ShopQuoteOutput(price.quoteRef().orElseThrow(), offer.value().label(), reward.items(), reward.contents(), price.price(), price.purchaseLimit(), offer.value().trade(), id, catalog.shopRevision(), input.count()));
    }
}
