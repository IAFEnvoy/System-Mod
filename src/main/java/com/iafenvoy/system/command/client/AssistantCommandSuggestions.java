package com.iafenvoy.system.command.client;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.screen.gui.AssistantView;

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
        for (String command : AssistantClient.state().commands()) add(candidates, input, prefix + command, "");
        for (String command : AssistantClientCommands.suggestions()) add(candidates, input, prefix + command, "");
        // Dynamic arguments come only from the current code-created result, never from inferred commands.
        AgentResult result = AssistantClient.state().current() == null ? null : AssistantClient.state().current().result();
        if (result != null) {
            int index = 1;
            for (CardUpdate card : result.cards()) {
                for (CardButton action : ClientCards.view(card).buttons()) {
                    String text = AssistantView.actionLabel(card, action);
                    String hint = MiscUtil.clip(text, 96);
                    add(candidates, input, prefix + "select " + index, hint);
                    add(candidates, input, prefix + "选择 " + index++, hint);
                }
                if (!(card.data().orElse(null) instanceof ResultTypes.QuestList(List<QuestBriefOutput> quests)))
                    continue;
                for (QuestBriefOutput quest : quests) {
                    String number = Integer.toString(quest.number()), title = quest.title().getString();
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
        hint = MiscUtil.clip(hint.replace('\r', ' ').replace('\n', ' '), 96);
        if (!value.equals(input) && value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
            candidates.putIfAbsent(value, new Suggestion(value, hint));
    }
}
