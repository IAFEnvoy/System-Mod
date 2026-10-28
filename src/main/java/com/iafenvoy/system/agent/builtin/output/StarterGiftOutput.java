package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * All gifts share one list; source IDs authorize actions but never become display titles.
 */
public record StarterGiftOutput(List<Gift> gifts) implements CardData {
    public static final MapCodec<StarterGiftOutput> CODEC = Gift.CODEC.listOf().fieldOf("gifts")
            .xmap(StarterGiftOutput::new, StarterGiftOutput::gifts);
    public static final Identifier TEMPLATE = Identifier.parse("system:starter_gift");

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
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.list("gifts", this.gifts(), (prefix, gift) -> builder
                .put(prefix + "result_items", gift.items())
                .put(prefix + "error", gift.error().map(error -> formatting.tr("system.error." + error.toLowerCase(Locale.ROOT))).orElse("")));
    }

    @Override
    public MapCodec<StarterGiftOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
