package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.action.NoOpAction;
import com.iafenvoy.saturn.data.action.SimpleActions;
import com.iafenvoy.saturn.data.action.builtin.SendMessageAction;
import com.iafenvoy.saturn.data.action.builtin.bientity.AddVelocityAction;
import com.iafenvoy.saturn.data.action.builtin.bientity.HealTargetBiEntityAction;
import com.iafenvoy.saturn.data.action.builtin.bientity.TeleportAction;
import com.iafenvoy.saturn.data.action.builtin.bientity.meta.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code bi_entity_action_type} entries.
 */
public final class SaturnBiEntityActions {
    public static final DeferredRegister<MapCodec<? extends BiEntityAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.BI_ENTITY_ACTION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("no_op", () -> NoOpAction.CODEC);
        REGISTRY.register("sequence", () -> SequenceBiEntityAction.CODEC);
        REGISTRY.register("chance", () -> ChanceAction.CODEC);
        REGISTRY.register("if_else", () -> IfElseAction.CODEC);
        REGISTRY.register("choice", () -> ChoiceAction.CODEC);
        REGISTRY.register("actor_action", () -> ActorAction.CODEC);
        REGISTRY.register("target_action", () -> TargetAction.CODEC);
        REGISTRY.register("send_message", () -> SendMessageAction.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("mount", () -> SimpleActions.createBiEntity(ctx -> ctx.actor().startRiding(ctx.target())));
        REGISTRY.register("heal_target", () -> HealTargetBiEntityAction.CODEC);
        REGISTRY.register("add_velocity", () -> AddVelocityAction.CODEC);
        REGISTRY.register("teleport", () -> TeleportAction.CODEC);
    }

    private SaturnBiEntityActions() {
    }
}
