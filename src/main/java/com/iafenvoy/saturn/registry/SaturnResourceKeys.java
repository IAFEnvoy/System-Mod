package com.iafenvoy.saturn.registry;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.condition.*;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.trigger.Trigger;
import com.iafenvoy.saturn.data.trigger.TriggerRule;
import com.iafenvoy.saturn.util.formula.FormulaVariable;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.objecthunter.exp4j.function.Function;

/**
 * Every registry key Saturn owns, kept separate from the registry instances and the registration events so codecs
 * and runtime services can read registry identity without depending on a registration class.
 *
 * <p>The namespace is {@code saturn}: a data pack writes its entries under {@code data/<namespace>/saturn/<registry>/}.
 */
public final class SaturnResourceKeys {
    // Builtin registries
    public static final ResourceKey<Registry<MapCodec<? extends Cost>>> COST_TYPE = create("cost_type");
    public static final ResourceKey<Registry<MapCodec<? extends DataStorage>>> DATA_STORAGE_TYPE = create("data_storage_type");
    public static final ResourceKey<Registry<MapCodec<? extends Trigger>>> TRIGGER_TYPE = create("trigger_type");
    public static final ResourceKey<Registry<MapCodec<? extends NumberProvider>>> NUMBER_PROVIDER_TYPE = create("number_provider_type");
    public static final ResourceKey<Registry<Function>> FORMULA_FUNCTION = create("formula_function");
    public static final ResourceKey<Registry<FormulaVariable>> FORMULA_VARIABLE = create("formula_variable");
    public static final ResourceKey<Registry<MapCodec<? extends EntityAction>>> ENTITY_ACTION_TYPE = create("entity_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends BiEntityAction>>> BI_ENTITY_ACTION_TYPE = create("bi_entity_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends BlockAction>>> BLOCK_ACTION_TYPE = create("block_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends ItemAction>>> ITEM_ACTION_TYPE = create("item_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends EntityCondition>>> ENTITY_CONDITION_TYPE = create("entity_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends BiEntityCondition>>> BI_ENTITY_CONDITION_TYPE = create("bi_entity_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends BlockCondition>>> BLOCK_CONDITION_TYPE = create("block_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends ItemCondition>>> ITEM_CONDITION_TYPE = create("item_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends DamageCondition>>> DAMAGE_CONDITION_TYPE = create("damage_condition_type");
    public static final ResourceKey<Registry<MapCodec<? extends Entry>>> ITEM_MATCHER_ENTRY_TYPE = create("item_matcher_entry_type");

    // Datapack registries
    public static final ResourceKey<Registry<TriggerRule>> TRIGGER = create("trigger");

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Saturn.MOD_ID, path));
    }
}
