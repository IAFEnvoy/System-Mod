package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.*;
import com.iafenvoy.system.agent.builtin.output.*;

import java.util.List;

/**
 * Stable typed keys shared by workflows and platform implementations; no game classes are referenced.
 */
public final class BuiltinTools {
    public static final ToolRegistry.Type<EmptyInput, PlayerSnapshotOutput> PLAYER =
            new ToolRegistry.Type<>("game.player", EmptyInput.CODEC, PlayerSnapshotOutput.CODEC);
    public static final ToolRegistry.Type<ItemQueryInput, ItemPageOutput> ITEMS =
            new ToolRegistry.Type<>("game.items", ItemQueryInput.CODEC, ItemPageOutput.CODEC);
    public static final ToolRegistry.Type<EntitlementInput, StateOutput> DELIVER =
            new ToolRegistry.Type<>("game.deliver", EntitlementInput.CODEC, StateOutput.CODEC);
    public static final ToolRegistry.Type<SignInCreditInput, RewardOutput> SIGN_IN_CREDIT =
            new ToolRegistry.Type<>("signin.credit", SignInCreditInput.CODEC, RewardOutput.CODEC);
    public static final ToolRegistry.Type<RewardCreditInput, CreditReceiptOutput> REWARD_CREDIT =
            new ToolRegistry.Type<>("reward.credit", RewardCreditInput.CODEC, CreditReceiptOutput.CODEC);
    public static final ToolRegistry.Type<FeatureInput, FeatureOutput> FEATURE_UNLOCK =
            new ToolRegistry.Type<>("feature.unlock", FeatureInput.CODEC, FeatureOutput.CODEC);
    public static final ToolRegistry.Type<ShopPurchaseInput, PurchaseReceiptOutput> SHOP_PURCHASE =
            new ToolRegistry.Type<>("game.shop.purchase", ShopPurchaseInput.CODEC, PurchaseReceiptOutput.CODEC);
    public static final ToolRegistry.Type<LotteryDrawInput, PurchaseReceiptOutput> LOTTERY_DRAW =
            new ToolRegistry.Type<>("game.lottery.draw", LotteryDrawInput.CODEC, PurchaseReceiptOutput.CODEC);
    public static final ToolRegistry.Type<QuestQueryInput, QuestPageOutput> QUEST_QUERY =
            new ToolRegistry.Type<>("ftb.query", QuestQueryInput.CODEC, QuestPageOutput.CODEC.codec());
    public static final ToolRegistry.Type<QuestDetailInput, QuestLookupOutput> QUEST_DETAIL =
            new ToolRegistry.Type<>("ftb.detail", QuestDetailInput.CODEC, QuestLookupOutput.CODEC);
    public static final ToolRegistry.Type<QuestClaimInput, QuestClaimOutput> QUEST_CLAIM =
            new ToolRegistry.Type<>("ftb.claim", QuestClaimInput.CODEC, QuestClaimOutput.CODEC);

    private BuiltinTools() {
    }

    @SuppressWarnings("unchecked")
    public static <I, O> ToolRegistry.Tool<I, O> require(List<ToolRegistry.Tool<?, ?>> tools, ToolRegistry.Type<I, O> type) {
        for (ToolRegistry.Tool<?, ?> tool : tools)
            if (tool.type() == type) return (ToolRegistry.Tool<I, O>) tool;
        throw new IllegalArgumentException("Missing tool " + type.id());
    }
}
