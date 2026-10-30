package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.condition.NeverCondition;
import com.iafenvoy.saturn.data.condition.builtin.item.*;
import com.iafenvoy.saturn.data.condition.builtin.item.meta.AndItemCondition;
import com.iafenvoy.saturn.data.condition.builtin.item.meta.ChanceCondition;
import com.iafenvoy.saturn.data.condition.builtin.item.meta.NotCondition;
import com.iafenvoy.saturn.data.condition.builtin.item.meta.OrCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code item_condition_type} entries.
 */
public final class SaturnItemConditions {
    public static final DeferredRegister<MapCodec<? extends ItemCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_CONDITION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("always", () -> AlwaysCondition.CODEC);
        REGISTRY.register("never", () -> NeverCondition.CODEC);
        REGISTRY.register("and", () -> AndItemCondition.CODEC);
        REGISTRY.register("or", () -> OrCondition.CODEC);
        REGISTRY.register("not", () -> NotCondition.CODEC);
        REGISTRY.register("chance", () -> ChanceCondition.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("item_id", () -> ItemIdCondition.CODEC);
        REGISTRY.register("item_tag", () -> ItemTagCondition.CODEC);
        REGISTRY.register("item_matcher", () -> ItemMatcherCondition.CODEC);
        REGISTRY.register("amount", () -> AmountCondition.CODEC);
        REGISTRY.register("fuel", () -> FuelCondition.CODEC);
        REGISTRY.register("is_equipable", () -> IsEquipableCondition.CODEC);
        REGISTRY.register("relative_durability", () -> RelativeDurabilityCondition.CODEC);
        REGISTRY.register("armor_value", () -> ArmorValueCondition.CODEC);
        REGISTRY.register("durability", () -> DurabilityCondition.CODEC);
        REGISTRY.register("on_cooldown", () -> OnCooldownCondition.CODEC);
        REGISTRY.register("ingredient", () -> IngredientCondition.CODEC);
        REGISTRY.register("tool_ability", () -> ToolAbilityCondition.CODEC);
        REGISTRY.register("base_enchantment", () -> BaseEnchantmentCondition.CODEC);
        REGISTRY.register("has_component", () -> HasComponentCondition.CODEC);
        REGISTRY.register("component", () -> ComponentCondition.CODEC);
    }

    private SaturnItemConditions() {
    }
}
