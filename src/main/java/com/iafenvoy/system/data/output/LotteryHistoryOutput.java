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

import java.time.Instant;
import java.util.*;

/**
 * Only the requesting player's settled results are exposed; unpaid selected prizes stay private.
 */
public record LotteryHistoryOutput(List<Entry> entries, Optional<Identifier> pool, int offset, int total,
                                   Optional<String> order) implements CardData {
    public record Result(Component label, Component contents, boolean guaranteed) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Result::label),
                ComponentSerialization.CODEC.fieldOf("contents").forGetter(Result::contents),
                Codec.BOOL.fieldOf("guaranteed").forGetter(Result::guaranteed)
        ).apply(i, Result::new));
    }

    public record Entry(String id, Identifier pool, Component label, long created, String state, int count,
                        List<Result> rolls) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id), Identifier.CODEC.fieldOf("pool").forGetter(Entry::pool),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Entry::label), Codec.LONG.fieldOf("created").forGetter(Entry::created),
                Codec.STRING.fieldOf("state").forGetter(Entry::state), Codec.INT.fieldOf("count").forGetter(Entry::count),
                Result.CODEC.listOf().fieldOf("rolls").forGetter(Entry::rolls)
        ).apply(i, Entry::new));
    }

    public static final MapCodec<LotteryHistoryOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(LotteryHistoryOutput::entries),
            Identifier.CODEC.optionalFieldOf("pool").forGetter(LotteryHistoryOutput::pool),
            Codec.INT.fieldOf("offset").forGetter(LotteryHistoryOutput::offset), Codec.INT.fieldOf("total").forGetter(LotteryHistoryOutput::total),
            Codec.STRING.optionalFieldOf("order").forGetter(LotteryHistoryOutput::order)
    ).apply(i, LotteryHistoryOutput::new));

    @Override
    public MapCodec<LotteryHistoryOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_history"))
                .title("system.lottery.history").summary("system.lottery.history")
                .enable("detail", this.order.isPresent())
                .put("range", Component.translatable(this.order.isPresent() ? "system.lottery.result_range" : "system.lottery.history_range", this.total == 0 ? 0 : this.offset + 1,
                        this.offset + (this.order.isPresent() ? this.entries.stream().mapToInt(entry -> entry.rolls().size()).sum() : this.entries.size()), this.total));
        builder.list("orders", this.entries, (prefix, order) -> {
            builder.put(prefix + "label", order.label()).put(prefix + "id", order.id()).put(prefix + "pool", order.pool().toString())
                    .put(prefix + "time", Instant.ofEpochMilli(order.created()).toString())
                    .put(prefix + "state", Component.translatable("system.lottery.state." + order.state().toLowerCase(Locale.ROOT)))
                    .put(prefix + "count", Component.translatable("system.lottery.count", order.count()));
            builder.put(prefix + "heading", order.label().copy().append(" · ")
                    .append(Component.translatable("system.lottery.count", order.count())).append(" · ")
                    .append(Component.translatable("system.lottery.state." + order.state().toLowerCase(Locale.ROOT))));
            if (this.order.isEmpty()) {
                Map<String, String> arguments = new HashMap<>();
                arguments.put("order", order.id());
                this.pool.ifPresent(pool -> arguments.put("pool", pool.toString()));
                builder.serverButton("order." + order.id(), "system.lottery.view_record", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_history"),
                                arguments, CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                        .actionParameter("label", order.label().copy().append(" · ").append(Component.translatable("system.lottery.count", order.count())));
            }
            builder.list(prefix + "rolls", order.rolls(), (key, roll) -> builder.put(key + "label", roll.label())
                    .put(key + "guaranteed", roll.guaranteed()).put(key + "contents", roll.contents()));
        });
        if (this.offset > 0) this.page(builder, "previous", Math.max(0, this.offset - 20));
        if (this.offset + 20 < this.total) this.page(builder, "next", this.offset + 20);
        if (this.order.isPresent())
            builder.serverButton("back", "system.lottery.back_history", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_history"),
                    this.pool.map(pool -> Map.of("pool", pool.toString())).orElse(Map.of()), CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("pager", true);
        builder.serverButton("catalog", "system.ui.nav_lottery", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery"),
                Map.of(), CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("pager", true);
        return builder;
    }

    private void page(CardBuilder builder, String direction, int offset) {
        Map<String, String> arguments = new HashMap<>();
        this.pool.ifPresent(pool -> arguments.put("pool", pool.toString()));
        this.order.ifPresent(order -> arguments.put("order", order));
        arguments.put("offset", Integer.toString(offset));
        builder.serverButton(direction, "system.action." + direction + "_batch", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_history"),
                arguments, CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("pager", true);
    }
}
