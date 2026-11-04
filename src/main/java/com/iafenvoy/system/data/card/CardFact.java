package com.iafenvoy.system.data.card;

import com.iafenvoy.saturn.util.MiscUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Bounded, allowlisted projection of one card for the routing prompt; tool free text never enters it.
 */
public record CardFact(String kind, String id, String label, String state, List<Reference> references) {
    public static final int MAX_TEXT = 96, MAX_REFERENCES = 2, MAX_CHOICES = 3, MAX_CHOICE_TEXT = 48;
    public static final Codec<CardFact> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(CardFact::kind),
            Codec.STRING.optionalFieldOf("id", "").forGetter(CardFact::id),
            Codec.STRING.optionalFieldOf("label", "").forGetter(CardFact::label),
            Codec.STRING.optionalFieldOf("state", "").forGetter(CardFact::state),
            Reference.CODEC.listOf().optionalFieldOf("references", List.of()).forGetter(CardFact::references)
    ).apply(i, CardFact::new));

    // Bounds belong to the record, so a new card cannot widen the prompt by passing a longer string.
    public CardFact {
        kind = clip(kind, MAX_TEXT);
        id = clip(id, MAX_TEXT);
        label = clip(label, MAX_TEXT);
        state = clip(state, MAX_TEXT);
        references = references == null ? List.of() : references.stream().limit(MAX_REFERENCES).toList();
    }

    public record Reference(String id, String label, String claimRef, List<String> choices, int choiceOffset) {
        public static final Codec<Reference> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Reference::id),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Reference::label),
                Codec.STRING.optionalFieldOf("claimRef", "").forGetter(Reference::claimRef),
                Codec.STRING.listOf().optionalFieldOf("choices", List.of()).forGetter(Reference::choices),
                Codec.INT.optionalFieldOf("choiceOffset", 0).forGetter(Reference::choiceOffset)
        ).apply(i, Reference::new));

        public Reference {
            id = clip(id, MAX_TEXT);
            label = clip(label, MAX_TEXT);
            claimRef = clip(claimRef, MAX_TEXT);
            choices = choices == null ? List.of() : choices.stream().limit(MAX_CHOICES).map(choice -> clip(choice, MAX_CHOICE_TEXT)).toList();
        }
    }

    private static String clip(String value, int limit) {
        return value == null ? "" : MiscUtil.clip(value, limit);
    }
}
