package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.builtin.input.*;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Shared codecs contain no optional-mod classes, so saved FTB cards also decode without FTB installed.
 */
public final class ResultTypes {
    public static final PayloadRegistry CARDS = new PayloadRegistry("type", "value");
    public static final PayloadRegistry ACTIONS = new PayloadRegistry("moduleId", "input");
    public static final TypedPayload.Type<EmptyInput> EMPTY = card("empty", EmptyInput.CODEC.codec());
    public static final TypedPayload.Type<PlayerSnapshotOutput> PLAYER = card("player", PlayerSnapshotOutput.CODEC);
    public static final TypedPayload.Type<ItemPageOutput> ITEMS = card("items", ItemPageOutput.CODEC);
    public static final TypedPayload.Type<StateOutput> DELIVERY_STATE = card("delivery_state", StateOutput.CODEC);
    public static final TypedPayload.Type<RewardOutput> REWARD = card("reward", RewardOutput.CODEC);
    public static final TypedPayload.Type<CreditReceiptOutput> ENTITLEMENT = card("entitlement", CreditReceiptOutput.CODEC);
    public static final TypedPayload.Type<PurchaseReceiptOutput> PURCHASE = card("purchase", PurchaseReceiptOutput.CODEC);
    public static final TypedPayload.Type<QuestOutput> QUEST = card("quest", QuestOutput.CODEC);
    public static final TypedPayload.Type<QuestClaimOutput> CLAIM = card("claim", QuestClaimOutput.CODEC);
    public static final TypedPayload.Type<Preferences> PREFERENCES = card("preferences", Preferences.CODEC);
    public static final TypedPayload.Type<ClientState> CLIENT_STATE = card("client_state", ClientState.CODEC);
    public static final TypedPayload.Type<History> HISTORY = card("history", History.CODEC);
    public static final TypedPayload.Type<SignInStatus> SIGN_IN_STATUS = card("sign_in_status", SignInStatus.CODEC);
    public static final TypedPayload.Type<ShopOffer> SHOP_OFFER = card("shop_offer", ShopOffer.CODEC);
    public static final TypedPayload.Type<ShopOrder> SHOP_ORDER = card("shop_order", ShopOrder.CODEC);
    public static final TypedPayload.Type<LotteryPoolOutput> LOTTERY_POOL = card("lottery_pool", LotteryPoolOutput.CODEC);
    public static final TypedPayload.Type<LotteryPrizeOutput> LOTTERY_PRIZE = card("lottery_prize", LotteryPrizeOutput.CODEC);
    public static final TypedPayload.Type<QuestList> QUEST_LIST = card("quest_list", QuestList.CODEC);
    public static final TypedPayload.Type<QuestNotice> QUEST_NOTICE = card("quest_notice", QuestNotice.CODEC);
    public static final TypedPayload.Type<QuestStartupOutput> QUEST_STARTUP = card("quest_startup", QuestStartupOutput.CODEC);
    public static final TypedPayload.Type<StarterGiftOutput> STARTER_GIFT = card("starter_gift", StarterGiftOutput.CODEC.codec());
    public static final TypedPayload.Type<QuestSubmitOutput> QUEST_SUBMIT = card("quest_submit", QuestSubmitOutput.CODEC);

    public static final TypedPayload.Type<EmptyInput> HELP_ACTION = action("help", EmptyInput.CODEC.codec());
    public static final TypedPayload.Type<EmptyInput> PLAYER_ACTION = action("player_query", EmptyInput.CODEC.codec());
    public static final TypedPayload.Type<EmptyInput> INBOX_ACTION = action("inbox", EmptyInput.CODEC.codec());
    public static final TypedPayload.Type<EmptyInput> SIGN_IN_ACTION = action("sign_in", EmptyInput.CODEC.codec());
    public static final TypedPayload.Type<ItemQueryInput> ITEMS_ACTION = action("item_query", ItemQueryInput.CODEC.codec());
    public static final TypedPayload.Type<EntitlementInput> DELIVER_ACTION = action("deliver", EntitlementInput.CODEC.codec());
    public static final TypedPayload.Type<ShopQueryInput> SHOP_ACTION = action("shop", ShopQueryInput.CODEC.codec());
    public static final TypedPayload.Type<ShopQuoteInput> QUOTE_ACTION = action("shop_quote", ShopQuoteInput.CODEC.codec());
    public static final TypedPayload.Type<ShopPurchaseInput> PURCHASE_ACTION = action("shop_purchase", ShopPurchaseInput.CODEC.codec());
    public static final TypedPayload.Type<LotteryQueryInput> LOTTERY_ACTION = action("lottery", LotteryQueryInput.CODEC.codec());
    public static final TypedPayload.Type<LotteryPreviewInput> LOTTERY_PREVIEW_ACTION = action("lottery_preview", LotteryPreviewInput.CODEC.codec());
    public static final TypedPayload.Type<LotteryDrawInput> LOTTERY_DRAW_ACTION = action("lottery_draw", LotteryDrawInput.CODEC.codec());
    public static final TypedPayload.Type<QuestQueryInput> QUESTS_ACTION = action("quest_query", QuestQueryInput.CODEC.codec());
    public static final TypedPayload.Type<QuestDetailInput> QUEST_ACTION = action("quest_detail", QuestDetailInput.CODEC.codec());
    public static final TypedPayload.Type<QuestClaimInput> CLAIM_ACTION = action("quest_claim", QuestClaimInput.CODEC.codec());
    public static final TypedPayload.Type<StarterGiftInput> STARTER_GIFT_ACTION = action("starter_gift", StarterGiftInput.CODEC.codec());
    public static final TypedPayload.Type<QuestSubmitInput> QUEST_SUBMIT_ACTION = action("quest_submit", QuestSubmitInput.CODEC.codec());

