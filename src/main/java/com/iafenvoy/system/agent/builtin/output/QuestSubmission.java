package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.builtin.input.QuestSubmitInput;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.util.ActionArguments;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Server-selected actions shared by quest detail cards and notifications.
 */
public enum QuestSubmission implements StringRepresentable {
    NONE, CHECKMARK, SUBMIT;

    public static final Codec<QuestSubmission> CODEC = StringRepresentable.fromEnum(QuestSubmission::values);

    public void addButton(CardBuilder builder, String questId) {
        if (this == NONE) return;
        QuestSubmitInput input = new QuestSubmitInput("", Optional.empty(), questId, this == CHECKMARK);
        builder.serverButton("submit", this == CHECKMARK ? "system.action.complete_quest" : "system.action.submit_quest",
                ResourceLocation.parse("system:quest_submit"), ActionArguments.encode(input));
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
