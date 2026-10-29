package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.action.NoOpAction;
import com.iafenvoy.saturn.data.action.builtin.SendMessageAction;
import com.iafenvoy.saturn.data.action.builtin.block.*;
import com.iafenvoy.saturn.data.action.builtin.block.meta.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code block_action_type} entries.
 */
public final class SaturnBlockActions {
    public static final DeferredRegister<MapCodec<? extends BlockAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.BLOCK_ACTION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("no_op", () -> NoOpAction.CODEC);
        REGISTRY.register("sequence", () -> SequenceBlockAction.CODEC);
        REGISTRY.register("chance", () -> ChanceAction.CODEC);
        REGISTRY.register("if_else", () -> IfElseAction.CODEC);
        REGISTRY.register("choice", () -> ChoiceAction.CODEC);
        REGISTRY.register("offset", () -> OffsetAction.CODEC);
        REGISTRY.register("send_message", () -> SendMessageAction.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("set_block", () -> SetBlockAction.CODEC);
        REGISTRY.register("break_block", () -> BreakBlockAction.CODEC);
        REGISTRY.register("schedule_tick", () -> ScheduleTickAction.CODEC);
        REGISTRY.register("bonemeal", () -> BonemealAction.CODEC);
        REGISTRY.register("light_up", () -> LightUpAction.CODEC);
        REGISTRY.register("explode", () -> ExplodeAction.CODEC);
        REGISTRY.register("spawn_entity", () -> SpawnEntityAction.CODEC);
    }

    private SaturnBlockActions() {
    }
}
