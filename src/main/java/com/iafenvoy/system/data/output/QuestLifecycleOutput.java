package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.input.QuestLifecycleInput;
import com.iafenvoy.assistant.action.ActionArguments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Optional;

/**
 * Optional source-owned lifecycle controls; FTB cards continue to use their existing submission actions.
 */
public record QuestLifecycleOutput(String revision, Component status, boolean accept, boolean abandon,
                                   Optional<PaymentQuote> quote,
                                   Optional<Component> eligibility) {
    public static final QuestLifecycleOutput NONE = new QuestLifecycleOutput("", Component.empty(), false, false, Optional.empty(), Optional.empty());
    public static final Codec<QuestLifecycleOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("revision").forGetter(QuestLifecycleOutput::revision),
            ComponentSerialization.CODEC.fieldOf("status").forGetter(QuestLifecycleOutput::status),
            Codec.BOOL.fieldOf("accept").forGetter(QuestLifecycleOutput::accept),
            Codec.BOOL.fieldOf("abandon").forGetter(QuestLifecycleOutput::abandon),
            PaymentQuote.CODEC.optionalFieldOf("quote").forGetter(QuestLifecycleOutput::quote),
            ComponentSerialization.CODEC.optionalFieldOf("eligibility").forGetter(QuestLifecycleOutput::eligibility)
    ).apply(i, QuestLifecycleOutput::new));

    public void fill(CardBuilder builder, String id) {
        if (!this.status.getString().isBlank()) builder.put("lifecycle_state", this.status);
        this.eligibility.ifPresent(value -> builder.put("eligibility", value));
        if (this.accept) this.button(builder, id, "accept", "system.action.accept_quest", Optional.empty());
        if (this.abandon) this.button(builder, id, "abandon", "system.action.abandon_quest", Optional.empty());
        this.quote.ifPresent(quote -> {
            builder.put("delivery_quote", PaymentQuote.describe(quote.price()));
            this.button(builder, id, "deliver", "system.action.confirm_delivery", Optional.of(quote.reference()));
        });
    }

    private void button(CardBuilder builder, String id, String operation, String label, Optional<String> quote) {
        QuestLifecycleInput input = new QuestLifecycleInput(id, this.revision, operation, quote);
        builder.serverButton(operation, label, input.id(), ActionArguments.encode(input));
    }
}
