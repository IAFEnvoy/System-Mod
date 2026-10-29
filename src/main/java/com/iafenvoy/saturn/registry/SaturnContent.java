package com.iafenvoy.saturn.registry;

import com.iafenvoy.saturn.registry.content.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The built-in entries Saturn ships, one class per registry under {@code registry/content}.
 *
 * <p>Every entry there is a cell of the data-pack vocabulary a definition may name by {@code type}. A dependent mod's
 * own entries are its own class over the same {@code SaturnRegistries} field; registering them beside these is enough.
 *
 * <p>What is <em>not</em> here is a rule of a world: no element, no realm, no aura, no cultivation, no item binding.
 * Those ids are the dependent mod's to register.
 */
public final class SaturnContent {
    private static final List<DeferredRegister<?>> REGISTERS = List.of(
            SaturnNumberProviders.REGISTRY,
            SaturnFormulaFunctions.REGISTRY,
            SaturnFormulaVariables.REGISTRY,
            SaturnDataStorages.REGISTRY,
            SaturnItemMatchers.REGISTRY,
            SaturnCosts.REGISTRY,
            SaturnTriggers.REGISTRY,
            SaturnEntityActions.REGISTRY,
            SaturnBiEntityActions.REGISTRY,
            SaturnBlockActions.REGISTRY,
            SaturnItemActions.REGISTRY,
            SaturnEntityConditions.REGISTRY,
            SaturnBiEntityConditions.REGISTRY,
            SaturnBlockConditions.REGISTRY,
            SaturnItemConditions.REGISTRY,
            SaturnDamageConditions.REGISTRY);

    private SaturnContent() {
    }

    public static void register(IEventBus bus) {
        REGISTERS.forEach(registry -> registry.register(bus));
    }

    // The alias table walks the same list, so a registry added here is aliased too.
    static List<DeferredRegister<?>> registers() {
        return REGISTERS;
    }
}
