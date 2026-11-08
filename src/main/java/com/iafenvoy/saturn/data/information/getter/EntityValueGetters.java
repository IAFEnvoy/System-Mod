package com.iafenvoy.saturn.data.information.getter;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.data.information.getter.builtin.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Builtin queries, codec validation and dependency provenance. Contains no player or world state.
 */
public final class EntityValueGetters {
    public static final AbsorptionGetter ABSORPTION = AbsorptionGetter.INSTANCE;
    public static final AdvancementsGetter ADVANCEMENTS = AdvancementsGetter.INSTANCE;
    public static final AirGetter AIR = AirGetter.INSTANCE;
    public static final AttributesGetter ATTRIBUTES = AttributesGetter.INSTANCE;
    public static final CarriedItemGetter CARRIED_ITEM = CarriedItemGetter.INSTANCE;
    public static final EffectsGetter EFFECTS = EffectsGetter.INSTANCE;
    public static final EnderChestGetter ENDER_CHEST = EnderChestGetter.INSTANCE;
    public static final EquipmentGetter EQUIPMENT = EquipmentGetter.INSTANCE;
    public static final ExperienceGetter EXPERIENCE = ExperienceGetter.INSTANCE;
    public static final FoodGetter FOOD = FoodGetter.INSTANCE;
    public static final GameModeGetter GAME_MODE = GameModeGetter.INSTANCE;
    public static final HealthGetter HEALTH = HealthGetter.INSTANCE;
    public static final InventoryGetter INVENTORY = InventoryGetter.INSTANCE;
    public static final LocationGetter LOCATION = LocationGetter.INSTANCE;
    public static final MaxHealthGetter MAX_HEALTH = MaxHealthGetter.INSTANCE;
    public static final ProfileGetter PROFILE = ProfileGetter.INSTANCE;
    public static final ScoresGetter SCORES = ScoresGetter.INSTANCE;
    public static final SelectedSlotGetter SELECTED_SLOT = SelectedSlotGetter.INSTANCE;
    public static final StatisticsGetter STATISTICS = StatisticsGetter.INSTANCE;
    public static final TeamGetter TEAM = TeamGetter.INSTANCE;

    private EntityValueGetters() {
    }

    /**
     * Types with no required parameters provide their default capture query.
     */
    public static List<EntityValueGetter<?>> defaults(DynamicOps<JsonElement> ops) {
        List<EntityValueGetter<?>> result = new ArrayList<>();
        SaturnRegistries.ENTITY_VALUE_GETTER.forEach(codec -> codec.codec().parse(ops, ops.emptyMap()).result().ifPresent(result::add));
        return List.copyOf(result);
    }

    public static void requireRegistered(EntityValueGetter<?> getter) {
        Objects.requireNonNull(getter);
        if (!SaturnRegistries.ENTITY_VALUE_GETTER.containsValue(getter.codec()))
            throw new IllegalArgumentException("Unregistered entity value getter codec");
        if (getter.schemaVersion() < 1) throw new IllegalArgumentException("Getter schema must be positive");
        Objects.requireNonNull(getter.valueCodec(), "Getter value codec");
    }

    public static Set<ResourceLocation> sources(EntityValueGetter<?> getter) {
        Set<ResourceLocation> result = new HashSet<>();
        sources(getter, result);
        return Set.copyOf(result);
    }

    private static void sources(EntityValueGetter<?> getter, Set<ResourceLocation> result) {
        if (result.add(getter.type())) getter.dependencies().forEach(dependency -> sources(dependency, result));
    }
}
