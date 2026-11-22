package com.iafenvoy.system.module.builtin.shop;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.assistant.commerce.ShopBrowsing;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.input.BuybackQueryInput;

import java.util.List;

/**
 * Browses fixed offers that collect the player's items in exchange for a payment.
 */
public final class BuybackModule extends AbstractAgentModule<BuybackQueryInput> {
    private final ShopBrowsing browsing;

    public BuybackModule(ShopBrowsing browsing) {
        super(BuybackQueryInput.CODEC, "Browse fixed buyback offers: items the player can SELL to the system for payment. This only lists offers; selling still needs a quote and explicit confirmation.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
        this.browsing = browsing;
    }

    @Override
    public ModuleRouting<BuybackQueryInput> routing() {
        return ModuleRouting.<BuybackQueryInput>defaults()
                .button(a -> new BuybackQueryInput(a.text("query"), a.number(0, "offset"), a.optional("search_ref")))
                .command(AgentCommand.argument("[query]", text -> new BuybackQueryInput(text, 0), "buyback", "收购"))
                .semantic(RoutingProfile.read(text -> new BuybackQueryInput("", 0),
                        List.of("打开物品收购", "系统收购哪些物品", "看看可以卖什么", "查看物品出售价格"),
                        List.of("Show item buybacks", "What can I sell?", "Which items does the system buy?", "Browse fixed sale prices")))
                .shortcut("system.action.view_buyback", new BuybackQueryInput("", 0));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, BuybackQueryInput input) {
        return this.browsing.browse(context, input, ShopTrade.BUYBACK);
    }
}
