package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Merged reward previews share one list; each source retains its claim reference and failure separately.
 */
public record StarterGiftOutput(List<Gift> gifts, List<ItemPreviewOutput> items,
                                List<QuestIconOutput> icons) implements CardData {
    public static final MapCodec<StarterGiftOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Gift.CODEC.listOf().fieldOf("gifts").forGetter(StarterGiftOutput::gifts),
            ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(StarterGiftOutput::items),
            QuestIconOutput.CODEC.listOf().fieldOf("icons").forGetter(StarterGiftOutput::icons)
    ).apply(i, StarterGiftOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:starter_gift");

    public StarterGiftOutput {
        gifts = List.copyOf(gifts);
        items = List.copyOf(items);
        icons = List.copyOf(icons);
    }

    public record Gift(String questId, Optional<String> claimRef, Optional<String> error) {
        public static final Codec<Gift> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("questId").forGetter(Gift::questId),
                Codec.STRING.optionalFieldOf("claimRef").forGetter(Gift::claimRef),
                Codec.STRING.optionalFieldOf("error").forGetter(Gift::error)
        ).apply(i, Gift::new));
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).put("result_items", this.items).put("reward_icons", this.icons)
                .enable("has_rewards", !this.items.isEmpty() || !this.icons.isEmpty());
        builder.list("gifts", this.gifts(), (prefix, gift) -> builder
                .put(prefix + "error", gift.error().map(error -> Component.translatable("system.error." + error.toLowerCase(Locale.ROOT))).orElse(Component.empty())));
        List<String> refs = this.gifts.stream().flatMap(gift -> gift.claimRef().stream()).toList();
        if (!refs.isEmpty())
            builder.serverButton("claim_gifts", "system.action.claim_starter_gift", ResourceLocation.parse("system:starter_gift"), Map.of("references", String.join(",", refs)));
        return builder.summary("system.result.starter_gift").speech(Component.translatable("system.tts.message.starter_gift"))
                .serverButton("refresh_gifts", "system.action.refresh_starter_gift", ResourceLocation.parse("system:starter_gifts"), Map.of());
    }

    @Override
    public MapCodec<StarterGiftOutput> codec() {
        return CODEC;
    }

}
