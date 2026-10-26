package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.TypedPayload;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Examples and argument extraction belong to the module; registration supplies identity and read authority. */
public record RoutingProfile<I>(Map<String, List<String>> examples, boolean direct, Arguments<I> arguments) {
    public RoutingProfile {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        examples.forEach((language, texts) -> {
            if (!language.matches("[a-z]{2,3}") || texts.isEmpty() || texts.size() > 24
                    || texts.stream().anyMatch(text -> text == null || text.isBlank() || text.length() > 240))
                throw new IllegalArgumentException("Invalid routing examples");
            copy.put(language, List.copyOf(texts));
        });
        examples = Collections.unmodifiableMap(copy);
        Objects.requireNonNull(arguments);
    }

    public static <I> RoutingProfile<I> read(Arguments<I> parser, List<String> chinese, List<String> english) {
        return new RoutingProfile<>(Map.of("zh", chinese, "en", english), true, parser);
    }

    public static <I> RoutingProfile<I> hints(List<String> chinese, List<String> english) {
        return new RoutingProfile<>(Map.of("zh", chinese, "en", english), false, text -> null);
    }

    public Bound bind(TypedPayload.Type<I> type, boolean readOnly) {
        return new Bound(type.id(), this.examples, this.direct && readOnly, text -> {
            try {
                I input = this.arguments.parse(text);
                if (input == null) return null;
                TypedPayload<I> payload = type.of(input);
                payload.json();
                return payload;
            } catch (RuntimeException ignored) {
                return null;
            }
        });
    }

    public record Bound(Identifier moduleId, Map<String, List<String>> examples, boolean direct,
                        Arguments<TypedPayload<?>> arguments) {
    }

    @FunctionalInterface
    public interface Arguments<I> {
        /** Return null when the current request cannot independently supply all required arguments. */
        I parse(String currentRequest);
    }
}
