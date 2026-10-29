package com.iafenvoy.saturn.data.trigger;

import com.iafenvoy.saturn.Saturn;
import net.minecraft.resources.ResourceLocation;

/**
 * Canonical signal identifiers emitted by Saturn's event bridges.
 */
public final class TriggerSignals {
    public static final ResourceLocation TICK = id("tick");
    public static final ResourceLocation ATTACK = id("attack");
    public static final ResourceLocation HURT = id("hurt");
    public static final ResourceLocation KILL = id("kill");
    public static final ResourceLocation BLOCK_BREAK = id("block_break");
    public static final ResourceLocation BLOCK_USE = id("block_use");
    public static final ResourceLocation ITEM_USE = id("item_use");
    public static final ResourceLocation EQUIP = id("equip");
    public static final ResourceLocation DEATH = id("death");
    public static final ResourceLocation BREAKTHROUGH = id("breakthrough");
    public static final ResourceLocation PROGRESSION_LEVEL = id("progression_level");

    // Ported vanilla triggers: the signal keeps the vanilla id, but Saturn's own hooks publish it instead of
    // the call sites vanilla fires from.
    public static final ResourceLocation CONSUME_ITEM = id("consume_item");
    public static final ResourceLocation BREWED_POTION = id("brewed_potion");
    public static final ResourceLocation TAME_ANIMAL = id("tame_animal");
    public static final ResourceLocation BRED_ANIMALS = id("bred_animals");
    public static final ResourceLocation VILLAGER_TRADE = id("villager_trade");
    public static final ResourceLocation USED_TOTEM = id("used_totem");
    public static final ResourceLocation STARTED_RIDING = id("started_riding");
    public static final ResourceLocation CHANGED_DIMENSION = id("changed_dimension");
    public static final ResourceLocation EFFECTS_CHANGED = id("effects_changed");
    public static final ResourceLocation LIGHTNING_STRIKE = id("lightning_strike");
    public static final ResourceLocation PLAYER_HURT_ENTITY = id("player_hurt_entity");
    public static final ResourceLocation ENTITY_HURT_PLAYER = id("entity_hurt_player");
    public static final ResourceLocation PLAYER_KILLED_ENTITY = id("player_killed_entity");
    public static final ResourceLocation ENTITY_KILLED_PLAYER = id("entity_killed_player");
    public static final ResourceLocation SHOT_CROSSBOW = id("shot_crossbow");
    public static final ResourceLocation PLAYER_INTERACTED_WITH_ENTITY = id("player_interacted_with_entity");
    public static final ResourceLocation FISHING_ROD_HOOKED = id("fishing_rod_hooked");
    public static final ResourceLocation THROWN_ITEM_PICKED_UP_BY_PLAYER = id("thrown_item_picked_up_by_player");
    public static final ResourceLocation ENTER_BLOCK = id("enter_block");
    public static final ResourceLocation LOCATION = id("location");
    public static final ResourceLocation LEVITATION = id("levitation");
    public static final ResourceLocation FALL_FROM_HEIGHT = id("fall_from_height");
    public static final ResourceLocation FALL_AFTER_EXPLOSION = id("fall_after_explosion");
    public static final ResourceLocation RIDE_ENTITY_IN_LAVA = id("ride_entity_in_lava");
    public static final ResourceLocation NETHER_TRAVEL = id("nether_travel");
    public static final ResourceLocation INVENTORY_CHANGED = id("inventory_changed");
    public static final ResourceLocation FILLED_BUCKET = id("filled_bucket");
    public static final ResourceLocation ITEM_DURABILITY_CHANGED = id("item_durability_changed");
    public static final ResourceLocation USING_ITEM = id("using_item");
    public static final ResourceLocation SLEPT_IN_BED = id("slept_in_bed");

    private TriggerSignals() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Saturn.MOD_ID, path);
    }
}
