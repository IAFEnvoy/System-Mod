package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardText;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * One shelf card lists a batch of browsable pools; each cell dispatches the existing preview action, so odds and costs
 * stay server-side until the player opens a specific pool.
 */
public record LotteryCatalogOutput(String revision, String query, List<Pool> pools,
                                   int offset, int total, Optional<String> searchRef, int itemOffset,
                                   Optional<Integer> nextItemOffset) implements CardData {
    /**
     * One pool prize as the shelf tooltip shows it: its name, odds and the previewed items of that reward.
     */
    public record Prize(Component label, String chance, List<ItemPreviewOutput> items, List<SpaceStack> contents,
                        int maxWins) {
        public static final Codec<Prize> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Prize::label),
                Codec.STRING.optionalFieldOf("chance", "").forGetter(Prize::chance),
                ItemPreviewOutput.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Prize::items),
                Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(Prize::contents),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("maxWins", 0).forGetter(Prize::maxWins)
        ).apply(i, Prize::new));

        public Prize {
            items = List.copyOf(items);
            contents = List.copyOf(contents);
        }
    }

    public record Pool(String id, Component label, int prizeCount, List<Prize> prizes,
                       boolean available, Optional<Component> activity) implements ShelfEntry {
        private static final int MAX_ICONS = 2;

        public static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Pool::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Pool::label),
                Codec.INT.optionalFieldOf("prizeCount", 0).forGetter(Pool::prizeCount),
                Prize.CODEC.listOf().optionalFieldOf("prizes", List.of()).forGetter(Pool::prizes),
                Codec.BOOL.optionalFieldOf("available", true).forGetter(Pool::available),
                ComponentSerialization.CODEC.optionalFieldOf("activity").forGetter(Pool::activity)
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

        @Override
        public List<SpaceStack> contents() {
            List<SpaceStack> previews = new ArrayList<>();
            for (int index = this.prizes.size() - 1; index >= 0; index--)
                previews.addAll(this.prizes.get(index).contents());
            return previews.stream().limit(Math.max(0, MAX_ICONS - this.items().size())).toList();
        }

        /**
         * Every preview of one pool shares the same tooltip: the whole odds list, so a single hover compares the pool.
         */
        @Override
        public List<Component> tooltip(int index) {
            List<Component> lines = new ArrayList<>();
            this.activity.ifPresent(lines::add);
            if (!this.available) lines.add(Component.translatable("system.ui.condition_unmet"));
            lines.add(Component.translatable("system.lottery.catalog_odds").withStyle(ChatFormatting.DARK_GRAY));
            if (this.prizeCount > this.prizes.size())
                lines.add(Component.translatable("system.ui.lottery_tooltip_top", this.prizeCount, this.prizes.size())
                        .withStyle(ChatFormatting.DARK_GRAY));
            for (Prize prize : this.prizes) {
                lines.add(prize.label().copy().append("  ")
                        .append(Component.translatable("system.ui.lottery_odds", prize.chance() + "%").withStyle(ChatFormatting.DARK_GRAY)));
                if (prize.maxWins() > 0)
                    lines.add(Component.translatable("system.lottery.reward_max_wins", prize.maxWins()).withStyle(ChatFormatting.GRAY));
                for (ItemPreviewOutput preview : prize.items()) {
                    ItemStack stack = preview.item().create();
                    if (stack.isEmpty()) continue;
                    lines.add(Component.literal("  ").append(Component.translatable("system.ui.item_amount", stack.getHoverName(), preview.amount()))
                            .withStyle(ChatFormatting.GRAY));
                }
                prize.contents().forEach(content -> lines.add(Component.literal("  ").append(content.label()).withStyle(ChatFormatting.GRAY)));
            }
            return lines;
        }
    }

    public static final MapCodec<LotteryCatalogOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("revision", "").forGetter(LotteryCatalogOutput::revision),
            Codec.STRING.optionalFieldOf("query", "").forGetter(LotteryCatalogOutput::query),
            Pool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(LotteryCatalogOutput::pools),
            Codec.INT.optionalFieldOf("offset", 0).forGetter(LotteryCatalogOutput::offset),
            Codec.INT.optionalFieldOf("total", 0).forGetter(LotteryCatalogOutput::total),
            Codec.STRING.optionalFieldOf("searchRef").forGetter(LotteryCatalogOutput::searchRef),
            Codec.INT.optionalFieldOf("itemOffset", 0).forGetter(LotteryCatalogOutput::itemOffset),
            Codec.INT.optionalFieldOf("nextItemOffset").forGetter(LotteryCatalogOutput::nextItemOffset)
    ).apply(i, LotteryCatalogOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_catalog");

    public LotteryCatalogOutput {
        pools = List.copyOf(pools);
    }

    public LotteryCatalogOutput(String revision, String query, List<Pool> pools, int offset, int total) {
        this(revision, query, pools, offset, total, Optional.empty(), 0, Optional.empty());
    }

    @Override
    public MapCodec<LotteryCatalogOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.lottery").summary("system.result.lottery")
                .put("revision", this.revision).put("query", this.query).enable("empty", this.pools.isEmpty())
                .put("range", Component.translatable("system.ui.lottery_pool_range", this.offset + 1,
                        this.offset + this.pools.size(), Math.max(this.total, this.offset + this.pools.size())));
        builder.list("pools", this.pools, (prefix, entry) -> builder.contents(prefix, entry.contents())
                .enable(prefix + "condition_unmet", !entry.available())
                .put(prefix + "label", entry.label()).put(prefix + "items", entry.items().stream().limit(2).toList())
                .put(prefix + "tooltip", new CardText.ShelfTooltip(entry, 0)));
        // Cell order matches the pool list; the grid binds each cell to the matching action callback.
        for (int index = 0; index < this.pools.size(); index++)
            builder.serverButton("pool." + index, "", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"),
                    Map.of("pool", this.pools.get(index).id(), "revision", this.revision), CardButton.Placement.BODY, CardButton.Behavior.REPLACE);
        if (this.offset > 0) this.page(builder, "previous", this.offset - 1);
        if ((long) this.offset + this.pools.size() < this.total)
            this.page(builder, "next", this.offset + this.pools.size());
        this.nextItemOffset.ifPresent(next -> builder.serverButton("more_search", "system.action.more_search_results",
                        Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery"),
                        Map.of("query", this.query, "item_offset", Integer.toString(next)), CardButton.Placement.FOOTER, CardButton.Behavior.PUSH)
                .actionParameter("pager", true));
        builder.serverButton("history", "system.lottery.history", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_history"),
                Map.of(), CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("pager", true);
        return builder;
    }

    private void page(CardBuilder builder, String direction, int offset) {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("query", this.query);
        arguments.put("offset", Integer.toString(offset));
        arguments.put("item_offset", Integer.toString(this.itemOffset));
        this.searchRef.ifPresent(reference -> arguments.put("search_ref", reference));
        builder.serverButton(direction, "system.action." + direction + "_batch", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery"),
                        arguments, CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                .actionParameter("pager", true);
    }
}
