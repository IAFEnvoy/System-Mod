package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in result data and its serialization codec.
 */
public record SectionPageOutput(int offset, int nextOffset, boolean hasMore) {
    static MapCodec<SectionPageOutput> codec(String name) {
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf(name + "Offset").forGetter(SectionPageOutput::offset),
                Codec.INT.fieldOf("next" + capitalized + "Offset").forGetter(SectionPageOutput::nextOffset),
                Codec.BOOL.fieldOf("hasMore" + (name.equals("reward") ? "Rewards" : name.equals("task") ? "Tasks" : "Description")).forGetter(SectionPageOutput::hasMore)
        ).apply(i, SectionPageOutput::new));
    }
}
