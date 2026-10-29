package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.formula.SimpleFunction;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.objecthunter.exp4j.function.Function;

/**
 * The built-in {@code formula_function} entries.
 */
public final class SaturnFormulaFunctions {
    public static final DeferredRegister<Function> REGISTRY = DeferredRegister.create(SaturnRegistries.FORMULA_FUNCTION, Saturn.MOD_ID);

    static {
        REGISTRY.register("round", () -> new SimpleFunction("round", 1, args -> Math.round(args[0])));
        REGISTRY.register("clamp", () -> new SimpleFunction("clamp", 3, args -> Mth.clamp(args[0], args[1], args[2])));
        REGISTRY.register("min", () -> new SimpleFunction("min", 2, args -> Math.min(args[0], args[1])));
        REGISTRY.register("max", () -> new SimpleFunction("max", 2, args -> Math.max(args[0], args[1])));
    }

    private SaturnFormulaFunctions() {
    }
}
