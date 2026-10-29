package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.formula.FormulaVariable;
import com.iafenvoy.saturn.util.formula.variable.EntityVariable;
import com.iafenvoy.saturn.util.formula.variable.RandomVariable;
import com.iafenvoy.saturn.util.formula.variable.ZeroVariable;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code formula_variable} entries.
 */
public final class SaturnFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(SaturnRegistries.FORMULA_VARIABLE, Saturn.MOD_ID);

    static {
        REGISTRY.register("zero", ZeroVariable::new);
        REGISTRY.register("random", RandomVariable::new);
        REGISTRY.register("caster", () -> new EntityVariable("caster_"));
        REGISTRY.register("target", () -> new EntityVariable("target_"));
    }

    private SaturnFormulaVariables() {
    }
}
