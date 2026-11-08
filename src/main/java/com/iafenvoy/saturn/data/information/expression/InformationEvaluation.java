package com.iafenvoy.saturn.data.information.expression;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.getter.EntityValueKey;
import com.iafenvoy.saturn.data.information.model.InformationContext;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One collection's cache. Local iteration scopes share decoded fields, never global player data.
 */
public final class InformationEvaluation {
    private final PlayerView view;
    private final DynamicOps<JsonElement> ops;
    private final Map<EntityValueKey, ExpressionValue> fields;
    private final Map<String, ExpressionValue> locals;
    private final Budget budget;

    public InformationEvaluation(PlayerView view, InformationContext context) {
        this(view, context.ops(view), new HashMap<>(), Map.of(), new Budget());
    }

    private InformationEvaluation(PlayerView view, DynamicOps<JsonElement> ops, Map<EntityValueKey, ExpressionValue> fields,
                                  Map<String, ExpressionValue> locals, Budget budget) {
        this.view = view;
        this.ops = ops;
        this.fields = fields;
        this.locals = locals;
        this.budget = budget;
    }

    public DynamicOps<JsonElement> ops() {
        return this.ops;
    }

    /**
     * A row has its own work limit while all rows reuse the field encoding cache.
     */
    public InformationEvaluation forRow() {
        return new InformationEvaluation(this.view, this.ops, this.fields, Map.of(), new Budget());
    }

    public ExpressionValue evaluate(InformationExpression expression) {
        if (++this.budget.depth > 64 || --this.budget.remaining < 0) {
            --this.budget.depth;
            return new ExpressionValue.Missing(MissingReason.INVALID);
        }
        try {
            return Objects.requireNonNull(expression.evaluate(this));
        } catch (ExpressionValue.Unavailable failure) {
            return new ExpressionValue.Missing(failure.reason);
        } finally {
            --this.budget.depth;
        }
    }

    public ExpressionValue getter(EntityValueGetter<?> getter, List<String> path) {
        ExpressionValue value = this.fields.computeIfAbsent(EntityValueKey.of(getter, this.ops), key -> this.encode(getter));
        return this.select(value, path);
    }

    private <T> ExpressionValue encode(EntityValueGetter<T> getter) {
        ViewValue<T> value = this.view.read(getter);
        if (!value.isKnown()) return new ExpressionValue.Missing(value.missingReason().orElseThrow());
        return getter.valueCodec().encodeStart(this.ops, value.value().orElseThrow()).result()
                .<ExpressionValue>map(json -> new ExpressionValue.Data(new Dynamic<>(this.ops, json)))
                .orElseGet(() -> new ExpressionValue.Missing(MissingReason.INVALID));
    }

    public ExpressionValue local(String name, List<String> path) {
        return this.select(this.locals.getOrDefault(name, new ExpressionValue.Missing(MissingReason.NOT_APPLICABLE)), path);
    }

    public InformationEvaluation withLocal(String name, JsonElement value, String key) {
        Map<String, ExpressionValue> locals = new HashMap<>(this.locals);
        locals.put(name, this.data(value));
        locals.put(name + "_key", this.data(this.ops.createString(key)));
        return new InformationEvaluation(this.view, this.ops, this.fields, Map.copyOf(locals), this.budget);
    }

    public ExpressionValue data(JsonElement value) {
        return new ExpressionValue.Data(new Dynamic<>(this.ops, value.deepCopy()));
    }

    private ExpressionValue select(ExpressionValue source, List<String> path) {
        if (source instanceof ExpressionValue.Missing) return source;
        if (!(source instanceof ExpressionValue.Data(Dynamic<JsonElement> data1)))
            return new ExpressionValue.Missing(MissingReason.INVALID);
        JsonElement value = data1.getValue();
        for (String segment : path) {
            if (value.isJsonObject()) value = value.getAsJsonObject().get(segment);
            else if (value.isJsonArray()) {
                try {
                    int index = Integer.parseInt(segment);
                    value = index >= 0 && index < value.getAsJsonArray().size() ? value.getAsJsonArray().get(index) : null;
                } catch (NumberFormatException failure) {
                    return new ExpressionValue.Missing(MissingReason.INVALID);
                }
            } else return new ExpressionValue.Missing(MissingReason.INVALID);
            if (value == null || value.isJsonNull()) return new ExpressionValue.Missing(MissingReason.NOT_CAPTURED);
        }
        return this.data(value);
    }

    private static final class Budget {
        int remaining = 100_000;
        int depth;
    }
}
