package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.progress.EarnedReward;
import com.iafenvoy.system.data.space.SpaceStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public record RecordRewardsOutput(List<EarnedReward> rewards, int offset, boolean hasMore, long pending,
                                  Map<String, Long> counts) implements CardData {
    public static final int PAGE_SIZE = 18;
    public static final MapCodec<RecordRewardsOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EarnedReward.CODEC.listOf().fieldOf("rewards").forGetter(RecordRewardsOutput::rewards),
            Codec.INT.fieldOf("offset").forGetter(RecordRewardsOutput::offset),
            Codec.BOOL.fieldOf("hasMore").forGetter(RecordRewardsOutput::hasMore),
            Codec.LONG.fieldOf("pending").forGetter(RecordRewardsOutput::pending),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("counts").forGetter(RecordRewardsOutput::counts)
    ).apply(i, RecordRewardsOutput::new));

    public RecordRewardsOutput {
        rewards = List.copyOf(rewards);
        counts = Map.copyOf(counts);
    }

    @Override
    public MapCodec<RecordRewardsOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(id("record_rewards")).title("system.records.title").summary("system.records.title")
                .put("pending", Component.translatable("system.records.pending", this.pending));
        builder.list("rewards", this.rewards, (prefix, earned) -> builder.put(prefix + "reward_label", earned.reward().value().label())
                .put(prefix + "preview", earned.reward().value().items().stream().limit(2).map(ItemPreviewOutput::of).toList())
                .put(prefix + "reward_text", CardBuilder.join(earned.reward().value().stacks().stream().map(SpaceStack::label).toList(), " · "))
                .put(prefix + "status", Component.translatable(earned.transferred() ? "system.records.transferred" : "system.records.available")));
        for (int index = 0; index < this.rewards.size(); index++) {
            EarnedReward earned = this.rewards.get(index);
            builder.serverButton("claim." + index, "system.records.claim", id("record_claim"), Map.of("entitlement", earned.id()),
                    CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("disabled", earned.transferred());
        }
        builder.put("counts", this.counts.entrySet().stream().filter(entry -> !entry.getKey().contains("|"))
                .sorted(Map.Entry.comparingByKey()).map(entry -> Component.translatable("system.records.count",
                        Component.translatable("system.fact." + Identifier.parse(entry.getKey()).getPath()), entry.getValue())).toList());
        if (this.offset > 0)
            builder.serverButton("previous", "system.action.previous", id("record_query"), Map.of("offset", Integer.toString(Math.max(0, this.offset - PAGE_SIZE)))).actionParameter("footer", true);
        if (this.hasMore)
            builder.serverButton("next", "system.action.more_items", id("record_query"), Map.of("offset", Integer.toString(this.offset + PAGE_SIZE))).actionParameter("footer", true);
        return builder.serverButton("refresh", "system.action.job_refresh", id("record_query"), Map.of()).actionParameter("footer", true)
                .serverButton("space", "system.ui.nav_inbox", id("inbox"), Map.of()).actionParameter("footer", true);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, path);
    }
}
