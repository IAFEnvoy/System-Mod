package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.system.data.space.SpaceContent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * Server-only salvage terms. Output amounts are resolved once, before the player confirms destruction.
 */
public record SalvageRule(Component label, ItemPredicate input, int priority, List<Output> outputs,
                          List<Cost> costs, boolean enabled, EntryConditions conditions) implements ConditionalEntry {
    public static final Codec<SalvageRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(SalvageRule::label),
            ItemPredicate.CODEC.fieldOf("input").forGetter(SalvageRule::input),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(SalvageRule::priority),
            Output.CODEC.listOf(1, 36).fieldOf("outputs").forGetter(SalvageRule::outputs),
            Cost.CODEC.listOf(0, 36).optionalFieldOf("costs", List.of()).forGetter(SalvageRule::costs),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(SalvageRule::enabled),
            EntryConditions.CODEC.forGetter(SalvageRule::conditions)
    ).apply(i, SalvageRule::new));

    public SalvageRule {
        label = label.copy();
        outputs = List.copyOf(outputs);
        costs = List.copyOf(costs);
    }

    public record Output(SpaceContent content, NumberProvider amount) {
        private static final Codec<SpaceContent> CONTENT = Codec.of(SpaceContent.CODEC, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<SpaceContent, T>> decode(DynamicOps<T> ops, T input) {
                String type = ops.get(input, "type").flatMap(ops::getStringValue).result().orElse("");
                if (type.equals("system:item")) {
                    Optional<T> count = ops.get(input, "item").flatMap(item -> ops.get(item, "count")).result();
                    if (count.isPresent() && ops.getNumberValue(count.get()).result().map(number -> number.doubleValue() != 1).orElse(true))
                        return DataResult.error(() -> "Salvage item template count must be 1; set output amount instead");
                }
                return SpaceContent.CODEC.decode(ops, input);
            }
        });
        private static final Codec<NumberProvider> AMOUNT = NumberProvider.CODEC.validate(value -> {
            try {
                SalvageNumbers.validate(value);
                return DataResult.success(value);
            } catch (IllegalArgumentException error) {
                return DataResult.error(error::getMessage);
            }
        });
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                CONTENT.fieldOf("content").forGetter(Output::content),
                AMOUNT.optionalFieldOf("amount", new Constant(1)).forGetter(Output::amount)
        ).apply(i, Output::new));
    }
}
