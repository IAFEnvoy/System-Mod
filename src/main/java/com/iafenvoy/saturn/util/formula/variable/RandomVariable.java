package com.iafenvoy.saturn.util.formula.variable;

import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaVariable;

import java.util.Set;

public record RandomVariable() implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("random");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        return suffix.isEmpty() ? context.random().nextDouble() : Double.NaN;
    }
}
