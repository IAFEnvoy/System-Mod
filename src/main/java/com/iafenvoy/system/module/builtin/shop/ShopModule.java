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
import com.iafenvoy.system.data.input.ShopQueryInput;

import java.util.List;

/**
 * Browses goods the player can buy; sales belong to the separate buyback workflow.
 */
public final class ShopModule extends AbstractAgentModule<ShopQueryInput> {
    private final ShopBrowsing browsing;

    public ShopModule(ShopBrowsing browsing) {
        super(ShopQueryInput.CODEC, "Browse goods the player can BUY from the system shop. To SELL items, use system:buyback instead.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
        this.browsing = browsing;
    }

    @Override
    public ModuleRouting<ShopQueryInput> routing() {
        return ModuleRouting.<ShopQueryInput>defaults()
                .button(a -> new ShopQueryInput(a.text("query"), a.number(0, "offset"), a.optional("search_ref")))
                .command(AgentCommand.argument("[query]", text -> new ShopQueryInput(text, 0), "shop", "商城"))
                .semantic(RoutingProfile.read(text -> new ShopQueryInput("", 0),
                        List.of("打开系统商城", "看看商店卖什么", "列出所有兑换商品", "查看系统商品和价格"),
                        List.of("Open the system shop", "Show goods I can buy", "What does the store sell?", "Browse the shop and prices")))
                .shortcut("system.action.view_shop", new ShopQueryInput("", 0));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, ShopQueryInput input) {
        return this.browsing.browse(context, input, ShopTrade.PURCHASE);
    }
}
