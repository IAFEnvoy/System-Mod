package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.action.NoOpAction;
import com.iafenvoy.saturn.data.action.SimpleActions;
import com.iafenvoy.saturn.data.action.builtin.SendMessageAction;
import com.iafenvoy.saturn.data.action.builtin.entity.*;
import com.iafenvoy.saturn.data.action.builtin.entity.meta.ChanceAction;
import com.iafenvoy.saturn.data.action.builtin.entity.meta.ChoiceAction;
import com.iafenvoy.saturn.data.action.builtin.entity.meta.IfElseAction;
import com.iafenvoy.saturn.data.action.builtin.entity.meta.SequenceAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code entity_action_type} entries.
 */
public final class SaturnEntityActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_ACTION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("no_op", () -> NoOpAction.CODEC);
        REGISTRY.register("sequence", () -> SequenceAction.CODEC);
        REGISTRY.register("chance", () -> ChanceAction.CODEC);
        REGISTRY.register("if_else", () -> IfElseAction.CODEC);
        REGISTRY.register("choice", () -> ChoiceAction.CODEC);
        REGISTRY.register("send_message", () -> SendMessageAction.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("dismount", () -> SimpleActions.createEntity(ctx -> ctx.entity().stopRiding()));
        REGISTRY.register("extinguish", () -> SimpleActions.createEntity(ctx -> ctx.entity().clearFire()));
        REGISTRY.register("heal", () -> HealAction.CODEC);
        REGISTRY.register("apply_effect", () -> ApplyEffectAction.CODEC);
        REGISTRY.register("teleport", () -> TeleportAction.CODEC);
        REGISTRY.register("knockback", () -> KnockbackAction.CODEC);
        REGISTRY.register("spawn_entity", () -> SpawnEntityAction.CODEC);
        REGISTRY.register("spawn_projectile", () -> SpawnProjectileAction.CODEC);
        REGISTRY.register("add_experience", () -> AddExperienceAction.CODEC);
        REGISTRY.register("add_velocity", () -> AddVelocityAction.CODEC);
        REGISTRY.register("exhaust", () -> ExhaustAction.CODEC);
        REGISTRY.register("feed", () -> FeedAction.CODEC);
        REGISTRY.register("gain_air", () -> GainAirAction.CODEC);
        REGISTRY.register("give_item", () -> GiveItemAction.CODEC);
        REGISTRY.register("play_sound", () -> PlaySoundAction.CODEC);
        REGISTRY.register("remove_effect", () -> RemoveEffectAction.CODEC);
        REGISTRY.register("set_fall_distance", () -> SetFallDistanceAction.CODEC);
        REGISTRY.register("set_no_gravity", () -> SetNoGravityAction.CODEC);
        REGISTRY.register("set_on_fire", () -> SetOnFireAction.CODEC);
        REGISTRY.register("swing_hand", () -> SwingHandAction.CODEC);
        REGISTRY.register("emit_game_event", () -> EmitGameEventAction.CODEC);
        REGISTRY.register("passenger_action", () -> PassengerAction.CODEC);
        REGISTRY.register("block_action", () -> BlockActionAction.CODEC);
        REGISTRY.register("self_bientity_action", () -> SelfBiEntityAction.CODEC);
        REGISTRY.register("equipped_item_action", () -> EquippedItemAction.CODEC);
        REGISTRY.register("riding_action", () -> RidingAction.CODEC);
        REGISTRY.register("spawn_particles", () -> SpawnParticlesAction.CODEC);
        REGISTRY.register("spawn_effect_cloud", () -> SpawnEffectCloudAction.CODEC);
    }

    private SaturnEntityActions() {
    }
}
