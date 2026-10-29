package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Examples and argument extraction belong to the module; registration supplies identity and read authority.
 */
public record RoutingProfile<I extends AgentAction>(Map<String, List<String>> examples, boolean direct,
                                                    Arguments<I> arguments) {
    public RoutingProfile {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        examples.forEach((language, texts) -> {
            if (!language.matches("[a-z]{2,3}") || texts.isEmpty() || texts.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Invalid routing examples");
            copy.put(language, List.copyOf(texts));
        });
        examples = Collections.unmodifiableMap(copy);
        Objects.requireNonNull(arguments);
    }

    public static <I extends AgentAction> RoutingProfile<I> read(Arguments<I> parser, List<String> chinese, List<String> english) {
        return new RoutingProfile<>(Map.of("zh", chinese, "en", english), true, parser);
    }

    public static <I extends AgentAction> RoutingProfile<I> hints(List<String> chinese, List<String> english) {
        return new RoutingProfile<>(Map.of("zh", chinese, "en", english), false, text -> null);
    }

    public Bound bind(ResourceLocation id, MapCodec<I> codec, boolean readOnly) {
        return new Bound(id, this.examples, this.direct && readOnly, text -> {
            try {
                I input = this.arguments.parse(text);
                if (input == null) return null;
                return AgentAction.validate(codec, input);
            } catch (RuntimeException ignored) {
                return null;
            }
        });
    }

    public record Bound(ResourceLocation moduleId, Map<String, List<String>> examples, boolean direct,
                        Arguments<AgentAction> arguments) {
    }

    @FunctionalInterface
    public interface Arguments<I> {
        /**
         * Return null when the current request cannot independently supply all required arguments.
         */
        I parse(String currentRequest);
    }
}
