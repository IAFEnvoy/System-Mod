package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.trigger.Trigger;
import com.iafenvoy.saturn.data.trigger.TriggerSignals;
import com.iafenvoy.saturn.data.trigger.builtin.BuiltinTrigger;
import com.iafenvoy.saturn.data.trigger.builtin.VanillaTrigger;
import com.iafenvoy.saturn.data.trigger.builtin.VanillaTrigger.Matcher;
import com.iafenvoy.saturn.data.trigger.builtin.VanillaTriggerMatchers;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code trigger_type} entries.
 */
public final class SaturnTriggers {
    public static final DeferredRegister<MapCodec<? extends Trigger>> REGISTRY = DeferredRegister.create(SaturnRegistries.TRIGGER_TYPE, Saturn.MOD_ID);

    static {
        builtinTrigger("tick");
        builtinTrigger("attack");
        builtinTrigger("hurt");
        builtinTrigger("kill");
        builtinTrigger("block_break");
        builtinTrigger("block_use");
        builtinTrigger("item_use");
        builtinTrigger("equip");
        builtinTrigger("death");
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        port(TriggerSignals.CONSUME_ITEM, ConsumeItemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::consumeItem);
        port(TriggerSignals.BREWED_POTION, BrewedPotionTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::brewedPotion);
        port(TriggerSignals.TAME_ANIMAL, TameAnimalTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::tameAnimal);
        port(TriggerSignals.BRED_ANIMALS, BredAnimalsTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::bredAnimals);
        port(TriggerSignals.VILLAGER_TRADE, TradeTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::villagerTrade);
        port(TriggerSignals.USED_TOTEM, UsedTotemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::usedTotem);
        port(TriggerSignals.STARTED_RIDING, StartRidingTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::startedRiding);
        port(TriggerSignals.CHANGED_DIMENSION, ChangeDimensionTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::changedDimension);
        port(TriggerSignals.EFFECTS_CHANGED, EffectsChangedTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::effectsChanged);
        port(TriggerSignals.LIGHTNING_STRIKE, LightningStrikeTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::lightningStrike);
        port(TriggerSignals.PLAYER_HURT_ENTITY, PlayerHurtEntityTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerHurtEntity);
        port(TriggerSignals.ENTITY_HURT_PLAYER, EntityHurtPlayerTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::entityHurtPlayer);
        port(TriggerSignals.PLAYER_KILLED_ENTITY, KilledTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerKilledEntity);
        port(TriggerSignals.ENTITY_KILLED_PLAYER, KilledTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::entityKilledPlayer);
        port(TriggerSignals.SHOT_CROSSBOW, ShotCrossbowTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::shotCrossbow);
        port(TriggerSignals.PLAYER_INTERACTED_WITH_ENTITY, PlayerInteractTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerInteractedWithEntity);
        port(TriggerSignals.FISHING_ROD_HOOKED, FishingRodHookedTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::fishingRodHooked);
        port(TriggerSignals.THROWN_ITEM_PICKED_UP_BY_PLAYER, PickedUpItemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::thrownItemPickedUpByPlayer);
        port(TriggerSignals.ENTER_BLOCK, EnterBlockTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::enterBlock);
        port(TriggerSignals.LOCATION, PlayerTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerOnly);
        port(TriggerSignals.SLEPT_IN_BED, PlayerTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::playerOnly);
        port(TriggerSignals.LEVITATION, LevitationTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::levitation);
        port(TriggerSignals.FALL_FROM_HEIGHT, DistanceTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::fallFromHeight);
        port(TriggerSignals.FALL_AFTER_EXPLOSION, FallAfterExplosionTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::fallAfterExplosion);
        port(TriggerSignals.RIDE_ENTITY_IN_LAVA, DistanceTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::rideEntityInLava);
        port(TriggerSignals.NETHER_TRAVEL, DistanceTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::netherTravel);
        port(TriggerSignals.INVENTORY_CHANGED, InventoryChangeTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::inventoryChanged);
        port(TriggerSignals.USING_ITEM, UsingItemTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::usingItem);
        port(TriggerSignals.FILLED_BUCKET, FilledBucketTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::filledBucket);
        port(TriggerSignals.ITEM_DURABILITY_CHANGED, ItemDurabilityTrigger.TriggerInstance.CODEC, VanillaTriggerMatchers::itemDurabilityChanged);
    }

    private static void builtinTrigger(String signal) {
        REGISTRY.register(signal, () -> MapCodec.unit(new BuiltinTrigger(TriggerSignals.id(signal))));
    }

    // The type name is derived from the signal and the payload codec is vanilla's own instance codec, so the
    // arguments a definition writes can never disagree with the trigger they mirror.
    private static <T extends CriterionTriggerInstance> void port(ResourceLocation signal, Codec<T> codec, Matcher<T> matcher) {
        REGISTRY.register(signal.getPath(), () -> VanillaTrigger.mapCodec(signal, codec, matcher));
    }

    private SaturnTriggers() {
    }
}
