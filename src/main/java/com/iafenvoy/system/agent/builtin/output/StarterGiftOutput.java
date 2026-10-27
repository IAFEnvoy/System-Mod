package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * A gift is a typed quest detail, retaining its source and original item components.
 */
public record StarterGiftOutput(String questId, String title, String chapter, List<ItemPreviewOutput> items,
                                Optional<String> claimRef, boolean claimed) implements QuestLookupOutput {
    public static final MapCodec<StarterGiftOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("questId").forGetter(StarterGiftOutput::questId),
            Codec.STRING.fieldOf("title").forGetter(StarterGiftOutput::title),
            Codec.STRING.fieldOf("chapter").forGetter(StarterGiftOutput::chapter),
            ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(StarterGiftOutput::items),
            Codec.STRING.optionalFieldOf("claimRef").forGetter(StarterGiftOutput::claimRef),
            Codec.BOOL.fieldOf("claimed").forGetter(StarterGiftOutput::claimed)
    ).apply(i, StarterGiftOutput::new));

    public StarterGiftOutput {
        items = List.copyOf(items);
    }

    @Override
    public DispatchRegistry.Type<StarterGiftOutput> type() {
        return Types.STARTER_GIFT;
    }
}
