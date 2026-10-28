package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.builtin.input.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Shared payload definitions never reference optional-mod API classes.
 */
@SuppressWarnings("unused")
public final class SystemActionTypes {
    public static final DeferredRegister<TypedPayload.Type<?>> REGISTRY = DeferredRegister.create(SystemResourceKeys.ACTION_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<EmptyInput>> HELP_ACTION = register("help", () -> EmptyInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<EmptyInput>> PLAYER_ACTION = register("player_query", () -> EmptyInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<EmptyInput>> INBOX_ACTION = register("inbox", () -> EmptyInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<EmptyInput>> SIGN_IN_ACTION = register("sign_in", () -> EmptyInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<ItemQueryInput>> ITEMS_ACTION = register("item_query", () -> ItemQueryInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<EntitlementInput>> DELIVER_ACTION = register("deliver", () -> EntitlementInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<ShopQueryInput>> SHOP_ACTION = register("shop", () -> ShopQueryInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<ShopQuoteInput>> QUOTE_ACTION = register("shop_quote", () -> ShopQuoteInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<ShopPurchaseInput>> PURCHASE_ACTION = register("shop_purchase", () -> ShopPurchaseInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<LotteryQueryInput>> LOTTERY_ACTION = register("lottery", () -> LotteryQueryInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<LotteryPreviewInput>> LOTTERY_PREVIEW_ACTION = register("lottery_preview", () -> LotteryPreviewInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<LotteryDrawInput>> LOTTERY_DRAW_ACTION = register("lottery_draw", () -> LotteryDrawInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<QuestQueryInput>> QUESTS_ACTION = register("quest_query", () -> QuestQueryInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<QuestDetailInput>> QUEST_ACTION = register("quest_detail", () -> QuestDetailInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<QuestClaimInput>> CLAIM_ACTION = register("quest_claim", () -> QuestClaimInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<StarterGiftInput>> STARTER_GIFT_ACTION = register("starter_gift", () -> StarterGiftInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<EmptyInput>> STARTER_GIFTS_ACTION = register("starter_gifts", () -> EmptyInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<QuestSubmitInput>> QUEST_SUBMIT_ACTION = register("quest_submit", () -> QuestSubmitInput.CODEC);
    public static final DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<AdvancementInput>> ADVANCEMENT_ACTION = register("advancement_followup", () -> AdvancementInput.CODEC);

    private static <T> DeferredHolder<TypedPayload.Type<?>, TypedPayload.Type<T>> register(String path, Supplier<SchemaMapCodec<T>> codec) {
        return REGISTRY.register(path, id -> new TypedPayload.Type<>(id, codec.get()));
    }
}
