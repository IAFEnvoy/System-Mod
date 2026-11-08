package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.getter.builtin.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Parameter codecs for entity value getters. Each implementation owns its query and snapshot payload codec.
 */
public final class SaturnEntityValueGetters {
    public static final DeferredRegister<MapCodec<? extends EntityValueGetter<?>>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_VALUE_GETTER, Saturn.MOD_ID);

    static {
        REGISTRY.register("attribute", () -> AttributeGetter.CODEC);
        REGISTRY.register("effect", () -> EffectGetter.CODEC);
        REGISTRY.register("score", () -> ScoreGetter.CODEC);
        REGISTRY.register("absorption", () -> AbsorptionGetter.CODEC);
        REGISTRY.register("advancements", () -> AdvancementsGetter.CODEC);
        REGISTRY.register("air", () -> AirGetter.CODEC);
        REGISTRY.register("attributes", () -> AttributesGetter.CODEC);
        REGISTRY.register("carried_item", () -> CarriedItemGetter.CODEC);
        REGISTRY.register("effects", () -> EffectsGetter.CODEC);
        REGISTRY.register("ender_chest", () -> EnderChestGetter.CODEC);
        REGISTRY.register("equipment", () -> EquipmentGetter.CODEC);
        REGISTRY.register("experience", () -> ExperienceGetter.CODEC);
        REGISTRY.register("food", () -> FoodGetter.CODEC);
        REGISTRY.register("game_mode", () -> GameModeGetter.CODEC);
        REGISTRY.register("health", () -> HealthGetter.CODEC);
        REGISTRY.register("inventory", () -> InventoryGetter.CODEC);
        REGISTRY.register("location", () -> LocationGetter.CODEC);
        REGISTRY.register("max_health", () -> MaxHealthGetter.CODEC);
        REGISTRY.register("profile", () -> ProfileGetter.CODEC);
        REGISTRY.register("scores", () -> ScoresGetter.CODEC);
        REGISTRY.register("selected_slot", () -> SelectedSlotGetter.CODEC);
        REGISTRY.register("statistics", () -> StatisticsGetter.CODEC);
        REGISTRY.register("team", () -> TeamGetter.CODEC);
    }

    private SaturnEntityValueGetters() {
    }
}
