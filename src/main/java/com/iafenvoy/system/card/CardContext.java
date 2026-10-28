package com.iafenvoy.system.card;

import com.iafenvoy.system.util.LegacyText;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One flat set of states, parameters and trusted callbacks is shared by the whole card.
 */
public record CardContext(Object2DoubleMap<String> states, Map<String, Object> parameters,
                          Map<String, Runnable> callbacks) {
    public static final String RESULT_ITEMS = "result_items";
    public static final String RESULT_ACTIONS = "result_actions";
    public static final CardContext EMPTY = builder().build();

    public enum Mode {
        FULL, SIMPLE, NOTIFICATION, TEXT
    }

    public CardContext {
        states = Object2DoubleMaps.unmodifiable(new Object2DoubleOpenHashMap<>(states));
        parameters = Map.copyOf(parameters);
        callbacks = Map.copyOf(callbacks);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public <T> T get(String id, Class<T> clazz) {
        Object value = this.parameters.get(id);
        return clazz.isInstance(value) ? clazz.cast(value) : null;
    }

    public static final class Builder {
        private final Object2DoubleMap<String> states = new Object2DoubleOpenHashMap<>();
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private final Map<String, Runnable> callbacks = new LinkedHashMap<>();

        public Builder put(String id, Object value) {
            if (value instanceof Component component) value = LegacyText.snapshot(component);
            if (value instanceof List<?> list) value = List.copyOf(list);
            this.parameters.put(id, value);
            double state = switch (value) {
                case Boolean flag -> flag ? 1 : 0;
                case Number number -> number.doubleValue();
                case String text -> text.isBlank() ? 0 : 1;
                case Collection<?> entries -> entries.size();
                default -> 1;
            };
            return this.enable(id, state);
        }

        public Builder enable(String id, boolean enabled) {
            return this.enable(id, enabled ? 1 : 0);
        }

        public Builder enable(String id, double state) {
            this.states.put(id, state);
            return this;
        }

        public Builder callback(String id, Runnable callback) {
            this.callbacks.put(id, callback);
            return this;
        }

        /**
         * Entries write indexed keys into this same builder; there are no child contexts.
         */
        public <T> Builder list(String id, List<T> entries, BiConsumer<String, T> parameters) {
            this.put(id, entries.size());
            for (int index = 0; index < entries.size(); index++)
                parameters.accept(id + "." + index + ".", entries.get(index));
            return this;
        }

        public CardContext build() {
            return new CardContext(this.states, this.parameters, this.callbacks);
        }
    }
}
