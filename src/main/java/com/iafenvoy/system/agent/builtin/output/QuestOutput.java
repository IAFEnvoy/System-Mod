package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestOutput(String id, String title, String chapter, String description, double progress,
                          boolean completed, boolean canComplete, boolean starterGift,
                          List<QuestTaskOutput> tasks, List<QuestRewardOutput> rewards, SectionPageOutput rewardPage,
                          SectionPageOutput taskPage, SectionPageOutput descriptionPage) implements CardData {
    public static final MapCodec<QuestOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(QuestOutput::id),
            Codec.STRING.fieldOf("title").forGetter(QuestOutput::title),
            Codec.STRING.fieldOf("chapter").forGetter(QuestOutput::chapter),
            Codec.STRING.fieldOf("description").forGetter(QuestOutput::description),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestOutput::progress),
            Codec.BOOL.fieldOf("completed").forGetter(QuestOutput::completed),
            Codec.BOOL.optionalFieldOf("canComplete", false).forGetter(QuestOutput::canComplete),
            Codec.BOOL.fieldOf("starterGift").forGetter(QuestOutput::starterGift),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestOutput::tasks),
            QuestRewardOutput.CODEC.listOf().fieldOf("rewards").forGetter(QuestOutput::rewards),
            SectionPageOutput.codec("reward").forGetter(QuestOutput::rewardPage),
            SectionPageOutput.codec("task").forGetter(QuestOutput::taskPage),
            SectionPageOutput.codec("description").forGetter(QuestOutput::descriptionPage)
    ).apply(i, QuestOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:quest");

    public QuestOutput {
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
    }

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.put("title", this.title()).put("chapter", this.chapter()).put("description", this.description())
                .put("progress", (int) this.progress()).put("completed", this.completed()).put("can_complete", this.canComplete())
                .list("tasks", this.tasks(), (prefix, task) -> task.buildContext(builder, prefix))
                .list("rewards", this.rewards(), (prefix, reward) -> reward.buildContext(builder, prefix))
                .forActions((prefix, action) -> this.rewardActionParameters(builder, prefix, action));
    }

    private void rewardActionParameters(CardContext.Builder builder, String prefix, AgentResult.Action action) {
        if (action.call().value() instanceof QuestClaimInput(String claimRef, Optional<Integer> choice))
            for (QuestRewardOutput reward : this.rewards()) {
                if (reward.claimRef().isEmpty() || !reward.claimRef().get().equals(claimRef)) continue;
                if (choice.isPresent() && reward.choices().isPresent()) {
                    int index = choice.get() - reward.choiceOffset();
                    if (index >= 0 && index < reward.choices().get().size()) {
                        builder.put(prefix + "action_reward_title", reward.choices().get().get(index).title());
                        return;
                    }
                }
                builder.put(prefix + "action_reward_title", reward.title());
                return;
            }
    }

    @Override
    public MapCodec<QuestOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
