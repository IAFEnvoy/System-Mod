package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.condition.NeverCondition;
import com.iafenvoy.saturn.data.condition.builtin.damage.*;
import com.iafenvoy.saturn.data.condition.builtin.damage.meta.AndDamageCondition;
import com.iafenvoy.saturn.data.condition.builtin.damage.meta.ChanceDamageCondition;
import com.iafenvoy.saturn.data.condition.builtin.damage.meta.NotDamageCondition;
import com.iafenvoy.saturn.data.condition.builtin.damage.meta.OrDamageCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code damage_condition_type} entries.
 */
public final class SaturnDamageConditions {
    public static final DeferredRegister<MapCodec<? extends DamageCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.DAMAGE_CONDITION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("always", () -> AlwaysCondition.CODEC);
        REGISTRY.register("never", () -> NeverCondition.CODEC);
        REGISTRY.register("and", () -> AndDamageCondition.CODEC);
        REGISTRY.register("or", () -> OrDamageCondition.CODEC);
        REGISTRY.register("not", () -> NotDamageCondition.CODEC);
        REGISTRY.register("chance", () -> ChanceDamageCondition.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("amount_range", () -> DamageAmountRangeCondition.CODEC);
        REGISTRY.register("directness", () -> DirectDamageCondition.CODEC);
        REGISTRY.register("damage_type", () -> DamageTypeCondition.CODEC);
        REGISTRY.register("damage_type_tag", () -> DamageTypeTagCondition.CODEC);
        REGISTRY.register("fire", () -> FireDamageCondition.CODEC);
        REGISTRY.register("magic", () -> MagicDamageCondition.CODEC);
        REGISTRY.register("projectile", () -> ProjectileDamageCondition.CODEC);
    }

    private SaturnDamageConditions() {
    }
}
