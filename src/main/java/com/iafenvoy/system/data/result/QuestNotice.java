package com.iafenvoy.system.data.result;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.*;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.QuestIconOutput;
import com.iafenvoy.system.data.output.QuestSubmission;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Quest notice card; the shared codec contains no optional-mod classes, so saved FTB cards still decode without FTB installed.
 */
public record QuestNotice(String id, Component title, Component chapter, List<ItemPreviewOutput> items,
                          String description, List<Component> requirements, List<QuestIconOutput> rewardIcons,
                          List<QuestIconOutput> taskIcons, boolean manualSubmission, QuestEvent event,
                          QuestSubmission submission, boolean rewardPending, Optional<String> claimRef,
                          SpeechContent speech) implements CardData {
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
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_notice");

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
                .put("notice_title", Component.translatable("system.ui.card_title_quest_" + this.event.getSerializedName()))
                .put("requirements", CardBuilder.join(this.requirements(), "，"))
                .icons("reward_icons", this.rewardIcons()).icons("task_icons", this.taskIcons())
                .enable("has_rewards", !this.items().isEmpty() || !this.rewardIcons().isEmpty())
                .put("manual_submission", this.manualSubmission()).put("result_items", this.items());
        Component main = Component.translatable("system.tts.message.quest_" + this.event.getSerializedName(), this.title);
        if (this.rewardPending)
            main = main.copy().append("\n").append(Component.translatable("system.ui.quest_reward_pending"));
        builder.summary(main).speech(this.speech);
        this.submission.addButton(builder, this.id);
        if (this.claimRef.isPresent())
            builder.serverButton("claim", "system.action.claim", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_claim"), Map.of("claim_ref", this.claimRef.get()));
        else if (this.rewardPending)
            builder.serverButton("rewards", "system.action.claim", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_detail"), Map.of("quest", this.id));
        return builder.serverButton("details", "system.action.quest_detail", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_detail"), Map.of("quest", this.id), CardButton.Placement.FOOTER, CardButton.Behavior.REPLACE);
    }

    @Override
    public MapCodec<QuestNotice> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("quest_notice", "", this.title().getString(), "", List.of()));
    }

}
