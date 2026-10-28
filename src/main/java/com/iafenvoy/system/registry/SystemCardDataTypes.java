package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.ResultTypes.QuestList;
import com.iafenvoy.system.agent.ResultTypes.QuestNotice;
import com.iafenvoy.system.agent.ResultTypes.ShopOrder;
import com.iafenvoy.system.agent.ResultTypes.SignInStatus;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.data.BindingCardData;
import com.iafenvoy.system.card.data.SelectionCardData;
import com.iafenvoy.system.card.data.TextCardData;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Shared card codecs are owned by their data implementations; rendering is client-only.
 */
@SuppressWarnings("unused")
public final class SystemCardDataTypes {
    public static final DeferredRegister<MapCodec<? extends CardData>> REGISTRY = DeferredRegister.create(SystemResourceKeys.CARD_DATA_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<TextCardData>> TEXT = REGISTRY.register("text", () -> TextCardData.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<WebSearchOutput>> WEB_SEARCH = REGISTRY.register("web_search", () -> WebSearchOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<SelectionCardData>> SELECTION = REGISTRY.register("selection", () -> SelectionCardData.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<BindingCardData>> BINDING = REGISTRY.register("binding", () -> BindingCardData.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<ShopOfferOutput>> SHOP_OFFER = REGISTRY.register("shop_offer", () -> ShopOfferOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<ShopQuoteOutput>> SHOP_QUOTE = REGISTRY.register("shop_quote", () -> ShopQuoteOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<PlayerSnapshotOutput>> PLAYER = REGISTRY.register("player", () -> PlayerSnapshotOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<ItemPageOutput>> ITEMS = REGISTRY.register("items", () -> ItemPageOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<StateOutput>> DELIVERY_STATE = REGISTRY.register("delivery_state", () -> StateOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<RewardOutput>> REWARD = REGISTRY.register("reward", () -> RewardOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<CreditReceiptOutput>> ENTITLEMENT = REGISTRY.register("entitlement", () -> CreditReceiptOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<PurchaseReceiptOutput>> PURCHASE = REGISTRY.register("purchase", () -> PurchaseReceiptOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<QuestOutput>> QUEST = REGISTRY.register("quest", () -> QuestOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<QuestClaimOutput>> CLAIM = REGISTRY.register("claim", () -> QuestClaimOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<SignInStatus>> SIGN_IN_STATUS = REGISTRY.register("sign_in_status", () -> SignInStatus.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<ShopOrder>> SHOP_ORDER = REGISTRY.register("shop_order", () -> ShopOrder.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<LotteryPoolOutput>> LOTTERY_POOL = REGISTRY.register("lottery_pool", () -> LotteryPoolOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<LotteryPrizeOutput>> LOTTERY_PRIZE = REGISTRY.register("lottery_prize", () -> LotteryPrizeOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<QuestList>> QUEST_LIST = REGISTRY.register("quest_list", () -> QuestList.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<QuestNotice>> QUEST_NOTICE = REGISTRY.register("quest_notice", () -> QuestNotice.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<QuestStartupOutput>> QUEST_STARTUP = REGISTRY.register("quest_startup", () -> QuestStartupOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<StarterGiftOutput>> STARTER_GIFT = REGISTRY.register("starter_gift", () -> StarterGiftOutput.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<QuestSubmitOutput>> QUEST_SUBMIT = REGISTRY.register("quest_submit", () -> QuestSubmitOutput.CODEC);
}
