package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.condition.NeverCondition;
import com.iafenvoy.saturn.data.condition.builtin.block.*;
import com.iafenvoy.saturn.data.condition.builtin.block.meta.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code block_condition_type} entries.
 */
public final class SaturnBlockConditions {
    public static final DeferredRegister<MapCodec<? extends BlockCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.BLOCK_CONDITION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("always", () -> AlwaysCondition.CODEC);
        REGISTRY.register("never", () -> NeverCondition.CODEC);
        REGISTRY.register("and", () -> AndBlockCondition.CODEC);
        REGISTRY.register("or", () -> OrCondition.CODEC);
        REGISTRY.register("not", () -> NotCondition.CODEC);
        REGISTRY.register("chance", () -> ChanceCondition.CODEC);
        REGISTRY.register("offset", () -> OffsetCondition.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("block_id", () -> BlockIdCondition.CODEC);
        REGISTRY.register("block_tag", () -> BlockTagCondition.CODEC);
        REGISTRY.register("biome_tag", () -> BiomeTagBlockCondition.CODEC);
        REGISTRY.register("hardness", () -> HardnessCondition.CODEC);
        REGISTRY.register("height", () -> HeightCondition.CODEC);
        REGISTRY.register("light_level", () -> LightLevelCondition.CODEC);
        REGISTRY.register("slipperiness", () -> SlipperinessCondition.CODEC);
        REGISTRY.register("blast_resistance", () -> BlastResistanceCondition.CODEC);
        REGISTRY.register("movement_blocking", () -> MovementBlockingCondition.CODEC);
        REGISTRY.register("adjacent", () -> AdjacentCondition.CODEC);
    }

    private SaturnBlockConditions() {
    }
}
