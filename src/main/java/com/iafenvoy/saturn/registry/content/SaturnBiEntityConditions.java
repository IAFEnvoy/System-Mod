package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.NeverCondition;
import com.iafenvoy.saturn.data.condition.builtin.bientity.*;
import com.iafenvoy.saturn.data.condition.builtin.bientity.meta.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code bi_entity_condition_type} entries.
 */
public final class SaturnBiEntityConditions {
    public static final DeferredRegister<MapCodec<? extends BiEntityCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.BI_ENTITY_CONDITION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("always", () -> AlwaysCondition.CODEC);
        REGISTRY.register("never", () -> NeverCondition.CODEC);
        REGISTRY.register("and", () -> AndBiEntityCondition.CODEC);
        REGISTRY.register("or", () -> OrCondition.CODEC);
        REGISTRY.register("not", () -> NotCondition.CODEC);
        REGISTRY.register("chance", () -> ChanceCondition.CODEC);
        REGISTRY.register("actor_condition", () -> ActorCondition.CODEC);
        REGISTRY.register("target_condition", () -> TargetCondition.CODEC);
        REGISTRY.register("both", () -> BothCondition.CODEC);
        REGISTRY.register("either", () -> EitherCondition.CODEC);
        REGISTRY.register("mutual", () -> MutualCondition.CODEC);
        REGISTRY.register("undirected", () -> UndirectedCondition.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("distance", () -> DistanceBiEntityCondition.CODEC);
        REGISTRY.register("team", () -> TeamBiEntityCondition.CODEC);
        REGISTRY.register("relation", () -> RelationBiEntityCondition.CODEC);
        REGISTRY.register("can_see", () -> CanSeeCondition.CODEC);
        REGISTRY.register("riding_recursive", () -> RidingRecursiveCondition.CODEC);
        REGISTRY.register("same_team", () -> SameTeamCondition.CODEC);
        REGISTRY.register("relative_rotation", () -> RelativeRotationCondition.CODEC);
    }

    private SaturnBiEntityConditions() {
    }
}
