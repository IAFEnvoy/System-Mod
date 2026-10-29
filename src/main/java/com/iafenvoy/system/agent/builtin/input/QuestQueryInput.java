package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Built-in parameters with boundary validation.
 */
public record QuestQueryInput(String query, View view) implements AgentAction {
    public static final SchemaMapCodec<QuestQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(QuestQueryInput::query),
            f.optional("view", View.SCHEMA, View.ACTIVE).forGetter(QuestQueryInput::view)
    ).apply(i, QuestQueryInput::new));

    @Override
    public MapCodec<QuestQueryInput> codec() {
        return CODEC;
    }

    public enum View implements StringRepresentable {
        ACTIVE, CLAIMABLE, SUBMITTABLE;

        public static final SchemaCodecs.Value<View> SCHEMA = SchemaCodecs.matching("active|claimable|submittable")
                .xmap(View::parse, View::getSerializedName);

        public static View parse(String value) {
            return value.isBlank() ? ACTIVE : valueOf(value.toUpperCase(Locale.ROOT));
        }

        public String messageKey() {
            return switch (this) {
                case ACTIVE -> "system.result.quests";
                case CLAIMABLE -> "system.result.claimable_quests";
                case SUBMITTABLE -> "system.result.submittable_quests";
            };
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
