package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.builtin.ItemCost;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fixed delivery requests, distinct from repeatable shop offers.
 */
public record CommissionDefinition(Component label, Component description, Component chapter,
                                   Optional<IconReference> icon, List<Cost> costs, Reward reward,
                                   ShopPurchaseLimit limit, boolean enabled, int order,
                                   EntryConditions conditions) implements ConditionalEntry {
    public static final Codec<CommissionDefinition> CODEC = codec(EntryConditions.CODEC);
    public static final Codec<CommissionDefinition> SNAPSHOT_CODEC = codec(MapCodec.unit(EntryConditions.DEFAULT));

    private static Codec<CommissionDefinition> codec(MapCodec<EntryConditions> conditions) {
        return RecordCodecBuilder.<CommissionDefinition>create(i -> i.group(
                MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(CommissionDefinition::label),
                MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()).forGetter(CommissionDefinition::description),
                MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("chapter", Component.translatable("system.commission.chapter")).forGetter(CommissionDefinition::chapter),
                IconReference.CODEC.optionalFieldOf("icon").forGetter(CommissionDefinition::icon),
                Cost.LIST_CODEC.fieldOf("costs").forGetter(CommissionDefinition::costs),
                Reward.CODEC.fieldOf("reward").forGetter(CommissionDefinition::reward),
                ShopPurchaseLimit.CODEC.optionalFieldOf("limit", new ShopPurchaseLimit(1, ShopPurchaseLimit.Period.LIFETIME)).forGetter(CommissionDefinition::limit),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CommissionDefinition::enabled),
                Codec.INT.optionalFieldOf("order", 0).forGetter(CommissionDefinition::order),
                conditions.forGetter(CommissionDefinition::conditions)
        ).apply(i, CommissionDefinition::new)).validate(value -> value.label.getString().isBlank() || value.costs.isEmpty()
                || value.costs.size() > 16 || value.costs.stream().anyMatch(cost -> !(cost instanceof ItemCost))
                || value.reward.stacks().isEmpty()
                ? DataResult.error(() -> "Commissions require a label, 1..16 saturn:item costs and a nonempty reward") : DataResult.success(value));
    }

    public CommissionDefinition {
        label = label.copy();
        description = description.copy();
        chapter = chapter.copy();
        costs = List.copyOf(costs);
    }
}
