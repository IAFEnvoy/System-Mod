package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestRewardOutput(String id, Component title, boolean claimable, String state,
                                Optional<List<QuestChoiceOutput>> choices,
                                int choiceOffset, boolean hasMoreChoices, Optional<String> claimRef,
                                List<ItemPreviewOutput> items, List<QuestIconOutput> icons) {
    public static final Codec<QuestRewardOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(QuestRewardOutput::id),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestRewardOutput::title),
            Codec.BOOL.fieldOf("claimable").forGetter(QuestRewardOutput::claimable),
            Codec.STRING.fieldOf("state").forGetter(QuestRewardOutput::state),
            QuestChoiceOutput.CODEC.listOf().optionalFieldOf("choices").forGetter(QuestRewardOutput::choices),
            Codec.INT.optionalFieldOf("choiceOffset", 0).forGetter(QuestRewardOutput::choiceOffset),
            Codec.BOOL.optionalFieldOf("hasMoreChoices", false).forGetter(QuestRewardOutput::hasMoreChoices),
            Codec.STRING.optionalFieldOf("claimRef").forGetter(QuestRewardOutput::claimRef),
            ItemPreviewOutput.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestRewardOutput::items),
            QuestIconOutput.CODEC.listOf().optionalFieldOf("icons", List.of()).forGetter(QuestRewardOutput::icons)
    ).apply(i, QuestRewardOutput::new));

    public QuestRewardOutput {
        title = LegacyText.snapshot(title);

        choices = choices.map(List::copyOf);
        items = List.copyOf(items);
        icons = List.copyOf(icons);
    }

    public void fill(CardBuilder builder, String prefix) {
        List<QuestChoiceOutput> choices = this.choices().orElse(List.of());
        builder.put(prefix + "reward_title", this.title()).put(prefix + "reward_state", this.state()).put(prefix + "result_items", this.items())
                .put(prefix + "reward_icons", this.icons());
        builder.put(prefix + "choices", choices.size());
        for (int index = 0; index < choices.size(); index++) {
            QuestChoiceOutput choice = choices.get(index);
            String id = prefix + "choices." + index + ".";
            builder.put(id + "choice_number", this.choiceOffset() + index + 1)
                    .put(id + "choice_title", choice.title()).put(id + "result_items", choice.items()).put(id + "choice_icons", choice.icons());
        }
    }
}
