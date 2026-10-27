package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * All gifts share one list; source IDs authorize actions but never become display titles.
 */
public record StarterGiftOutput(List<Gift> gifts) implements QuestLookupOutput {
    public static final MapCodec<StarterGiftOutput> CODEC = Gift.CODEC.listOf().fieldOf("gifts")
            .xmap(StarterGiftOutput::new, StarterGiftOutput::gifts);

    public StarterGiftOutput {
        gifts = List.copyOf(gifts);
    }

    public record Gift(String questId, List<ItemPreviewOutput> items, Optional<String> claimRef,
                       Optional<String> error) {
        public static final Codec<Gift> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("questId").forGetter(Gift::questId),
                ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(Gift::items),
                Codec.STRING.optionalFieldOf("claimRef").forGetter(Gift::claimRef),
                Codec.STRING.optionalFieldOf("error").forGetter(Gift::error)
        ).apply(i, Gift::new));

        public Gift {
            items = List.copyOf(items);
        }
    }

    @Override
    public DispatchRegistry.Type<StarterGiftOutput> type() {
        return Types.STARTER_GIFT;
    }
}
