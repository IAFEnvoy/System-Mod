package com.iafenvoy.system.screen.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.output.QuestRewardOutput;
import com.iafenvoy.system.util.TypeDispatch;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Shared plain-text formatting for both layouts.
 */
public final class AssistantView {
    private static final TypeDispatch<AgentResult.Action, String> ACTION_LABELS = new TypeDispatch<AgentResult.Action, String>((action, _) -> tr(action.labelKey()))
            .register(ResultTypes.QuestList.class, AssistantView::questRowLabel)
            .register(QuestOutput.class, AssistantView::rewardLabel);
    private static final TypeDispatch<AgentResult.Card, String> CARD_FORMATTERS = new TypeDispatch<AgentResult.Card, String>((card, _) -> format(card.data().json(), 0))
            .register(ResultTypes.QuestList.class, (_, _) -> tr("system.ui.quest_list_hint"))
            .register(QuestOutput.class, (card, _) -> formatQuest(card));

    private AssistantView() {
    }

    public static String actionLabel(AgentResult.Card card, AgentResult.Action action) {
        return ACTION_LABELS.apply(action, card.data().value());
    }

    private static String questRowLabel(AgentResult.Action action, ResultTypes.QuestList list) {
        String label = tr(action.labelKey());
        if (action.call().value() instanceof QuestDetailInput detail) {
            for (QuestBriefOutput quest : list.quests())
                if (quest.id().equals(detail.questId()))
                    return quest.number() + ". " + quest.title() + " · " + quest.chapter() + " · " + (int) quest.progress() + "%";
        }
        return label;
    }

    private static String rewardLabel(AgentResult.Action action, QuestOutput quest) {
        String label = tr(action.labelKey());
        if (action.call().value() instanceof QuestClaimInput(
                String claimRef, Optional<Integer> choice1
        )) {
            for (QuestRewardOutput reward : quest.rewards()) {
                if (reward.claimRef().isEmpty() || !reward.claimRef().get().equals(claimRef)) continue;
                if (choice1.isPresent() && reward.choices().isPresent()) {
                    int choice = choice1.get() - reward.choiceOffset();
                    if (choice >= 0 && choice < reward.choices().get().size())
                        return label + " · " + reward.choices().get().get(choice);
                }
                return label + " · " + reward.title();
            }
        }
        return label;
    }

    public static String formatCard(AgentResult.Card card) {
        // Summary rows are numbered detail actions; never dump quest IDs or nested objectives here.
        return CARD_FORMATTERS.apply(card, card.data().value());
    }

    private static String formatQuest(AgentResult.Card card) {
        JsonObject visible = card.data().json().getAsJsonObject();
        for (String field : List.of("rewardOffset", "nextRewardOffset", "hasMoreRewards", "taskOffset", "nextTaskOffset", "hasMoreTasks",
                "descriptionOffset", "nextDescriptionOffset", "hasMoreDescription"))
            visible.remove(field);
        return format(visible, 0);
    }

    public static String format(JsonElement value, int depth) {
        if (value == null || value.isJsonNull() || depth > 5) return "";
        if (value.isJsonPrimitive()) {
            String text = value.getAsString();
            if (Set.of("PENDING", "DELIVERING", "DELIVERED", "DELIVERY_UNKNOWN", "CLAIM_EXECUTED", "CLAIMED", "CAN_CLAIM", "CANT_CLAIM", "ALREADY_CLAIMED",
                    "ITEMS", "SYSTEM_COIN", "COMPLETED", "PREPARED", "PAYMENT_PENDING", "PAYMENT_UNKNOWN").contains(text))
                return tr("system.state." + text.toLowerCase(Locale.ROOT));
            return text.equals("true") ? tr("system.ui.yes") : text.equals("false") ? tr("system.ui.no") : text;
        }
        List<String> lines = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) lines.add(format(element, depth + 1));
        } else for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
            if (Set.of("claimRef", "quoteRef", "catalogRevision").contains(field.getKey())) continue;
            if ((field.getKey().equals("coins") && field.getValue().isJsonPrimitive() && field.getValue().getAsLong() == 0)
                    || (field.getKey().equals("items") && field.getValue().isJsonArray() && field.getValue().getAsJsonArray().isEmpty()))
                continue;
            if (field.getKey().equals("error") && field.getValue().isJsonPrimitive()) {
                String error = field.getValue().getAsString();
                if (!error.isBlank()) lines.add(tr("system.error." + error.toLowerCase(Locale.ROOT)));
                continue;
            }
            String key = "system.field." + field.getKey();
            lines.add(tr(key) + ": " + format(field.getValue(), depth + 1));
        }
        return String.join("\n", lines);
    }

    public static Element element(Document document, String tag, String css, String value) {
        Element result = document.createElement(tag);
        result.setClassName(css);
        result.setTextContent(value);
        return result;
    }

    public static void bind(Document document, String id, Runnable action) {
        document.getElementById(id).addEventListener("click", event -> action.run());
    }

    /**
     * Button-backed switches use one state attribute for appearance, accessibility and form reads.
     */
    public static void bindSwitch(Document document, String id, Consumer<Boolean> changed) {
        Element control = document.getElementById(id);
        control.addEventListener("click", event -> {
            if (control.isDisabled()) return;
            boolean checked = !switchChecked(control);
            switchChecked(control, checked);
            changed.accept(checked);
        });
    }

    public static boolean switchChecked(Element control) {
        return "true".equals(control.getAttribute("aria-checked"));
    }

    public static void switchChecked(Element control, boolean checked) {
        if (switchChecked(control) != checked) control.setAttribute("aria-checked", Boolean.toString(checked));
    }

    public static void text(Element element, String value) {
        if (element != null) element.setTextContent(value);
    }

    public static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
