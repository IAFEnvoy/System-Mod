package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.catalog.LotteryPity;
import com.iafenvoy.system.data.catalog.ShopPurchaseLimit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Player-private quota and pity snapshot; the reservation transaction checks everything again.
 */
public record LotteryStatusOutput(List<Integer> counts, int count, Optional<ShopPurchaseLimit> limit, int used,
                                  Optional<LotteryPity> pity, int misses, long cooldown, boolean pending,
                                  Component targets,
                                  Map<Identifier, Integer> wins, int remaining, boolean pityAvailable) {
    public static final Codec<LotteryStatusOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("counts").forGetter(LotteryStatusOutput::counts),
            Codec.INT.fieldOf("count").forGetter(LotteryStatusOutput::count),
            ShopPurchaseLimit.CODEC.optionalFieldOf("limit").forGetter(LotteryStatusOutput::limit),
            Codec.INT.fieldOf("used").forGetter(LotteryStatusOutput::used),
            LotteryPity.CODEC.optionalFieldOf("pity").forGetter(LotteryStatusOutput::pity),
            Codec.INT.fieldOf("misses").forGetter(LotteryStatusOutput::misses),
            Codec.LONG.fieldOf("cooldown").forGetter(LotteryStatusOutput::cooldown),
            Codec.BOOL.fieldOf("pending").forGetter(LotteryStatusOutput::pending),
            ComponentSerialization.CODEC.fieldOf("targets").forGetter(LotteryStatusOutput::targets),
            Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, Integer.MAX_VALUE)).optionalFieldOf("wins", Map.of()).forGetter(LotteryStatusOutput::wins),
            Codec.INT.optionalFieldOf("remaining", Integer.MAX_VALUE).forGetter(LotteryStatusOutput::remaining),
            Codec.BOOL.optionalFieldOf("pityAvailable", true).forGetter(LotteryStatusOutput::pityAvailable)
    ).apply(i, LotteryStatusOutput::new));

    public LotteryStatusOutput {
        counts = List.copyOf(counts);
        wins = Map.copyOf(wins);
    }

    public boolean available(int count) {
        return count > 0 && count <= this.remaining && !this.pending && this.cooldown == 0 && this.limit.map(limit -> (long) this.used + count <= limit.count()).orElse(true);
    }

    public List<Component> descriptions() {
        ArrayList<Component> lines = new ArrayList<Component>();
        this.limit.ifPresent(limit -> lines.add(Component.translatable("system.lottery.limit",
                Component.translatable("system.ui.shop_limit_period." + limit.period().getSerializedName()),
                limit.count(), Math.max(0, limit.count() - this.used))));
        if (this.pity.isPresent() && this.pityAvailable) {
            LotteryPity pity = this.pity.get();
            lines.add(Component.translatable("system.lottery.pity", Math.max(1, pity.count() - this.misses), pity.count()));
            lines.add(Component.translatable("system.lottery.pity_rewards", this.targets));
            lines.add(Component.translatable("system.lottery.base_odds"));
        }
        if (this.pity.isPresent() && !this.pityAvailable)
            lines.add(Component.translatable("system.lottery.pity_exhausted"));
        if (!this.wins.isEmpty()) lines.add(Component.translatable("system.lottery.limited_odds"));
        if (this.remaining != Integer.MAX_VALUE)
            lines.add(Component.translatable("system.lottery.remaining", this.remaining));
        if (this.count > this.remaining) lines.add(Component.translatable("system.error.lottery_exhausted"));
        if (this.cooldown > 0) lines.add(Component.translatable("system.lottery.cooldown", this.cooldown));
        if (this.pending) lines.add(Component.translatable("system.lottery.pending"));
        return lines;
    }
}
