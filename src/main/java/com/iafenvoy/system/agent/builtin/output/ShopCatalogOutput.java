package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * One shelf card lists every browsable offer; each cell dispatches the existing quote action, so prices stay
 * server-side until the player asks for a specific offer.
 */
public record ShopCatalogOutput(String revision, String query, List<Offer> offers,
                                boolean truncated) implements CardData {
    public record Offer(String id, String label, List<ItemPreviewOutput> items) implements ShelfEntry {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Offer::id),
                Codec.STRING.fieldOf("label").forGetter(Offer::label),
                ItemPreviewOutput.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Offer::items)
        ).apply(i, Offer::new));

        public Offer {
            items = List.copyOf(items);
        }
    }

    public static final MapCodec<ShopCatalogOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("revision", "").forGetter(ShopCatalogOutput::revision),
            Codec.STRING.optionalFieldOf("query", "").forGetter(ShopCatalogOutput::query),
            Offer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(ShopCatalogOutput::offers),
            Codec.BOOL.optionalFieldOf("truncated", false).forGetter(ShopCatalogOutput::truncated)
    ).apply(i, ShopCatalogOutput::new));

    public ShopCatalogOutput {
        offers = List.copyOf(offers);
    }

    @Override
    public MapCodec<ShopCatalogOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_catalog"))
                .title("system.result.shop").summary("system.result.shop")
                .put("revision", this.revision).put("query", this.query)
                .put("truncated", this.truncated).put("offers", this.offers);
        // Cell order matches the offer list; the grid binds each cell to the matching action callback.
        for (int index = 0; index < this.offers.size(); index++)
            builder.serverButton("offer." + index, "", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_quote"),
                    Map.of("offer", this.offers.get(index).id(), "revision", this.revision));
        return builder;
    }
}
