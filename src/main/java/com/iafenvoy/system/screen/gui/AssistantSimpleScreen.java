package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.client.ClientCards;
import com.iafenvoy.system.screen.card.CardPresentation;
import com.iafenvoy.system.screen.card.CardTemplates;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.Objects;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * One current exchange, no transcript or clickable capability controls.
 */
public final class AssistantSimpleScreen extends AssistantInputScreen {
    private long revision = -1;
    private long templateRevision = -1;
    private AgentResult renderedResult;
    private String renderedFallback = "";

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
        AssistantClientState.ChatEntry current = AssistantClient.state().current();
        String request = current == null ? "" : current.text();
        text(document.getElementById("current_request"), request);
        display(document.getElementById("current_request"), request.isBlank() ? "none" : "block");
        AgentResult result = current == null ? null : current.result();
        boolean busy = AssistantClient.state().busy();
        String fallback = result == null ? (busy ? "" : tr(AssistantClient.system().description()) + "\n\n")
                + tr(busy ? "system.ui.simple_working" : "system.ui.simple_welcome") : "";
        boolean redraw = this.revision < 0 || this.templateRevision != CardTemplates.revision()
                || !Objects.equals(this.renderedResult, result) || !this.renderedFallback.equals(fallback);
        this.revision = AssistantClient.revision();
        this.templateRevision = CardTemplates.revision();
        this.renderedResult = result;
        this.renderedFallback = fallback;
        text(document.getElementById("status"), tr(busy ? "system.ui.working" : "system.ui.ready"));
        text(document.getElementById("send"), tr(busy ? "system.ui.working" : "system.ui.send"));
        if (!redraw) return;
        Element speech = document.getElementById("current_result");
        speech.setInnerHTML("");
        if (result == null) text(speech, fallback);
        else {
            int index = 1;
            for (CardUpdate card : result.cards()) {
                speech.appendChild(CardPresentation.of(card, result.messageKey(), CardContext.Mode.SIMPLE, index - 1).render(document));
                index += ClientCards.view(card).buttons().size();
            }
            if (index > 1) speech.appendChild(element(document, "div", "card_context", tr("system.ui.simple_actions")));
        }
        Element area = document.getElementById("speech");
        area.scrollTop = 0;
        area.targetScrollTop = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
