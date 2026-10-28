package com.iafenvoy.system.agent.prompt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable per-server prompt catalog; executions retain one revision across resource reloads.
 */
public final class SystemPrompts {
    public static final String ROUTING = "system:routing";
    public static final String SIGN_IN = "system:sign_in";
    public static final String QUEST_NARRATION = "system:quest_narration";
    public static final String QUEST_BACKGROUND = "system:quest_background";
    public static final String WEB_SEARCH = "system:web_search";
    private final Map<String, PromptTemplate> templates;
    private final String revision = UUID.randomUUID().toString();

    private SystemPrompts(Map<String, PromptTemplate> templates) {
        this.templates = Map.copyOf(templates);
    }

    public static SystemPrompts empty() {
        return new SystemPrompts(Map.of());
    }

    public static SystemPrompts create(Map<String, String> definitions) {
        Map<String, PromptTemplate> templates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definitions.entrySet()) {
            String id = entry.getKey(), text = entry.getValue();
            try {
                templates.put(id, PromptTemplate.compile(text));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Invalid prompt " + id, error);
            }
        }
        validate(templates, ROUTING, Set.of("player_language", "capabilities"), Set.of("capabilities"));
        validate(templates, SIGN_IN, Set.of("player_language"), Set.of());
        validate(templates, QUEST_NARRATION, Set.of("player_language"), Set.of());
        validate(templates, QUEST_BACKGROUND, Set.of("player_language"), Set.of());
        validate(templates, WEB_SEARCH, Set.of("player_language"), Set.of());
        return new SystemPrompts(templates);
    }

    private static void validate(Map<String, PromptTemplate> templates, String id, Set<String> allowed, Set<String> required) {
        PromptTemplate template = templates.get(id);
        if (template == null) throw new IllegalArgumentException("Missing required prompt " + id);
        if (!allowed.containsAll(template.placeholders()) || !template.placeholders().containsAll(required))
            throw new IllegalArgumentException("Invalid placeholders in prompt " + id);
    }

    public String render(String id, Map<String, String> values) {
        PromptTemplate template = this.templates.get(id);
        if (template == null) throw new IllegalArgumentException("Unknown prompt " + id);
        return template.render(values);
    }

    public String revision() {
        return this.revision;
    }
}
