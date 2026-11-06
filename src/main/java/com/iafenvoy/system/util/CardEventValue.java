package com.iafenvoy.system.util;

import net.minecraft.resources.Identifier;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The built-in string convention; external button events may use their own format.
 */
public record CardEventValue(Identifier action, Map<String, String> arguments) {
    public CardEventValue {
        arguments = Map.copyOf(arguments);
    }

    public static String encode(Identifier action, Map<String, String> arguments) {
        if (arguments.isEmpty()) return action.toString();
        return action + "?" + arguments.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> quote(e.getKey()) + "=" + quote(e.getValue())).collect(Collectors.joining("&"));
    }

    public static CardEventValue parse(String value) {
        int separator = value.indexOf('?');
        Identifier id = Identifier.tryParse(separator < 0 ? value : value.substring(0, separator));
        if (id == null) throw new IllegalArgumentException("Invalid button action ID");
        Map<String, String> arguments = new LinkedHashMap<>();
        if (separator >= 0 && separator + 1 < value.length())
            for (String entry : value.substring(separator + 1).split("&")) {
                int equals = entry.indexOf('=');
                if (equals < 0) throw new IllegalArgumentException("Missing button argument value");
                String key = URLDecoder.decode(entry.substring(0, equals), StandardCharsets.UTF_8);
                if (arguments.putIfAbsent(key, URLDecoder.decode(entry.substring(equals + 1), StandardCharsets.UTF_8)) != null)
                    throw new IllegalArgumentException("Duplicate button argument " + key);
            }
        return new CardEventValue(id, arguments);
    }

    private static String quote(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
