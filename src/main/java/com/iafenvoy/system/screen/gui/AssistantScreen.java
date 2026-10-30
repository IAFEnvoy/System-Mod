package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.module.client.ClientModuleBindingsCache;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardTemplates;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;

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
        ClientModuleCaches.BINDINGS.get().bindWorkspace(this, document);
        this.revision = -1;
        this.renderEntries();
    }

    public void showPage(String next) {
        this.page = next;
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.updateLayout(document, () -> this.updatePage(document));
    }

    private void updatePage(Document document) {
        boolean available = ClientModuleCaches.BINDINGS.get().workspace().stream().anyMatch(entry -> entry.page().equals(this.page) && entry.available());
        if (!available) this.page = "chat";
        for (ClientModuleBindingsCache.Workspace entry : ClientModuleCaches.BINDINGS.get().workspace()) {
            if (!entry.navigation().isBlank()) {
                Element navigation = document.getElementById(entry.navigation());
                navigation.setInlineStyleProperty("display", entry.available() ? "block" : "none");
                navigation.setClassName(!entry.page().isBlank() && entry.page().equals(this.page) ? "nav active" : "nav");
            }
            if (!entry.page().isBlank())
                document.getElementById("page_" + entry.page()).setInlineStyleProperty("display", entry.available() && entry.page().equals(this.page) ? "flex" : "none");
        }
    }

    @Override
    public void tick() {
        super.tick();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        if (this.revision != AssistantClient.revision() || this.templateRevision != CardTemplates.revision())
            this.renderEntries();
    }

    public String page() {
        return this.page;
    }

    private void renderEntries() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.revision = AssistantClient.revision();
        this.templateRevision = CardTemplates.revision();
        this.updateLayout(document, () -> {
            text(document.getElementById("brand"), tr("system.ui.assistant"));
            text(document.getElementById("welcome_title"), tr(AssistantClient.system().name()));
            text(document.getElementById("welcome_text"), tr(AssistantClient.system().description()));
            this.updatePage(document);
            Element list = document.getElementById("conversation_entries");
            list.setInnerHTML("");
            List<AssistantClientState.ChatEntry> entries = AssistantClient.state().entries();
            for (AssistantClientState.ChatEntry entry : entries) {
                Element row = element(document, "div", entry.player() ? "message user" : "message assistant", "");
                row.appendChild(element(document, "div", "message_label", tr(entry.player() ? "system.ui.you" : AssistantClient.system().name())));
                if (!entry.text().isBlank()) row.appendChild(element(document, "div", "message_text", entry.text()));
                AgentResult result = entry.result();
                if (result != null) {
                    for (CardUpdate card : result.cards())
                        row.appendChild(renderCard(document, card, result.messageKey()));
                }
                list.appendChild(row);
            }
            document.getElementById("welcome").setInlineStyleProperty("display", entries.isEmpty() ? "flex" : "none");
            document.getElementById("conversation").setInlineStyleProperty("display", entries.isEmpty() ? "none" : "flex");
            text(document.getElementById("status"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.ready"));
            text(document.getElementById("send"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.send"));
        });
    }

    private void updateLayout(Document document, Runnable update) {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            AssistantPages.commit(document);
            Element list = document.getElementById("conversation");
            double limit = scrollLimit(list);
            double current = list.getScrollTop(), target = list.getTargetScrollTop();
            // Respect an in-progress upward scroll, even if its animation still starts at the bottom.
            boolean follow = Math.min(current, target) >= limit - 2;
            update.run();
            AssistantPages.commit(document);
            limit = scrollLimit(list);
            list.setScrollTop(follow ? limit : Math.clamp(target, 0, limit));
            list.scrollTop = follow ? limit : Math.clamp(current, 0, limit);
        }
    }

    private static double scrollLimit(Element list) {
        list.commitScrollMetricsFromLayout();
        return Math.max(0, list.scrollHeight - Box.of(list).innerSize().height());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
