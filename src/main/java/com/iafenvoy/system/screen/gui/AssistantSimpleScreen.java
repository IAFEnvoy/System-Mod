package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.ArrayList;
import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * One current exchange, no transcript or clickable capability controls.
 */
public final class AssistantSimpleScreen extends AssistantInputScreen {
    private long revision = -1;

    public AssistantSimpleScreen(String draft) {
        super("system/assistant-simple.html", draft);
        this.setPauseGame(false);
        this.setShowDefaultBackground(false);
    }

    @Override
    protected void init() {
        super.init();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        text(document.getElementById("speaker"), tr("system.ui.assistant"));
        Element input = document.getElementById("message");
        input.setAttribute("placeholder", tr("system.ui.simple_placeholder"));
        bind(document, "send", this::sendMessage);
        this.revision = -1;
        this.renderCurrent();
    }

    @Override
    public void tick() {
        super.tick();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        if (this.revision != AssistantClient.revision()) this.renderCurrent();
    }

    private void renderCurrent() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.revision = AssistantClient.revision();
        AssistantClientState.ChatEntry current = AssistantClient.state().current();
        String request = current == null ? "" : current.text();
        text(document.getElementById("current_request"), request);
        document.getElementById("current_request").setInlineStyleProperty("display", request.isBlank() ? "none" : "block");
        AgentResult result = current == null ? null : current.result();
        List<String> lines = new ArrayList<>();
        if (result == null)
            lines.add(tr(AssistantClient.state().busy() ? "system.ui.simple_working" : "system.ui.simple_welcome"));
        else {
            lines.add(tr(result.messageKey()));
            if (!result.text().isBlank()) lines.add(result.text());
            int index = 1;
            for (AgentResult.Card card : result.cards()) {
                if (!card.titleKey().equals(result.messageKey())) lines.add(tr(card.titleKey()));
                String data = formatCard(card, false);
                if (!data.isBlank()) lines.add(data);
                for (AgentResult.Action action : card.actions()) {
                    boolean questRow = card.data().value() instanceof ResultTypes.QuestList && action.labelKey().equals("system.action.quest_detail");
                    lines.add((questRow ? "" : index + ". ") + actionLabel(card, action));
                    index++;
                }
            }
            if (index > 1) lines.add(tr("system.ui.simple_actions"));
        }
        Element speech = document.getElementById("current_result");
        text(speech, String.join("\n\n", lines));
        AssistantItems.render(document, document.getElementById("current_items"),
                result == null ? List.of() : AssistantItems.groups(result), false);
        Element area = document.getElementById("speech");
        area.scrollTop = 0;
        area.targetScrollTop = 0;
        text(document.getElementById("status"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.ready"));
        text(document.getElementById("send"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.send"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
