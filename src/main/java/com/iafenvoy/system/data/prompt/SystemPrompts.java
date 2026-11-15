package com.iafenvoy.system.data.prompt;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    public static final String KNOWLEDGE_ANSWER = "system:knowledge_answer";
    public static final String REACT = "system:react";
    public static final String TOOL_CALL = "system:tool_call";
    public static final String FREE_CHAT = "system:free_chat";
    private final Map<String, PromptTemplate> templates;
    private final Map<String, String> variables;
    private final String revision = UUID.randomUUID().toString();

    private SystemPrompts(Map<String, PromptTemplate> templates) {
        this(templates, Map.of());
    }

    private SystemPrompts(Map<String, PromptTemplate> templates, Map<String, String> variables) {
        this.templates = Map.copyOf(templates);
        this.variables = Map.copyOf(variables);
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
        validate(templates, KNOWLEDGE_ANSWER, Set.of("player_language"), Set.of());
        validate(templates, REACT, Set.of("player_language"), Set.of());
        validate(templates, TOOL_CALL, Set.of(), Set.of());
        validate(templates, FREE_CHAT, Set.of("player_language", "emotion_catalog"), Set.of());
        return new SystemPrompts(templates);
    }

    private static void validate(Map<String, PromptTemplate> templates, String id, Set<String> allowed, Set<String> required) {
        PromptTemplate template = templates.get(id);
        if (template == null) throw new IllegalArgumentException("Missing required prompt " + id);
        if (!allowed.containsAll(template.placeholders()) || !template.placeholders().containsAll(required))
            throw new IllegalArgumentException("Invalid placeholders in prompt " + id);
    }

    public String render(String id, Map<String, String> values) {
        return this.find(id, values).orElseThrow(() -> new IllegalArgumentException("Unknown prompt " + id));
    }

    public Optional<String> find(String id, Map<String, String> values) {
        Map<String, String> merged = new HashMap<>(this.variables);
        merged.putAll(values);
        return Optional.ofNullable(this.templates.get(id)).map(template -> template.render(merged));
    }

    public SystemPrompts withVariables(Map<String, String> variables) {
        return new SystemPrompts(this.templates, variables);
    }

    public String revision() {
        return this.revision;
    }
}
