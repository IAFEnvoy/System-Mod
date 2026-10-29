package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.SpeechContent;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestOutput(String id, Component title, Component chapter, String description, double progress,
                          boolean completed, QuestSubmission submission, boolean starterGift, boolean rewardPending,
                          List<QuestTaskOutput> tasks, List<QuestRewardOutput> rewards, SectionPageOutput rewardPage,
                          SectionPageOutput taskPage, SectionPageOutput descriptionPage,
                          SpeechContent speech) implements CardData {
    public static final MapCodec<QuestOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(QuestOutput::id),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestOutput::title),
            ComponentSerialization.CODEC.fieldOf("chapter").forGetter(QuestOutput::chapter),
            Codec.STRING.fieldOf("description").forGetter(QuestOutput::description),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestOutput::progress),
            Codec.BOOL.fieldOf("completed").forGetter(QuestOutput::completed),
            QuestSubmission.CODEC.fieldOf("submission").forGetter(QuestOutput::submission),
            Codec.BOOL.fieldOf("starterGift").forGetter(QuestOutput::starterGift),
            Codec.BOOL.fieldOf("rewardPending").forGetter(QuestOutput::rewardPending),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestOutput::tasks),
            QuestRewardOutput.CODEC.listOf().fieldOf("rewards").forGetter(QuestOutput::rewards),
            SectionPageOutput.codec("reward").forGetter(QuestOutput::rewardPage),
            SectionPageOutput.codec("task").forGetter(QuestOutput::taskPage),
            SectionPageOutput.codec("description").forGetter(QuestOutput::descriptionPage),
            SpeechContent.CODEC.fieldOf("speech").forGetter(QuestOutput::speech)
    ).apply(i, QuestOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:quest");

    public QuestOutput {
        title = LegacyText.snapshot(title);
        chapter = LegacyText.snapshot(chapter);
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
    }

    @Override
    public CardBuilder buildCard() {
        // Share the decorated heading with the summary; speech keeps its separate, undecorated content.
        Component heading = Component.empty()
                .append(Component.literal(this.completed ? "✓ " : "✗ ").withStyle(this.completed ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(this.title);
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary(heading);
        builder.put("title", heading).put("chapter", this.chapter()).put("description", this.description())
                .put("progress", (int) this.progress()).put("completed", this.completed())
                .list("tasks", this.tasks(), (prefix, task) -> task.fill(builder, prefix))
                .list("rewards", this.rewards(), (prefix, reward) -> reward.fill(builder, prefix));
        this.submission.addButton(builder, this.id);
        for (QuestRewardOutput reward : this.rewards) {
            if (!reward.claimable() || reward.claimRef().isEmpty()) continue;
            if (reward.choices().isPresent()) {
                for (int i = 0; i < reward.choices().get().size(); i++) {
                    QuestChoiceOutput choice = reward.choices().get().get(i);
                    builder.serverButton("claim_" + reward.id() + "_" + i, "system.action.choose", ResourceLocation.parse("system:quest_claim"),
                                    Map.of("claim_ref", reward.claimRef().get(), "choice", Integer.toString(reward.choiceOffset() + i)))
                            .actionParameter("action_reward_title", choice.title());
                }
                if (reward.hasMoreChoices())
                    this.page(builder, "more_choices", "system.action.more_choices", this.rewardPage.offset(), reward.choiceOffset() + reward.choices().get().size(), this.descriptionPage.offset(), this.taskPage.offset());
            } else
                builder.serverButton("claim_" + reward.id(), "system.action.claim", ResourceLocation.parse("system:quest_claim"), Map.of("claim_ref", reward.claimRef().get()))
                        .actionParameter("action_reward_title", reward.title());
        }
        if (this.rewardPage.hasMore())
            this.page(builder, "more_rewards", "system.action.more_rewards", this.rewardPage.nextOffset(), 0, this.descriptionPage.offset(), this.taskPage.offset());
        if (this.taskPage.hasMore())
            this.page(builder, "more_tasks", "system.action.more_tasks", this.rewardPage.offset(), 0, this.descriptionPage.offset(), this.taskPage.nextOffset());
        if (this.descriptionPage.hasMore())
            this.page(builder, "more_description", "system.action.more_description", this.rewardPage.offset(), 0, this.descriptionPage.nextOffset(), this.taskPage.offset());
        return builder.title("system.result.quest").speech(this.speech);
    }

    private void page(CardBuilder builder, String id, String label, int rewards, int choices, int description, int tasks) {
        builder.serverButton(id, label, ResourceLocation.parse("system:quest_detail"), Map.of("quest", this.id,
                "rewards", Integer.toString(rewards), "choices", Integer.toString(choices), "description", Integer.toString(description), "tasks", Integer.toString(tasks)));
    }

    @Override
    public MapCodec<QuestOutput> codec() {
        return CODEC;
    }

}
