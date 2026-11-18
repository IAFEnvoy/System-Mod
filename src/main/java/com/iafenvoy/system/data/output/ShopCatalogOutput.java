package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardText;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One shelf card lists every browsable offer; each cell dispatches the existing quote action, so prices stay
 * server-side until the player asks for a specific offer.
 */
public record ShopCatalogOutput(String revision, String query, List<Offer> offers,
                                boolean truncated, Optional<ShopTrade> trade, boolean searchRequired,
                                Optional<Integer> nextOffset, List<ShopRotationOutput> rotations) implements CardData {
    public record Offer(String id, Component label, List<ItemPreviewOutput> items, List<SpaceStack> contents,
                        Optional<ShopPurchaseLimitOutput> purchaseLimit, boolean available,
                        ShopTrade trade, List<Component> details) implements ShelfEntry {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Offer::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Offer::label),
                ItemPreviewOutput.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Offer::items),
                Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(Offer::contents),
                ShopPurchaseLimitOutput.CODEC.optionalFieldOf("purchaseLimit").forGetter(Offer::purchaseLimit),
                Codec.BOOL.optionalFieldOf("available", true).forGetter(Offer::available),
                ShopTrade.CODEC.optionalFieldOf("trade", ShopTrade.PURCHASE).forGetter(Offer::trade),
                ComponentSerialization.CODEC.listOf().optionalFieldOf("details", List.of()).forGetter(Offer::details)
        ).apply(i, Offer::new));

        public Offer {
            items = List.copyOf(items);
            contents = List.copyOf(contents);
            details = List.copyOf(details);
        }

        @Override
        public List<Component> tooltip(int index) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(this.trade.key("label")));
            lines.addAll(this.details);
            this.contents.forEach(content -> lines.add(content.label()));
            this.purchaseLimit.ifPresent(limit -> lines.add(limit.description(this.trade)));
            if (!this.available) lines.add(Component.translatable("system.ui.condition_unmet"));
            return lines;
        }
    }

    public static final MapCodec<ShopCatalogOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("revision", "").forGetter(ShopCatalogOutput::revision),
            Codec.STRING.optionalFieldOf("query", "").forGetter(ShopCatalogOutput::query),
            Offer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(ShopCatalogOutput::offers),
            Codec.BOOL.optionalFieldOf("truncated", false).forGetter(ShopCatalogOutput::truncated),
            ShopTrade.CODEC.optionalFieldOf("trade").forGetter(ShopCatalogOutput::trade),
            Codec.BOOL.optionalFieldOf("searchRequired", false).forGetter(ShopCatalogOutput::searchRequired),
            Codec.intRange(0, 1000000).optionalFieldOf("nextOffset").forGetter(ShopCatalogOutput::nextOffset),
            ShopRotationOutput.CODEC.listOf().optionalFieldOf("rotations", List.of()).forGetter(ShopCatalogOutput::rotations)
    ).apply(i, ShopCatalogOutput::new));

    public ShopCatalogOutput {
        offers = List.copyOf(offers);
        rotations = List.copyOf(rotations);
    }

    @Override
    public MapCodec<ShopCatalogOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_catalog"))
                .title(this.trade.map(value -> value.key("catalog")).orElse("system.result.shop"))
                .summary(this.trade.filter(value -> value == ShopTrade.BUYBACK).isPresent() ? "system.result.buyback" : "system.result.shop")
                .put("catalog_title", Component.translatable(this.trade.map(value -> value.key("catalog")).orElse("system.ui.nav_shop")))
                .put("revision", this.revision).put("query", this.query)
                .put("truncated", this.truncated).put("search_required", this.searchRequired)
                .enable("no_matches", this.offers.isEmpty() && !this.searchRequired);
        builder.list("rotations", this.rotations, (prefix, rotation) -> builder.put(prefix + "description", rotation.description())
                .put(prefix + "source", rotation.source().toString()));
        builder.list("offers", this.offers, (prefix, entry) -> builder.contents(prefix, entry.contents().stream().limit(Math.max(0, 2 - entry.items().size())).toList())
                .enable(prefix + "condition_unmet", !entry.available())
                .enable(prefix + "buyback", entry.trade() == ShopTrade.BUYBACK)
                .put(prefix + "label", entry.label()).put(prefix + "items", entry.items().stream().limit(2).toList())
                .put(prefix + "trade", Component.translatable(entry.trade().key("label")))
                .put(prefix + "tooltip", new CardText.ShelfTooltip(entry, 0)));
        // Cell order matches the offer list; the grid binds each cell to the matching action callback.
        for (int index = 0; index < this.offers.size(); index++)
            builder.serverButton("offer." + index, "", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_quote"),
                    Map.of("offer", this.offers.get(index).id(), "revision", this.revision,
                            "trade", this.offers.get(index).trade().getSerializedName()), CardButton.Placement.BODY, CardButton.Behavior.REPLACE);
        this.nextOffset.ifPresent(offset -> builder.serverButton("next", "system.action.more_search_results",
                        Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, this.trade.orElse(ShopTrade.PURCHASE) == ShopTrade.BUYBACK ? "buyback" : "shop"),
                        Map.of("query", this.query, "offset", Integer.toString(offset)), CardButton.Placement.FOOTER, CardButton.Behavior.PUSH)
                .actionParameter("footer", true));
        if (!this.rotations.isEmpty())
            builder.serverButton("refresh", "system.shop.rotation.refresh", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID,
                            this.trade.orElse(ShopTrade.PURCHASE) == ShopTrade.BUYBACK ? "buyback" : "shop"), Map.of("query", this.query))
                    .actionParameter("footer", true);
        return builder;
    }
}
