package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record IsEquipableCondition(Optional<EquipmentSlot> slot) implements ItemCondition {
    public static final MapCodec<IsEquipableCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EquipmentSlot.CODEC.optionalFieldOf("slot").forGetter(IsEquipableCondition::slot)
    ).apply(i, IsEquipableCondition::new));

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        // 1.21.1 expresses equippability through the Equipable item interface rather than a data component.
        Equipable equipable = Equipable.get(ctx.stack());
        return equipable != null && this.slot.map(value -> value == equipable.getEquipmentSlot()).orElse(true);
    }

    @Override
    public @NotNull MapCodec<IsEquipableCondition> codec() {
        return CODEC;
    }
}
