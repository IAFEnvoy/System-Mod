package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestIconOutput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmission;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.SpeechContent;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared codecs contain no optional-mod classes, so saved FTB cards also decode without FTB installed.
 */
public final class ResultTypes {
    private ResultTypes() {
    }

    public record ClientState(Optional<ResourceLocation> system, boolean historySaved, boolean externalModel,
                              List<ResourceLocation> modules,
                              List<String> commands, long requestTimeoutMillis) {
        public static final MapCodec<ClientState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("system").forGetter(ClientState::system),
                Codec.BOOL.fieldOf("historySaved").forGetter(ClientState::historySaved),
                Codec.BOOL.fieldOf("externalModel").forGetter(ClientState::externalModel),
                ResourceLocation.CODEC.listOf().fieldOf("modules").forGetter(ClientState::modules),
                Codec.STRING.listOf().fieldOf("commands").forGetter(ClientState::commands),
                Codec.LONG.fieldOf("requestTimeoutMillis").forGetter(ClientState::requestTimeoutMillis)
        ).apply(i, ClientState::new));

        public ClientState {
            modules = List.copyOf(modules);
            commands = List.copyOf(commands);
        }

    }

    public record History(List<PlayerData.Message> messages) {
        public static final Codec<PlayerData.Message> MESSAGE_CODEC = PlayerData.Message.CODEC;
        public static final MapCodec<History> CODEC = MESSAGE_CODEC.listOf().fieldOf("messages").xmap(History::new, History::messages);

        public History {
            messages = List.copyOf(messages);
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
        public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:sign_in_status");

        public SignInStatus {
            candidates = List.copyOf(candidates);
        }

        @Override
        public CardBuilder buildCard() {
            CardBuilder builder = CardBuilder.template(TEMPLATE).summary("system.result.sign_in_status");
            builder.put("date", this.date()).put("signed_in", this.signedIn())
                    .put("history_saved", this.historySaved()).put("external_model", this.externalModel())
                    .list("candidates", this.candidates(), builder::reward);
            return builder;
        }

        @Override
        public MapCodec<SignInStatus> codec() {
            return CODEC;
        }

    }

    public record ShopOrder(String orderId, String label, String state, String error) implements CardData {
        public static final MapCodec<ShopOrder> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("orderId").forGetter(ShopOrder::orderId),
                Codec.STRING.fieldOf("label").forGetter(ShopOrder::label),
                Codec.STRING.fieldOf("state").forGetter(ShopOrder::state),
                Codec.STRING.optionalFieldOf("error", "").forGetter(ShopOrder::error)
        ).apply(i, ShopOrder::new));
        public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:shop_order");

        @Override
        public CardBuilder buildCard() {
            CardBuilder builder = CardBuilder.template(TEMPLATE).summary(Component.literal(this.label));
            builder.put("order_id", this.orderId()).put("label", this.label()).put("state", this.state())
                    .put("error", this.error().isBlank() ? "" : Component.translatable("system.error." + this.error().toLowerCase(Locale.ROOT)));
            return builder;
        }

        @Override
        public MapCodec<ShopOrder> codec() {
            return CODEC;
        }

    }

    public record QuestList(List<QuestBriefOutput> quests) implements CardData {
        public static final MapCodec<QuestList> CODEC = QuestBriefOutput.CODEC.listOf().fieldOf("quests").xmap(QuestList::new, QuestList::quests);
        public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:quest_list");

        public QuestList {
            quests = List.copyOf(quests);
        }

        @Override
        public CardBuilder buildCard() {
            CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.quests").summary("system.result.quests");
            for (QuestBriefOutput quest : this.quests) {
                builder.serverButton("quest_" + quest.id(), "system.action.quest_detail", ResourceLocation.parse("system:quest_detail"),
                                Map.of("quest", quest.id()), CardButton.Placement.BODY, CardButton.Behavior.PUSH)
                        .actionParameter("action_number", quest.number()).actionParameter("action_title", quest.title())
                        .actionParameter("action_chapter", quest.chapter()).actionParameter("action_progress", (int) quest.progress());
            }
            return builder;
        }

        @Override
        public MapCodec<QuestList> codec() {
            return CODEC;
        }

    }

    public record QuestNotice(String id, Component title, Component chapter, List<ItemPreviewOutput> items,
                              String description, List<Component> requirements, List<QuestIconOutput> rewardIcons,
                              List<QuestIconOutput> taskIcons,
                              boolean manualSubmission, QuestEvent event, QuestSubmission submission,
                              boolean rewardPending,
                              Optional<String> claimRef, SpeechContent speech) implements CardData {
        public static final MapCodec<QuestNotice> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(QuestNotice::id),
                ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestNotice::title),
                ComponentSerialization.CODEC.fieldOf("chapter").forGetter(QuestNotice::chapter),
                ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(QuestNotice::items),
                Codec.STRING.fieldOf("description").forGetter(QuestNotice::description),
                ComponentSerialization.CODEC.listOf().fieldOf("requirements").forGetter(QuestNotice::requirements),
                QuestIconOutput.CODEC.listOf().optionalFieldOf("rewardIcons", List.of()).forGetter(QuestNotice::rewardIcons),
                QuestIconOutput.CODEC.listOf().optionalFieldOf("taskIcons", List.of()).forGetter(QuestNotice::taskIcons),
                Codec.BOOL.fieldOf("manualSubmission").forGetter(QuestNotice::manualSubmission),
                QuestEvent.CODEC.fieldOf("event").forGetter(QuestNotice::event),
                QuestSubmission.CODEC.fieldOf("submission").forGetter(QuestNotice::submission),
                Codec.BOOL.fieldOf("rewardPending").forGetter(QuestNotice::rewardPending),
                Codec.STRING.optionalFieldOf("claimRef").forGetter(QuestNotice::claimRef),
                SpeechContent.CODEC.fieldOf("speech").forGetter(QuestNotice::speech)
        ).apply(i, QuestNotice::new));
        public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:quest_notice");

        public QuestNotice {
            title = LegacyText.snapshot(title);
            chapter = LegacyText.snapshot(chapter);
            items = List.copyOf(items);
            requirements = List.copyOf(requirements);
            rewardIcons = List.copyOf(rewardIcons);
            taskIcons = List.copyOf(taskIcons);
        }

        @Override
        public CardBuilder buildCard() {
            CardBuilder builder = CardBuilder.template(TEMPLATE);
            builder.put("id", this.id()).put("title", this.title()).put("chapter", this.chapter()).put("description", this.description())
                    .put("requirements", CardBuilder.join(this.requirements(), "，"))
                    .put("reward_icons", this.rewardIcons()).put("task_icons", this.taskIcons())
                    .enable("has_rewards", !this.items().isEmpty() || !this.rewardIcons().isEmpty())
                    .put("manual_submission", this.manualSubmission()).put("result_items", this.items());
            Component main = Component.translatable("system.tts.message.quest_" + this.event.getSerializedName(), this.title);
            if (this.rewardPending)
                main = main.copy().append("\n").append(Component.translatable("system.ui.quest_reward_pending"));
            builder.summary(main).speech(this.speech);
            this.submission.addButton(builder, this.id);
            if (this.claimRef.isPresent())
                builder.serverButton("claim", "system.action.claim", ResourceLocation.parse("system:quest_claim"), Map.of("claim_ref", this.claimRef.get()));
            else if (this.rewardPending)
                builder.serverButton("rewards", "system.action.claim", ResourceLocation.parse("system:quest_detail"), Map.of("quest", this.id));
            return builder.serverButton("details", "system.action.quest_detail", ResourceLocation.parse("system:quest_detail"), Map.of("quest", this.id), CardButton.Placement.FOOTER, CardButton.Behavior.REPLACE);
        }

        @Override
        public MapCodec<QuestNotice> codec() {
            return CODEC;
        }

    }

    public enum QuestEvent implements StringRepresentable {
        COMPLETED, UNLOCKED;
        public static final Codec<QuestEvent> CODEC = StringRepresentable.fromEnum(QuestEvent::values);

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
