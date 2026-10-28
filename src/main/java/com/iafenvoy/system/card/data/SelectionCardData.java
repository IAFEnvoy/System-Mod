package com.iafenvoy.system.card.data;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.card.CardAction;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.util.ActionArguments;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Read-only workflow shortcuts and page choices are explicit card data.
 */
public record SelectionCardData(Component summary, Component speech, List<Choice> choices) implements CardData {
    public static final MapCodec<SelectionCardData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("summary").forGetter(SelectionCardData::summary),
            ComponentSerialization.CODEC.fieldOf("speech").forGetter(SelectionCardData::speech),
            Choice.CODEC.listOf().fieldOf("choices").forGetter(SelectionCardData::choices)
    ).apply(i, SelectionCardData::new));

    public SelectionCardData {
        summary = LegacyText.snapshot(summary);
        speech = LegacyText.snapshot(speech);
        choices = List.copyOf(choices);
    }

    public static SelectionCardData shortcuts(String key, String speechKey, List<AgentResult.Action> actions) {
        return new SelectionCardData(Component.translatable(key), Component.translatable(speechKey), actions.stream().map(action -> new Choice(action.labelKey(),
                new CardAction.Server(action.moduleId(), ActionArguments.encode(action.call())))).toList());
    }

    public static SelectionCardData fromActions(List<AgentResult.Action> actions) {
        return new SelectionCardData(Component.empty(), Component.empty(), actions.stream().map(action -> new Choice(action.labelKey(),
                new CardAction.Server(action.moduleId(), ActionArguments.encode(action.call())))).toList());
    }

    public record Choice(String labelKey, CardAction.Server action) {
        public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("label").forGetter(Choice::labelKey),
                CardAction.Server.CODEC.fieldOf("call").forGetter(Choice::action)
        ).apply(i, Choice::new));
    }

    @Override
    public MapCodec<SelectionCardData> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.parse("system:selection")).summary(this.summary).speech(this.speech);
        for (int i = 0; i < this.choices.size(); i++) {
            Choice choice = this.choices.get(i);
            builder.serverButton("choice_" + i, choice.labelKey(), choice.action.id(), choice.action.arguments(),
                    CardButton.Placement.BODY, CardButton.Behavior.CLOSE);
        }
        return builder;
    }
}
