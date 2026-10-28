package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE);
        builder.list("gifts", this.gifts(), (prefix, gift) -> builder
                .put(prefix + "result_items", gift.items())
                .put(prefix + "error", gift.error().map(error -> Component.translatable("system.error." + error.toLowerCase(Locale.ROOT))).orElse(Component.empty())));
        List<String> refs = this.gifts.stream().flatMap(gift -> gift.claimRef().stream()).toList();
        if (!refs.isEmpty())
            builder.serverButton("claim_gifts", "system.action.claim_starter_gift", Identifier.parse("system:starter_gift"), Map.of("references", String.join(",", refs)));
        return builder.summary("system.result.starter_gift").speech(Component.translatable("system.tts.message.starter_gift"))
                .serverButton("refresh_gifts", "system.action.refresh_starter_gift", Identifier.parse("system:starter_gifts"), Map.of());
    }

    @Override
    public MapCodec<StarterGiftOutput> codec() {
        return CODEC;
    }

}
