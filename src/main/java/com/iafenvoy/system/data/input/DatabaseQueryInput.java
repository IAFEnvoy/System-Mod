package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;

/**
 * Database queries are tool arguments, independent of any game integration or routable action.
 */
public record DatabaseQueryInput(String query) {
    public static final SchemaMapCodec<DatabaseQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.STRING).forGetter(DatabaseQueryInput::query)
    ).apply(i, DatabaseQueryInput::new));
}
