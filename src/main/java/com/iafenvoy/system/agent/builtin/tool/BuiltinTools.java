package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.*;
import com.iafenvoy.system.agent.builtin.output.*;

import java.util.List;

/**
 * Stable typed keys shared by workflows and platform implementations; no game classes are referenced.
 */
public final class BuiltinTools {
    public static final ToolRegistry.Type<CommercePreviewInput, List<CommercePreviewOutput>> COMMERCE_PREVIEW = new ToolRegistry.Type<>("game.commerce.preview", CommercePreviewInput.CODEC, CommercePreviewOutput.CODEC.listOf());
    public static final ToolRegistry.Type<EmptyInput, PlayerSnapshotOutput> PLAYER = new ToolRegistry.Type<>("game.player", EmptyInput.CODEC, PlayerSnapshotOutput.CODEC.codec());
    public static final ToolRegistry.Type<ItemQueryInput, ItemPageOutput> ITEMS = new ToolRegistry.Type<>("game.items", ItemQueryInput.CODEC, ItemPageOutput.CODEC.codec());
    public static final ToolRegistry.Type<EntitlementInput, StateOutput> DELIVER = new ToolRegistry.Type<>("game.deliver", EntitlementInput.CODEC, StateOutput.CODEC.codec());
    public static final ToolRegistry.Type<SignInCreditInput, RewardOutput> SIGN_IN_CREDIT = new ToolRegistry.Type<>("signin.credit", SignInCreditInput.CODEC, RewardOutput.CODEC.codec());
    public static final ToolRegistry.Type<RewardCreditInput, CreditReceiptOutput> REWARD_CREDIT = new ToolRegistry.Type<>("reward.credit", RewardCreditInput.CODEC, CreditReceiptOutput.CODEC.codec());
    public static final ToolRegistry.Type<FeatureInput, FeatureOutput> FEATURE_UNLOCK = new ToolRegistry.Type<>("feature.unlock", FeatureInput.CODEC, FeatureOutput.CODEC);
    public static final ToolRegistry.Type<ShopPurchaseInput, PurchaseReceiptOutput> SHOP_PURCHASE = new ToolRegistry.Type<>("game.shop.purchase", ShopPurchaseInput.CODEC, PurchaseReceiptOutput.CODEC.codec());
    public static final ToolRegistry.Type<LotteryDrawInput, PurchaseReceiptOutput> LOTTERY_DRAW = new ToolRegistry.Type<>("game.lottery.draw", LotteryDrawInput.CODEC, PurchaseReceiptOutput.CODEC.codec());
    public static final ToolRegistry.Type<QuestQueryInput, List<QuestBriefOutput>> QUEST_QUERY = new ToolRegistry.Type<>("ftb.query", QuestQueryInput.CODEC, QuestBriefOutput.CODEC.listOf());
    public static final ToolRegistry.Type<QuestFindInput, List<QuestBriefOutput>> QUEST_FIND = new ToolRegistry.Type<>("ftb.find", QuestFindInput.CODEC, QuestBriefOutput.CODEC.listOf());
    public static final ToolRegistry.Type<QuestDetailInput, QuestOutput> QUEST_DETAIL = new ToolRegistry.Type<>("ftb.detail", QuestDetailInput.CODEC, QuestOutput.CODEC.codec());
    public static final ToolRegistry.Type<QuestClaimInput, QuestClaimOutput> QUEST_CLAIM = new ToolRegistry.Type<>("ftb.claim", QuestClaimInput.CODEC, QuestClaimOutput.CODEC.codec());
    public static final ToolRegistry.Type<StarterGiftInput, StarterGiftOutput> STARTER_GIFT = new ToolRegistry.Type<>("ftb.starter_gift", StarterGiftInput.CODEC, StarterGiftOutput.CODEC.codec());
    public static final ToolRegistry.Type<EmptyInput, StarterGiftOutput> STARTER_GIFTS = new ToolRegistry.Type<>("ftb.starter_gifts", EmptyInput.CODEC, StarterGiftOutput.CODEC.codec());
    public static final ToolRegistry.Type<QuestSubmitInput, QuestSubmitOutput> QUEST_SUBMIT = new ToolRegistry.Type<>("ftb.submit", QuestSubmitInput.CODEC, QuestSubmitOutput.CODEC.codec());

    private BuiltinTools() {
    }
}
