package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.module.client.ClientModuleBindings;
import com.iafenvoy.system.screen.card.CardContext;
import com.iafenvoy.system.screen.card.CardTemplates;
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
    private long templateRevision = -1;

    public AssistantSimpleScreen(String draft) {
        super("system/assistant-simple.html", draft);
        this.setPauseGame(false);
        this.setShowDefaultBackground(false);
    }

    @Override
    protected void initPage(Document document) {
        super.initPage(document);
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
        if (this.revision != AssistantClient.revision() || this.templateRevision != CardTemplates.revision())
            this.renderCurrent();
    }

    private void renderCurrent() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.revision = AssistantClient.revision();
        this.templateRevision = CardTemplates.revision();
        text(document.getElementById("speaker"), tr("system.ui.assistant"));
        AssistantClientState.ChatEntry current = AssistantClient.state().current();
        String request = current == null ? "" : current.text();
        text(document.getElementById("current_request"), request);
        document.getElementById("current_request").setInlineStyleProperty("display", request.isBlank() ? "none" : "block");
        AgentResult result = current == null ? null : current.result();
        Element speech = document.getElementById("current_result");
        speech.setInnerHTML("");
        List<String> lines = new ArrayList<>();
        if (result == null) {
            if (!AssistantClient.state().busy()) lines.add(tr(AssistantClient.system().description()));
            lines.add(tr(AssistantClient.state().busy() ? "system.ui.simple_working" : "system.ui.simple_welcome"));
        } else {
            lines.add(tr(result.messageKey()));
            if (!result.text().isBlank()) lines.add(result.text());
            speech.appendChild(element(document, "div", "message_text", String.join("\n\n", lines)));
            int index = 1;
            for (AgentResult.Card card : result.cards()) {
                speech.appendChild(ClientModuleBindings.get().cards().presentation(card, result.messageKey(), CardContext.Mode.SIMPLE, index - 1).render(document));
                index += card.actions().size();
            }
            if (index > 1) speech.appendChild(element(document, "div", "card_context", tr("system.ui.simple_actions")));
        }
        if (result == null) text(speech, String.join("\n\n", lines));
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
