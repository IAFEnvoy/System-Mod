package com.iafenvoy.saturn.registry.cache;

import com.iafenvoy.saturn.data.trigger.TriggerRule;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.List;

/**
 * The caches Saturn itself keeps.
 *
 * <p>{@link RegistryCache} self-registers into the rebuild list, so a dependent mod does not add its caches here: it
 * declares its own {@code static final} cache fields and forces their class initialisation from its own mod
 * constructor, and the same {@code TagsUpdatedEvent} rebuilds them. That is why this class only owns the caches whose
 * registries are Saturn's, and why its {@link #init()} exists at all: nothing else touches this class before a world
 * loads, and a run without the call leaves the list empty for the whole session.
 */
public final class RegistryCaches {
    public static final FormulaNameCache<Attribute> ATTRIBUTE_NAMES = new FormulaNameCache<>(Registries.ATTRIBUTE, "attribute");
    // The rules one signal reaches, indexed once per rebuild: publishing a signal is one map lookup.
    public static final ReverseMappingListCache<ResourceLocation, TriggerRule> TRIGGER_RULES_BY_SIGNAL = new ReverseMappingListCache<>(SaturnResourceKeys.TRIGGER, rule -> List.of(rule.value().trigger().signalType()));

    public static void init() {
    }
}
