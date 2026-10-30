package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.condition.NeverCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.*;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.AndEntityCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.ChanceCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.NotEntityCondition;
import com.iafenvoy.saturn.data.condition.builtin.entity.meta.OrEntityCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code entity_condition_type} entries.
 */
public final class SaturnEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_CONDITION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("always", () -> AlwaysCondition.CODEC);
        REGISTRY.register("never", () -> NeverCondition.CODEC);
        REGISTRY.register("and", () -> AndEntityCondition.CODEC);
        REGISTRY.register("or", () -> OrEntityCondition.CODEC);
        REGISTRY.register("not", () -> NotEntityCondition.CODEC);
        REGISTRY.register("chance", () -> ChanceCondition.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("sneaking", () -> SneakingEntityCondition.CODEC);
        REGISTRY.register("partner", () -> PartnerEntityCondition.CODEC);
        REGISTRY.register("entity_tag", () -> EntityTypeTagCondition.CODEC);
        REGISTRY.register("air", () -> AirCondition.CODEC);
        REGISTRY.register("dimension", () -> DimensionCondition.CODEC);
        REGISTRY.register("entity_type", () -> EntityTypeCondition.CODEC);
        REGISTRY.register("fall_distance", () -> FallDistanceCondition.CODEC);
        REGISTRY.register("glowing", () -> GlowingCondition.CODEC);
        REGISTRY.register("health", () -> HealthCondition.CODEC);
        REGISTRY.register("exposed_to_sky", () -> ExposedToSkyCondition.CODEC);
        REGISTRY.register("food_level", () -> FoodLevelCondition.CODEC);
        REGISTRY.register("mob_effect", () -> MobEffectCondition.CODEC);
        REGISTRY.register("on_block", () -> OnBlockCondition.CODEC);
        REGISTRY.register("time_of_day", () -> TimeOfDayCondition.CODEC);
        REGISTRY.register("using_item", () -> UsingItemCondition.CODEC);
        REGISTRY.register("brightness", () -> BrightnessCondition.CODEC);
        REGISTRY.register("exposed_to_sun", () -> ExposedToSunCondition.CODEC);
        REGISTRY.register("experience_level", () -> ExperienceLevelCondition.CODEC);
        REGISTRY.register("experience_points", () -> ExperiencePointsCondition.CODEC);
        REGISTRY.register("relative_health", () -> RelativeHealthCondition.CODEC);
        REGISTRY.register("saturation_level", () -> SaturationLevelCondition.CODEC);
        REGISTRY.register("team", () -> TeamCondition.CODEC);
        REGISTRY.register("attribute", () -> AttributeCondition.CODEC);
        REGISTRY.register("block_collision", () -> BlockCollisionCondition.CODEC);
        REGISTRY.register("can_have_effect", () -> CanHaveEffectCondition.CODEC);
        REGISTRY.register("gamemode", () -> GamemodeCondition.CODEC);
        REGISTRY.register("passenger", () -> PassengerCondition.CODEC);
        REGISTRY.register("attack_cooldown", () -> AttackCooldownCondition.CODEC);
        REGISTRY.register("equipped_item", () -> EquippedItemCondition.CODEC);
        REGISTRY.register("main_hand_item", () -> MainHandItemCondition.CODEC);
        REGISTRY.register("in_block", () -> InBlockCondition.CODEC);
        REGISTRY.register("in_block_anywhere", () -> InBlockAnywhereCondition.CODEC);
        REGISTRY.register("scoreboard", () -> ScoreboardCondition.CODEC);
        REGISTRY.register("riding", () -> RidingCondition.CODEC);
        REGISTRY.register("riding_recursive", () -> RidingRecursiveCondition.CODEC);
        REGISTRY.register("passenger_recursive", () -> PassengerRecursiveCondition.CODEC);
        REGISTRY.register("riding_root", () -> RidingRootCondition.CODEC);
    }

    private SaturnEntityConditions() {
    }
}