    public static final TypedPayload.Type<AdvancementInput> ADVANCEMENT_ACTION = action("advancement_followup", AdvancementInput.CODEC.codec());

    private ResultTypes() {
    }

    private static <T> TypedPayload.Type<T> card(String path, Codec<T> codec) {
        return CARDS.register(Identifier.fromNamespaceAndPath("system", path), codec);
    }

    private static <T> TypedPayload.Type<T> action(String path, Codec<T> codec) {
        return ACTIONS.register(Identifier.fromNamespaceAndPath("system", path), codec);
    }

    public record Preferences(boolean historySaved, boolean externalModel) {
        public static final Codec<Preferences> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("historySaved").forGetter(Preferences::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(Preferences::externalModel)
        ).apply(i, Preferences::new));
    }

    public record ClientState(boolean historySaved, boolean externalModel, boolean questAvailable,
                              List<String> commands) {
        public static final Codec<ClientState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("historySaved").forGetter(ClientState::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(ClientState::externalModel),
                Codec.BOOL.fieldOf("questAvailable").forGetter(ClientState::questAvailable),
                Codec.STRING.listOf().fieldOf("commands").forGetter(ClientState::commands)
        ).apply(i, ClientState::new));

        public ClientState {
            commands = List.copyOf(commands);
        }
    }

    public record History(List<PlayerData.Message> messages) {
        public static final Codec<PlayerData.Message> MESSAGE_CODEC = PlayerData.Message.CODEC;
        public static final Codec<History> CODEC = MESSAGE_CODEC.listOf().fieldOf("messages").xmap(History::new, History::messages).codec();

        public History {
            messages = List.copyOf(messages);
        }
    }

    public record SignInStatus(String date, boolean signedIn, List<PlayerData.Reward> candidates, boolean historySaved,
                               boolean externalModel) {
        public static final Codec<SignInStatus> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("date").forGetter(SignInStatus::date),
                Codec.BOOL.fieldOf("signedIn").forGetter(SignInStatus::signedIn),
                PlayerData.Reward.CODEC.listOf().fieldOf("candidates").forGetter(SignInStatus::candidates),
                Codec.BOOL.fieldOf("historySaved").forGetter(SignInStatus::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(SignInStatus::externalModel)
        ).apply(i, SignInStatus::new));

        public SignInStatus {
            candidates = List.copyOf(candidates);
        }
    }

    public record ShopOffer(Optional<Identifier> id, String label, List<ItemStackTemplate> items,
                            CommerceCatalog.Price price,
                            Optional<Boolean> paymentAvailable) {
        public static final Codec<ShopOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.optionalFieldOf("id").forGetter(ShopOffer::id),
                Codec.STRING.fieldOf("label").forGetter(ShopOffer::label),
                PlayerData.Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopOffer::items),
                CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopOffer::price),
                Codec.BOOL.optionalFieldOf("paymentAvailable").forGetter(ShopOffer::paymentAvailable)
        ).apply(i, ShopOffer::new));

        public ShopOffer {
            items = List.copyOf(items);
        }
    }

    public record ShopOrder(String orderId, String label, String state, String error) {
        public static final Codec<ShopOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("orderId").forGetter(ShopOrder::orderId),
                Codec.STRING.fieldOf("label").forGetter(ShopOrder::label),
                Codec.STRING.fieldOf("state").forGetter(ShopOrder::state),
                Codec.STRING.optionalFieldOf("error", "").forGetter(ShopOrder::error)
        ).apply(i, ShopOrder::new));
    }

    public record QuestList(List<QuestBriefOutput> quests) {
        public static final Codec<QuestList> CODEC = QuestBriefOutput.CODEC.listOf().fieldOf("quests").xmap(QuestList::new, QuestList::quests).codec();

        public QuestList {
            quests = List.copyOf(quests);
        }
    }

    public record QuestNotice(String id, String title, String chapter, List<ItemPreviewOutput> items) {
        public static final Codec<QuestNotice> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(QuestNotice::id),
                Codec.STRING.fieldOf("title").forGetter(QuestNotice::title),
                Codec.STRING.fieldOf("chapter").forGetter(QuestNotice::chapter),
                ItemPreviewOutput.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestNotice::items)
        ).apply(i, QuestNotice::new));

        public QuestNotice {
            items = List.copyOf(items);
        }
    }
}
