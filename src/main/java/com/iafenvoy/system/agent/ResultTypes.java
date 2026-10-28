package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Shared codecs contain no optional-mod classes, so saved FTB cards also decode without FTB installed.
 */
public final class ResultTypes {
    private ResultTypes() {
    }

    public record Preferences(boolean historySaved, boolean externalModel) implements CardData {
        public static final MapCodec<Preferences> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("historySaved").forGetter(Preferences::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(Preferences::externalModel)
        ).apply(i, Preferences::new));
        public static final Identifier TEMPLATE = Identifier.parse("system:preferences");

        @Override
        public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
            builder.put("history_saved", this.historySaved()).put("external_model", this.externalModel());
        }

        @Override
        public MapCodec<Preferences> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record ClientState(boolean historySaved, boolean externalModel, List<Identifier> modules,
                              List<String> commands) implements CardData {
        public static final MapCodec<ClientState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("historySaved").forGetter(ClientState::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(ClientState::externalModel),
                Identifier.CODEC.listOf().fieldOf("modules").forGetter(ClientState::modules),
                Codec.STRING.listOf().fieldOf("commands").forGetter(ClientState::commands)
        ).apply(i, ClientState::new));
        public static final Identifier TEMPLATE = Identifier.parse("system:client_state");

        public ClientState {
            modules = List.copyOf(modules);
            commands = List.copyOf(commands);
        }

        @Override
        public MapCodec<ClientState> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record History(List<PlayerData.Message> messages) implements CardData {
        public static final Codec<PlayerData.Message> MESSAGE_CODEC = PlayerData.Message.CODEC;
        public static final MapCodec<History> CODEC = MESSAGE_CODEC.listOf().fieldOf("messages").xmap(History::new, History::messages);
        public static final Identifier TEMPLATE = Identifier.parse("system:history");

        public History {
            messages = List.copyOf(messages);
        }

        @Override
        public MapCodec<History> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record SignInStatus(String date, boolean signedIn, List<PlayerData.Reward> candidates, boolean historySaved,
                               boolean externalModel) implements CardData {
        public static final MapCodec<SignInStatus> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("date").forGetter(SignInStatus::date),
                Codec.BOOL.fieldOf("signedIn").forGetter(SignInStatus::signedIn),
                PlayerData.Reward.CODEC.listOf().fieldOf("candidates").forGetter(SignInStatus::candidates),
                Codec.BOOL.fieldOf("historySaved").forGetter(SignInStatus::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(SignInStatus::externalModel)
        ).apply(i, SignInStatus::new));
        public static final Identifier TEMPLATE = Identifier.parse("system:sign_in_status");

        public SignInStatus {
            candidates = List.copyOf(candidates);
        }

        @Override
        public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
            builder.put("date", this.date()).put("signed_in", this.signedIn())
                    .put("history_saved", this.historySaved()).put("external_model", this.externalModel())
                    .list("candidates", this.candidates(), (prefix, reward) -> formatting.rewardParameters(builder, prefix, reward));
        }

        @Override
        public MapCodec<SignInStatus> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record ShopOffer(Optional<Identifier> id, String label, List<ItemStackTemplate> items,
                            CommerceCatalog.Price price,
                            Optional<Boolean> paymentAvailable) implements CardData {
        public static final MapCodec<ShopOffer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.optionalFieldOf("id").forGetter(ShopOffer::id),
                Codec.STRING.fieldOf("label").forGetter(ShopOffer::label),
                PlayerData.Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopOffer::items),
                CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopOffer::price),
                Codec.BOOL.optionalFieldOf("paymentAvailable").forGetter(ShopOffer::paymentAvailable)
        ).apply(i, ShopOffer::new));
        public static final Identifier TEMPLATE = Identifier.parse("system:shop_offer");

        public ShopOffer {
            items = List.copyOf(items);
        }

        @Override
        public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
            builder.put("label", this.label()).put("id", this.id().map(Identifier::toString).orElse(""))
                    .put("items", formatting.items(this.items())).put("offer_items", this.items().stream().map(ItemPreviewOutput::of).toList())
                    .put("payment_available", this.paymentAvailable().orElse(true));
            formatting.priceParameters(builder, this.price());
        }

        @Override
        public MapCodec<ShopOffer> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record ShopOrder(String orderId, String label, String state, String error) implements CardData {
        public static final MapCodec<ShopOrder> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("orderId").forGetter(ShopOrder::orderId),
                Codec.STRING.fieldOf("label").forGetter(ShopOrder::label),
                Codec.STRING.fieldOf("state").forGetter(ShopOrder::state),
                Codec.STRING.optionalFieldOf("error", "").forGetter(ShopOrder::error)
        ).apply(i, ShopOrder::new));
        public static final Identifier TEMPLATE = Identifier.parse("system:shop_order");

        @Override
        public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
            builder.put("order_id", this.orderId()).put("label", this.label()).put("state", this.state())
                    .put("error", this.error().isBlank() ? "" : formatting.tr("system.error." + this.error().toLowerCase(Locale.ROOT)));
        }

        @Override
        public MapCodec<ShopOrder> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record QuestList(List<QuestBriefOutput> quests) implements CardData {
        public static final MapCodec<QuestList> CODEC = QuestBriefOutput.CODEC.listOf().fieldOf("quests").xmap(QuestList::new, QuestList::quests);
        public static final Identifier TEMPLATE = Identifier.parse("system:quest_list");

        public QuestList {
            quests = List.copyOf(quests);
        }

        @Override
        public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
            builder.forActions((prefix, action) -> {
                if (action.call().value() instanceof QuestDetailInput detail)
                    for (QuestBriefOutput quest : this.quests())
                        if (quest.id().equals(detail.questId())) {
                            builder.put(prefix + "action_number", quest.number()).put(prefix + "action_title", quest.title()).put(prefix + "action_chapter", quest.chapter())
                                    .put(prefix + "action_progress", (int) quest.progress());
                            return;
                        }
            });
        }

        @Override
        public MapCodec<QuestList> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }

    public record QuestNotice(String id, String title, String chapter, List<ItemPreviewOutput> items,
                              String description, List<Component> requirements, List<Component> otherRewards,
                              boolean manualSubmission) implements CardData {
        public static final MapCodec<QuestNotice> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(QuestNotice::id),
                Codec.STRING.fieldOf("title").forGetter(QuestNotice::title),
                Codec.STRING.fieldOf("chapter").forGetter(QuestNotice::chapter),
                ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(QuestNotice::items),
                Codec.STRING.fieldOf("description").forGetter(QuestNotice::description),
                ComponentSerialization.CODEC.listOf().fieldOf("requirements").forGetter(QuestNotice::requirements),
                ComponentSerialization.CODEC.listOf().fieldOf("otherRewards").forGetter(QuestNotice::otherRewards),
                Codec.BOOL.fieldOf("manualSubmission").forGetter(QuestNotice::manualSubmission)
        ).apply(i, QuestNotice::new));
        public static final Identifier TEMPLATE = Identifier.parse("system:quest_notice");

        public QuestNotice {
            items = List.copyOf(items);
            requirements = List.copyOf(requirements);
            otherRewards = List.copyOf(otherRewards);
        }

        @Override
        public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
            builder.put("id", this.id()).put("title", this.title()).put("chapter", this.chapter()).put("description", this.description())
                    .put("requirements", String.join("，", this.requirements().stream().map(formatting::text).toList()))
                    .put("other_rewards", String.join("，", this.otherRewards().stream().map(formatting::text).toList()))
                    .put("manual_submission", this.manualSubmission()).put("result_items", this.items());
        }

        @Override
        public MapCodec<QuestNotice> codec() {
            return CODEC;
        }

        @Override
        public Identifier template() {
            return TEMPLATE;
        }
    }
}
