package com.iafenvoy.saturn.registry;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.condition.*;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.trigger.Trigger;
import com.iafenvoy.saturn.util.formula.FormulaVariable;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.objecthunter.exp4j.function.Function;

import java.util.LinkedList;
import java.util.List;

/**
 * The code registries Saturn owns. The default id of each is part of the data-pack contract: a definition that omits
 * its {@code type} field loads as that entry.
 */
@EventBusSubscriber
public final class SaturnRegistries {
    private static final List<DefaultedRegistry<?>> REGISTRIES = new LinkedList<>();

    public static final DefaultedRegistry<MapCodec<? extends Cost>> COST_TYPE = create("item", SaturnResourceKeys.COST_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends DataStorage>> DATA_STORAGE_TYPE = create("empty", SaturnResourceKeys.DATA_STORAGE_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends Trigger>> TRIGGER_TYPE = create("use", SaturnResourceKeys.TRIGGER_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends NumberProvider>> NUMBER_PROVIDER_TYPE = create("constant", SaturnResourceKeys.NUMBER_PROVIDER_TYPE);
    public static final DefaultedRegistry<Function> FORMULA_FUNCTION = create("round", SaturnResourceKeys.FORMULA_FUNCTION);
    public static final DefaultedRegistry<FormulaVariable> FORMULA_VARIABLE = create("zero", SaturnResourceKeys.FORMULA_VARIABLE);
    public static final DefaultedRegistry<MapCodec<? extends EntityAction>> ENTITY_ACTION_TYPE = create("no_op", SaturnResourceKeys.ENTITY_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BiEntityAction>> BI_ENTITY_ACTION_TYPE = create("no_op", SaturnResourceKeys.BI_ENTITY_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BlockAction>> BLOCK_ACTION_TYPE = create("no_op", SaturnResourceKeys.BLOCK_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends ItemAction>> ITEM_ACTION_TYPE = create("no_op", SaturnResourceKeys.ITEM_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends EntityCondition>> ENTITY_CONDITION_TYPE = create("always", SaturnResourceKeys.ENTITY_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BiEntityCondition>> BI_ENTITY_CONDITION_TYPE = create("always", SaturnResourceKeys.BI_ENTITY_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends BlockCondition>> BLOCK_CONDITION_TYPE = create("always", SaturnResourceKeys.BLOCK_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends ItemCondition>> ITEM_CONDITION_TYPE = create("always", SaturnResourceKeys.ITEM_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends DamageCondition>> DAMAGE_CONDITION_TYPE = create("always", SaturnResourceKeys.DAMAGE_CONDITION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends Entry>> ITEM_MATCHER_ENTRY_TYPE = create("item", SaturnResourceKeys.ITEM_MATCHER_ENTRY_TYPE);

    public static List<DefaultedRegistry<?>> all() {
        return List.copyOf(REGISTRIES);
    }

    private static <T> DefaultedRegistry<T> create(String defaultKey, ResourceKey<? extends Registry<T>> key) {
        DefaultedRegistry<T> registry = new DefaultedMappedRegistry<>(defaultKey, key, Lifecycle.stable(), false);
        REGISTRIES.add(registry);
        return registry;
    }

    @SubscribeEvent
    public static void newRegistries(NewRegistryEvent event) {
        REGISTRIES.forEach(event::register);
    }
}
