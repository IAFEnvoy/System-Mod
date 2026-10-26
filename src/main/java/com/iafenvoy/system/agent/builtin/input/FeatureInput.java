package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;

/** Built-in parameters with boundary validation. */
public record FeatureInput(String featureId) {
    public static final SchemaMapCodec<FeatureInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("featureId", SchemaCodecs.matching("^[a-z0-9_.-]+:[a-z0-9_./-]+$", 0, 2000)).forGetter(FeatureInput::featureId)
    ).apply(i, FeatureInput::new));
}
