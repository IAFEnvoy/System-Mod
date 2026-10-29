package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.action.NoOpAction;
import com.iafenvoy.saturn.data.action.builtin.SendMessageAction;
import com.iafenvoy.saturn.data.action.builtin.item.*;
import com.iafenvoy.saturn.data.action.builtin.item.meta.ChanceAction;
import com.iafenvoy.saturn.data.action.builtin.item.meta.ChoiceAction;
import com.iafenvoy.saturn.data.action.builtin.item.meta.IfElseAction;
import com.iafenvoy.saturn.data.action.builtin.item.meta.SequenceItemAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code item_action_type} entries.
 */
public final class SaturnItemActions {
    public static final DeferredRegister<MapCodec<? extends ItemAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_ACTION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("no_op", () -> NoOpAction.CODEC);
        REGISTRY.register("sequence", () -> SequenceItemAction.CODEC);
        REGISTRY.register("chance", () -> ChanceAction.CODEC);
        REGISTRY.register("if_else", () -> IfElseAction.CODEC);
        REGISTRY.register("choice", () -> ChoiceAction.CODEC);
        REGISTRY.register("send_message", () -> SendMessageAction.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
        REGISTRY.register("damage_item", () -> DamageItemAction.CODEC);
        REGISTRY.register("consume_item", () -> ConsumeItemAction.CODEC);
        REGISTRY.register("consume_health", () -> ConsumeHealthItemAction.CODEC);
        REGISTRY.register("cooldown", () -> CooldownAction.CODEC);
        REGISTRY.register("remove_enchantment", () -> RemoveEnchantmentAction.CODEC);
        REGISTRY.register("add_enchantment", () -> AddEnchantmentAction.CODEC);
        REGISTRY.register("merge_components", () -> MergeComponentsAction.CODEC);
    }

    private SaturnItemActions() {
    }
}
