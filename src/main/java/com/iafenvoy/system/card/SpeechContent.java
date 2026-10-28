package com.iafenvoy.system.card;

import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

/**
 * All optional narration is prepared on the server; the client only selects enabled sections.
 */
public record SpeechContent(Component main, List<Component> requirements, List<Component> rewards,
                            List<Component> descriptions) {
    public static final SpeechContent EMPTY = new SpeechContent(Component.empty(), List.of(), List.of(), List.of());
    public static final Codec<SpeechContent> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("main").forGetter(SpeechContent::main),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(SpeechContent::requirements),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(SpeechContent::rewards),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("descriptions", List.of()).forGetter(SpeechContent::descriptions)
    ).apply(i, SpeechContent::new));

    public SpeechContent {
        main = LegacyText.snapshot(main);
        requirements = requirements.stream().map(LegacyText::snapshot).toList();
        rewards = rewards.stream().map(LegacyText::snapshot).toList();
        descriptions = descriptions.stream().map(LegacyText::snapshot).toList();
    }

    public static SpeechContent of(Component text) {
        return new SpeechContent(text, List.of(), List.of(), List.of());
    }
}
