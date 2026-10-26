package com.iafenvoy.system.agent.prompt;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-text substitution only: no expressions, recursive expansion or executable template language.
 */
final class PromptTemplate {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]*)}}");
    private final String text;
    private final Set<String> placeholders;

    private PromptTemplate(String text, Set<String> placeholders) {
        this.text = text;
        this.placeholders = Set.copyOf(placeholders);
    }

    static PromptTemplate compile(String text) {
        if (text.isBlank()) throw new IllegalArgumentException("Empty prompt template");
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        int position = 0;
        while (matcher.find()) {
            validateLiteral(text.substring(position, matcher.start()));
            placeholders.add(matcher.group(1));
            position = matcher.end();
        }
        validateLiteral(text.substring(position));
        return new PromptTemplate(text, placeholders);
    }

    private static void validateLiteral(String text) {
        if (text.contains("{{"))
            throw new IllegalArgumentException("Malformed prompt placeholder");
    }

    Set<String> placeholders() {
        return this.placeholders;
    }

    String render(Map<String, String> values) {
        for (String name : this.placeholders)
            if (values.get(name) == null) throw new IllegalArgumentException("Missing prompt placeholder " + name);
        // Match only the source template. Braces, dollar signs and backslashes inside values stay literal.
        return PLACEHOLDER.matcher(this.text).replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
    }
}
