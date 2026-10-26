package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.*;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.builtin.tool.FeatureUnlockTool;
import com.iafenvoy.system.agent.builtin.tool.RewardCreditTool;
import com.iafenvoy.system.agent.builtin.tool.SignInCreditTool;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Modules own their operation lists; shared operations reuse the same registered instance.
 */
public final class BuiltinModules {
    private BuiltinModules() {
    }

    public static void install(AgentEngine engine, List<ToolRegistry.Tool<?, ?>> gameTools) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player = BuiltinTools.require(gameTools, BuiltinTools.PLAYER);
        ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items = BuiltinTools.require(gameTools, BuiltinTools.ITEMS);
        ToolRegistry.Tool<EntitlementInput, StateOutput> deliver = BuiltinTools.require(gameTools, BuiltinTools.DELIVER);
        ToolRegistry.Tool<ShopPurchaseInput, PurchaseReceiptOutput> purchase = BuiltinTools.require(gameTools, BuiltinTools.SHOP_PURCHASE);
        ToolRegistry.Tool<SignInCreditInput, RewardOutput> signInCredit = new SignInCreditTool().tool();
        ToolRegistry.Tool<RewardCreditInput, CreditReceiptOutput> rewardCredit = new RewardCreditTool().tool();
        ToolRegistry.Tool<FeatureInput, FeatureOutput> featureUnlock = new FeatureUnlockTool().tool();
        engine.registerModule(new HelpModule());
        engine.registerModule(new PlayerQueryModule(player));
        engine.registerModule(new ItemQueryModule(items));
        engine.registerModule(new SignInModule(player, signInCredit));
        engine.registerModule(new InboxModule());
        engine.registerModule(new DeliverModule(deliver));
        engine.registerModule(new ShopModule());
        engine.registerModule(new ShopQuoteModule());
        engine.registerModule(new ShopPurchaseModule(purchase));
        engine.registerModule(new AdvancementFollowupModule(featureUnlock, rewardCredit));
        engine.modules().root(ResultTypes.HELP_ACTION.of(EmptyInput.INSTANCE));
        engine.modules().reject(Identifier.fromNamespaceAndPath("system", "semantic_reject"), RoutingProfile.hints(
                List.of("不要替我签到", "签到奖励有哪些", "如果签到会怎么样", "查询配方如何合成", "帮我建造房子", "我想问一个游戏攻略"),
                List.of("Do not sign me in", "Explain daily sign-in rewards", "What happens if I sign in?", "How do I craft this recipe?", "Build me a house", "Tell me a game strategy")));
    }
}
