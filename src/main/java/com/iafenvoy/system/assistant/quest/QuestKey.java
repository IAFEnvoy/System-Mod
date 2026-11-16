package com.iafenvoy.system.assistant.quest;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * The first '#' separates the source ID from an opaque native quest ID or reward reference.
 */
public record QuestKey(Identifier source, String localId) {
    public QuestKey {
        Objects.requireNonNull(source);
        if (localId == null || localId.isBlank()) throw new IllegalArgumentException("Empty quest key");
    }

    public static QuestKey parse(String value) {
        int separator = value.indexOf('#');
        if (separator < 1) throw new IllegalArgumentException("A source-qualified quest key is required");
        return new QuestKey(Identifier.parse(value.substring(0, separator)), value.substring(separator + 1));
    }

    @Override
    public @NonNull String toString() {
        return this.source + "#" + this.localId;
    }
}
