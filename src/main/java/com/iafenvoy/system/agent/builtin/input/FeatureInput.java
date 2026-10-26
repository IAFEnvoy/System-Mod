package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record FeatureInput(String featureId) {
    public static final SchemaMapCodec<FeatureInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("featureId", SchemaCodecs.matching("^[a-z0-9_.-]+:[a-z0-9_./-]+$")).forGetter(FeatureInput::featureId)
    ).apply(i, FeatureInput::new));
}
