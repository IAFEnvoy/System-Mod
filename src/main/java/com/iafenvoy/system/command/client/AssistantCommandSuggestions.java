package com.iafenvoy.system.command.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.runtime.client.AssistantClient;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local command and current-result completions; selecting a suggestion only edits the draft.
 */
public final class AssistantCommandSuggestions {
    private AssistantCommandSuggestions() {
    }

    public record Suggestion(String value, String hint) {
        public String label() {
            return this.hint.isBlank() ? this.value : this.value + " · " + this.hint;
        }
    }

    public static List<Suggestion> find(String draft) {
        String input = draft.stripLeading();
        if (input.isEmpty()) return List.of();
        String lower = input.toLowerCase(Locale.ROOT);
        String prefix = lower.startsWith("/system ") ? "/system " : input.startsWith("/") ? "/" : "";
        Map<String, Suggestion> candidates = new LinkedHashMap<>();
        if (!prefix.equals("/system ") && "/system ".startsWith(lower)) add(candidates, input, "/system ", "");
        for (String command : AssistantClient.commands()) add(candidates, input, prefix + command, "");
        for (String command : AssistantClientCommands.suggestions()) add(candidates, input, prefix + command, "");
        // Dynamic arguments come only from the current code-created result, never from inferred commands.
        AgentResult result = AssistantClient.current() == null ? null : AssistantClient.current().result();
        if (result != null) {
            int index = 1;
            for (AgentResult.Card card : result.cards()) {
                for (AgentResult.Action action : card.actions()) {
                    String hint = Json.clip(AssistantView.actionLabel(card, action), 96);
                    add(candidates, input, prefix + "select " + index, hint);
                    add(candidates, input, prefix + "选择 " + index++, hint);
                }
                if (!(card.data().value() instanceof ResultTypes.QuestList(List<QuestBriefOutput> quests))) continue;
                for (QuestBriefOutput quest : quests) {
                    String number = Integer.toString(quest.number()), title = quest.title();
                    for (String command : List.of("view", "查看")) {
                        add(candidates, input, prefix + command + " " + number, title);
                        add(candidates, input, prefix + command + " " + title, "");
                    }
                }
            }
        }
        return candidates.values().stream().limit(64).toList();
    }

    private static void add(Map<String, Suggestion> candidates, String input, String value, String hint) {
        value = value.replace('\r', ' ').replace('\n', ' ');
        hint = Json.clip(hint.replace('\r', ' ').replace('\n', ' '), 96);
        if (!value.equals(input) && value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
            candidates.putIfAbsent(value, new Suggestion(value, hint));
    }
}
