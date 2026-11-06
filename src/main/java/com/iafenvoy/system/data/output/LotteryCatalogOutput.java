package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One shelf card lists every browsable pool; each cell dispatches the existing preview action, so odds and costs
 * stay server-side until the player opens a specific pool.
 */
public record LotteryCatalogOutput(String revision, String query, List<Pool> pools,
                                   boolean truncated) implements CardData {
    /**
     * One pool prize as the shelf tooltip shows it: its name, odds and the previewed items of that reward.
     */
    public record Prize(Component label, String chance, List<ItemPreviewOutput> items) {
        public static final Codec<Prize> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Prize::label),
                Codec.STRING.optionalFieldOf("chance", "").forGetter(Prize::chance),
                ItemPreviewOutput.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Prize::items)
        ).apply(i, Prize::new));

        public Prize {
            items = List.copyOf(items);
        }
    }

    public record Pool(String id, Component label, int prizeCount, List<Prize> prizes) implements ShelfEntry {
        private static final int MAX_ICONS = 2;

        public static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Pool::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Pool::label),
                Codec.INT.optionalFieldOf("prizeCount", 0).forGetter(Pool::prizeCount),
                Prize.CODEC.listOf().optionalFieldOf("prizes", List.of()).forGetter(Pool::prizes)
        ).apply(i, Pool::new));

        public Pool {
            prizes = List.copyOf(prizes);
        }

        /**
         * Cell icons preview the rarest rewards, so the shelf highlights what the pool is worth.
         */
        @Override
        public List<ItemPreviewOutput> items() {
            List<ItemPreviewOutput> previews = new ArrayList<>();
            for (int index = this.prizes.size() - 1; index >= 0; index--) {
                for (ItemPreviewOutput item : this.prizes.get(index).items()) {
                    if (previews.size() >= MAX_ICONS) return previews;
                    previews.add(item);
                }
            }
            return previews;
        }

        /**
         * Every preview of one pool shares the same tooltip: the whole odds list, so a single hover compares the pool.
         */
        @Override
        public List<Component> tooltip(int index) {
            List<Component> lines = new ArrayList<>();
            if (this.prizeCount > this.prizes.size())
                lines.add(Component.translatable("system.ui.lottery_tooltip_top", this.prizeCount, this.prizes.size())
                        .withStyle(ChatFormatting.DARK_GRAY));
            for (Prize prize : this.prizes) {
                lines.add(prize.label().copy().append("  ")
                        .append(Component.translatable("system.ui.lottery_odds", prize.chance() + "%").withStyle(ChatFormatting.DARK_GRAY)));
                for (ItemPreviewOutput preview : prize.items()) {
                    ItemStack stack = preview.item().create();
                    if (stack.isEmpty()) continue;
                    lines.add(Component.literal("  ").append(Component.translatable("system.ui.item_amount", stack.getHoverName(), preview.amount()))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            return lines;
        }
    }

    public static final MapCodec<LotteryCatalogOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("revision", "").forGetter(LotteryCatalogOutput::revision),
            Codec.STRING.optionalFieldOf("query", "").forGetter(LotteryCatalogOutput::query),
            Pool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(LotteryCatalogOutput::pools),
            Codec.BOOL.optionalFieldOf("truncated", false).forGetter(LotteryCatalogOutput::truncated)
    ).apply(i, LotteryCatalogOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_catalog");

    public LotteryCatalogOutput {
        pools = List.copyOf(pools);
    }

    @Override
    public MapCodec<LotteryCatalogOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.lottery").summary("system.result.lottery")
                .put("revision", this.revision).put("query", this.query)
                .put("truncated", this.truncated);
        builder.list("pools", this.pools, (prefix, entry) -> builder.put(prefix + "label", entry.label()).put(prefix + "items", entry.items().stream().limit(2).toList())
                .put(prefix + "tooltip", new CardText.ShelfTooltip(entry, 0)));
        // Cell order matches the pool list; the grid binds each cell to the matching action callback.
        for (int index = 0; index < this.pools.size(); index++)
            builder.serverButton("pool." + index, "", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"),
                    Map.of("pool", this.pools.get(index).id(), "revision", this.revision));
        return builder;
    }
}
