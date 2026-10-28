package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.module.client.ClientModuleRegistry;
import com.iafenvoy.system.module.client.ClientModules;
import com.iafenvoy.system.screen.card.CardTemplates;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * AUI only receives plain text and code-created actions, never model HTML.
 */
public final class AssistantScreen extends AssistantInputScreen {
    private long revision = -1;
    private long templateRevision = -1;
    private String page = "chat";

    public AssistantScreen() {
        this("");
    }

    public AssistantScreen(String draft) {
        super("system/assistant.html", draft);
        this.setPauseGame(false);
        this.setShowDefaultBackground(false);
    }

    @Override
    protected void initPage(Document document) {
        super.initPage(document);
        for (String id : List.of("brand", "privacy_heading", "privacy_text", "send", "close", "welcome_title", "welcome_text"))
            text(document.getElementById(id), tr("system.ui." + id));
        document.getElementById("message").setAttribute("placeholder", tr("system.ui.message_placeholder"));
        bind(document, "close", this::onClose);
        bind(document, "send", this::sendMessage);
        ClientModules.registry().bindWorkspace(this, document);
        this.showPage(this.page);
        this.revision = -1;
        this.renderEntries();
    }

    public void showPage(String next) {
        this.page = next;
        Document document = this.getLinkedDocument();
        if (document == null) return;
        boolean available = ClientModules.registry().workspace().stream().anyMatch(entry -> entry.page().equals(next) && entry.available());
        if (!available) this.page = "chat";
        for (ClientModuleRegistry.Workspace entry : ClientModules.registry().workspace()) {
            if (!entry.navigation().isBlank()) {
                Element navigation = document.getElementById(entry.navigation());
                navigation.setInlineStyleProperty("display", entry.available() ? "block" : "none");
                navigation.setClassName(!entry.page().isBlank() && entry.page().equals(this.page) ? "nav active" : "nav");
            }
            if (!entry.page().isBlank())
                document.getElementById("page_" + entry.page()).setInlineStyleProperty("display", entry.available() && entry.page().equals(this.page) ? "flex" : "none");
        }
        document.getElementById("conversation").setInlineStyleProperty("display", "block");
    }

    @Override
    public void tick() {
        super.tick();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        if (this.revision != AssistantClient.revision() || this.templateRevision != CardTemplates.revision())
            this.renderEntries();
    }

    private void renderEntries() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.revision = AssistantClient.revision();
        this.templateRevision = CardTemplates.revision();
        text(document.getElementById("brand"), tr("system.ui.assistant"));
        text(document.getElementById("welcome_title"), tr(AssistantClient.system().name()));
        text(document.getElementById("welcome_text"), tr(AssistantClient.system().description()));
        this.showPage(this.page);
        Element list = document.getElementById("conversation");
        list.setInnerHTML("");
        List<AssistantClientState.ChatEntry> entries = AssistantClient.state().entries();
        for (AssistantClientState.ChatEntry entry : entries) {
            Element row = element(document, "div", entry.player() ? "message user" : "message assistant", "");
            row.appendChild(element(document, "div", "message_label", tr(entry.player() ? "system.ui.you" : AssistantClient.system().name())));
            if (!entry.text().isBlank()) row.appendChild(element(document, "div", "message_text", entry.text()));
            AgentResult result = entry.result();
            if (result != null) {
                row.appendChild(element(document, "div", "message_text", tr(result.messageKey())));
                if (!result.text().isBlank()) row.appendChild(element(document, "div", "message_text", result.text()));
                for (AgentResult.Card card : result.cards())
                    row.appendChild(renderCard(document, card, result.messageKey()));
            }
            list.appendChild(row);
        }
        document.getElementById("welcome").setInlineStyleProperty("display", entries.isEmpty() ? "flex" : "none");
        text(document.getElementById("status"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.ready"));
        text(document.getElementById("send"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.send"));
        list.scrollTop = list.scrollHeight;
        list.targetScrollTop = list.scrollHeight;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
