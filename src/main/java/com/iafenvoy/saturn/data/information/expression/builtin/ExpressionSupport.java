package com.iafenvoy.saturn.data.information.expression.builtin;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.mojang.serialization.Dynamic;

import java.util.*;

/**
 * Shared dependency, codec and collection helpers for the builtin operations.
 */
final class ExpressionSupport {
    private ExpressionSupport() {
    }

    static Set<EntityValueGetter<?>> collectGetters(InformationExpression... values) {
        return collectGetters(Arrays.asList(values));
    }

    static Set<EntityValueGetter<?>> collectGetters(Collection<? extends InformationExpression> values) {
        Set<EntityValueGetter<?>> getters = new HashSet<>();
        values.forEach(value -> getters.addAll(value.getters()));
        return Set.copyOf(getters);
    }

    static List<Map.Entry<String, JsonElement>> entries(ExpressionValue value) {
        if (value instanceof ExpressionValue.Missing(
                MissingReason reason
        )) throw new ExpressionValue.Unavailable(reason);
        if (!(value instanceof ExpressionValue.Data(Dynamic<JsonElement> data1)))
            throw new ExpressionValue.Unavailable(MissingReason.INVALID);
        JsonElement json = data1.getValue();
        if (json.isJsonObject()) {
            if (json.getAsJsonObject().size() > 10_000) throw new ExpressionValue.Unavailable(MissingReason.INVALID);
            return json.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        }
        if (!json.isJsonArray()) throw new ExpressionValue.Unavailable(MissingReason.INVALID);
        if (json.getAsJsonArray().size() > 10_000) throw new ExpressionValue.Unavailable(MissingReason.INVALID);
        List<Map.Entry<String, JsonElement>> result = new ArrayList<>();
        for (int index = 0; index < json.getAsJsonArray().size(); index++)
            result.add(Map.entry(Integer.toString(index), json.getAsJsonArray().get(index)));
        return result;
    }
}
