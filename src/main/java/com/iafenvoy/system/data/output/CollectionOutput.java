package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Only a bounded display page crosses the network; item predicates and conditions remain server-side.
 */
public record CollectionOutput(String collection, Component label, Component description, List<Row> rows,
                               int registered, int total, int offset, boolean hasMore, boolean locked,
                               List<Component> milestones) implements CardData {
    public static final int PAGE_SIZE = 18;
    public static final MapCodec<CollectionOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("collection").forGetter(CollectionOutput::collection),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(CollectionOutput::label),
            ComponentSerialization.CODEC.fieldOf("description").forGetter(CollectionOutput::description),
            Row.CODEC.listOf().fieldOf("rows").forGetter(CollectionOutput::rows),
            Codec.INT.fieldOf("registered").forGetter(CollectionOutput::registered),
            Codec.INT.fieldOf("total").forGetter(CollectionOutput::total),
            Codec.INT.fieldOf("offset").forGetter(CollectionOutput::offset),
            Codec.BOOL.fieldOf("hasMore").forGetter(CollectionOutput::hasMore),
            Codec.BOOL.fieldOf("locked").forGetter(CollectionOutput::locked),
            ComponentSerialization.CODEC.listOf().fieldOf("milestones").forGetter(CollectionOutput::milestones)
    ).apply(i, CollectionOutput::new));

    public CollectionOutput {
        rows = List.copyOf(rows);
        milestones = List.copyOf(milestones);
    }

    public record Row(Identifier id, Component label, Optional<ItemStackTemplate> icon, boolean complete,
                      int registered, int total) {
        public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Row::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Row::label),
                ItemStackTemplate.CODEC.optionalFieldOf("icon").forGetter(Row::icon),
                Codec.BOOL.fieldOf("complete").forGetter(Row::complete),
                Codec.INT.fieldOf("registered").forGetter(Row::registered),
                Codec.INT.fieldOf("total").forGetter(Row::total)
        ).apply(i, Row::new));
    }

    @Override
    public MapCodec<CollectionOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        boolean detail = !this.collection.isEmpty();
        CardBuilder builder = CardBuilder.template(id("collection")).title("system.collection.title").summary(this.label)
                .put("label", this.label)
                .put("description", this.description).enable("detail", detail).enable("locked", this.locked)
                .put("progress", this.total == 0 ? 0 : 100.0 * this.registered / this.total)
                .put("counts", Component.translatable("system.collection.counts", this.registered, this.total))
                .put("milestones", this.milestones);
        builder.list("rows", this.rows, (prefix, row) -> {
            builder.put(prefix + "label", row.label).enable(prefix + "complete", row.complete)
                    .put(prefix + "status", Component.translatable(detail ? row.complete ? "system.collection.registered" : "system.collection.missing" : "system.collection.counts", row.registered, row.total));
            row.icon.ifPresent(icon -> builder.put(prefix + "item", ItemPreviewOutput.of(icon)));
        });
        if (!detail) {
            for (int index = 0; index < this.rows.size(); index++)
                builder.serverButton("open." + index, "system.collection.open", id("collection_query"), Map.of("collection", this.rows.get(index).id.toString()),
                        CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("label", this.rows.get(index).label);
        } else {
            builder.serverButton("register", "system.collection.register", id("collection_register"), Map.of("collection", this.collection)).actionParameter("footer", true).actionParameter("disabled", this.locked);
            builder.serverButton("back", "system.action.back", id("collection_query"), Map.of()).actionParameter("footer", true);
        }
        if (this.offset > 0)
            builder.serverButton("previous", "system.action.previous", id("collection_query"), Map.of("collection", this.collection, "offset", Integer.toString(Math.max(0, this.offset - PAGE_SIZE)))).actionParameter("footer", true);
        if (this.hasMore)
            builder.serverButton("next", "system.action.more_items", id("collection_query"), Map.of("collection", this.collection, "offset", Integer.toString(this.offset + PAGE_SIZE))).actionParameter("footer", true);
        return builder.serverButton("records", "system.records.title", id("record_query"), Map.of()).actionParameter("footer", true);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, path);
    }
}
