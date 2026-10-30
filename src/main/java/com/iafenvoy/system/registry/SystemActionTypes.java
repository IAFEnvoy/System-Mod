package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.builtin.input.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Shared action codecs never reference optional-mod API classes.
 */
@SuppressWarnings("unused")
public final class SystemActionTypes {
    public static final DeferredRegister<MapCodec<? extends AgentAction>> REGISTRY = DeferredRegister.create(SystemResourceKeys.ACTION_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<HelpInput>> HELP_ACTION = REGISTRY.register("help", () -> HelpInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<QuestionInput>> QUESTION_ACTION = REGISTRY.register("question", () -> QuestionInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<PlayerQueryInput>> PLAYER_ACTION = REGISTRY.register("player_query", () -> PlayerQueryInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<InboxInput>> INBOX_ACTION = REGISTRY.register("inbox", () -> InboxInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<SignInInput>> SIGN_IN_ACTION = REGISTRY.register("sign_in", () -> SignInInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<ItemQueryInput>> ITEMS_ACTION = REGISTRY.register("item_query", () -> ItemQueryInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<EntitlementInput>> DELIVER_ACTION = REGISTRY.register("deliver", () -> EntitlementInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<ShopQueryInput>> SHOP_ACTION = REGISTRY.register("shop", () -> ShopQueryInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<ShopQuoteInput>> QUOTE_ACTION = REGISTRY.register("shop_quote", () -> ShopQuoteInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<ShopPurchaseInput>> PURCHASE_ACTION = REGISTRY.register("shop_purchase", () -> ShopPurchaseInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<LotteryQueryInput>> LOTTERY_ACTION = REGISTRY.register("lottery", () -> LotteryQueryInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<LotteryPreviewInput>> LOTTERY_PREVIEW_ACTION = REGISTRY.register("lottery_preview", () -> LotteryPreviewInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<LotteryDrawInput>> LOTTERY_DRAW_ACTION = REGISTRY.register("lottery_draw", () -> LotteryDrawInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<QuestQueryInput>> QUESTS_ACTION = REGISTRY.register("quest_query", () -> QuestQueryInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<QuestGuideInput>> QUEST_GUIDE_ACTION = REGISTRY.register("quest_guide", () -> QuestGuideInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<QuestDetailInput>> QUEST_ACTION = REGISTRY.register("quest_detail", () -> QuestDetailInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<QuestClaimInput>> CLAIM_ACTION = REGISTRY.register("quest_claim", () -> QuestClaimInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<StarterGiftInput>> STARTER_GIFT_ACTION = REGISTRY.register("starter_gift", () -> StarterGiftInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<StarterGiftsInput>> STARTER_GIFTS_ACTION = REGISTRY.register("starter_gifts", () -> StarterGiftsInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<QuestSubmitInput>> QUEST_SUBMIT_ACTION = REGISTRY.register("quest_submit", () -> QuestSubmitInput.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<AdvancementInput>> ADVANCEMENT_ACTION = REGISTRY.register("advancement_followup", () -> AdvancementInput.CODEC);
}
