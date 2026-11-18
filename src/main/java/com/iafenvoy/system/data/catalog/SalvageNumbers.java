package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaFunctions;
import com.iafenvoy.saturn.util.formula.FormulaVariables;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.saturn.util.formula.number.ContextVariable;
import com.iafenvoy.saturn.util.formula.number.Expression;
import com.iafenvoy.saturn.util.formula.number.Sum;
import com.mojang.serialization.MapCodec;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict, deterministic evaluation: Saturn's general-purpose fallback-to-zero is unsafe for destructive quotes.
 */
public final class SalvageNumbers {
    private static final Set<String> VARIABLES = Set.of("salvage_durability", "salvage_damage", "salvage_max_damage",
            "salvage_enchantment_count", "salvage_enchantment_levels", "salvage_value");
    private static final Set<String> FUNCTIONS = Set.of("abs", "acos", "asin", "atan", "cbrt", "ceil", "cos", "cosh",
            "exp", "floor", "log", "log10", "sin", "sinh", "sqrt", "tan", "tanh", "round", "clamp", "min", "max");
    private static final Pattern CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Map<MapCodec<?>, BiFunction<NumberProvider, FormulaContext, Double>> EXTENSIONS = new ConcurrentHashMap<>();

    private SalvageNumbers() {
    }

    /**
     * Register during mod setup. The evaluator must throw on missing values, never mutate game state or use randomness.
     */
    public static <T extends NumberProvider> void register(MapCodec<T> codec, Class<T> type, BiFunction<T, FormulaContext, Double> evaluator) {
        if (EXTENSIONS.putIfAbsent(codec, (value, context) -> evaluator.apply(type.cast(value), context)) != null)
            throw new IllegalArgumentException("Duplicate salvage number evaluator");
    }

    public static void validate(NumberProvider value) {
        validate(value, 0);
    }

    private static void validate(NumberProvider value, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Salvage number nesting exceeds 32");
        switch (value) {
            case Constant constant -> finite(constant.value());
            case ContextVariable variable -> requireVariable(variable.variable());
            case Sum sum -> sum.summands().forEach(child -> validate(child, depth + 1));
            case Expression expression -> {
                if (expression.source().length() > 2048 || !expression.problems().isEmpty())
                    throw new IllegalArgumentException("Invalid salvage expression");
                Matcher calls = CALL.matcher(expression.source());
                while (calls.find()) if (!FUNCTIONS.contains(calls.group(1)))
                    throw new IllegalArgumentException("Non-deterministic or unsupported salvage function: " + calls.group(1));
                for (String name : FormulaVariables.find(expression.source()))
                    if (!expression.params().containsKey(name)) requireVariable(name);
                expression.params().values().forEach(child -> validate(child, depth + 1));
            }
            default -> {
                if (!EXTENSIONS.containsKey(value.codec()))
                    throw new IllegalArgumentException("Number provider requires a deterministic salvage evaluator");
            }
        }
    }

    private static void requireVariable(String name) {
        if (!VARIABLES.contains(name)) throw new IllegalArgumentException("Unavailable salvage variable: " + name);
    }

    public static double evaluate(NumberProvider value, FormulaContext context) {
        return finite(switch (value) {
            case Constant constant -> constant.value();
            case ContextVariable variable -> required(context, variable.variable());
            case Sum sum -> sum.summands().stream().mapToDouble(child -> evaluate(child, context)).sum();
            case Expression expression -> {
                Set<String> variables = FormulaVariables.find(expression.source());
                net.objecthunter.exp4j.Expression compiled = new ExpressionBuilder(expression.source()).variables(variables)
                        .functions(FormulaFunctions.all().stream().filter(function -> FUNCTIONS.contains(function.getName())).toList()).build();
                for (String name : variables)
                    compiled.setVariable(name, expression.params().containsKey(name)
                            ? evaluate(expression.params().get(name), context) : required(context, name));
                yield compiled.evaluate();
            }
            default -> {
                BiFunction<NumberProvider, FormulaContext, Double> evaluator = EXTENSIONS.get(value.codec());
                if (evaluator == null) throw new IllegalArgumentException("Unsupported salvage number provider");
                yield evaluator.apply(value, context);
            }
        });
    }

    public static boolean requiresValue(NumberProvider value) {
        return switch (value) {
            case Constant ignored -> false;
            case ContextVariable variable -> variable.variable().equals("salvage_value");
            case Sum sum -> sum.summands().stream().anyMatch(SalvageNumbers::requiresValue);
            case Expression expression -> FormulaVariables.find(expression.source()).stream()
                    .anyMatch(name -> expression.params().containsKey(name) ? requiresValue(expression.params().get(name)) : name.equals("salvage_value"));
            default -> true;
        };
    }

    private static double required(FormulaContext context, String name) {
        double value = context.explicit(name);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Missing salvage value: " + name);
        return value;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite salvage quantity");
        return value;
    }
}
