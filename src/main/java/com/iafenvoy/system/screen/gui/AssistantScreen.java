package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.module.client.ClientModuleBindingsCache;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardPageState;
import com.iafenvoy.system.screen.card.CardTemplates;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.render.GeometryQueryScope;

import java.util.*;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * AUI only receives plain text and code-created actions, never model HTML.
 */
public final class AssistantScreen extends AssistantInputScreen {
    // Keep independent card pages across transcript redraws and window resizing, only for this screen's lifetime.
    private final Map<UUID, CardPageState> cardPages = new HashMap<>();
    private List<AssistantClientState.ChatEntry> renderedEntries = List.of();
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
        for (String id : List.of("brand", "send", "welcome_title", "welcome_text"))
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
        this.updateLayout(document, false, () -> this.updatePage(document));
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
            if (!entry.page().isBlank()) {
                // A module may be present in the registry while its page is omitted by a custom template.
                Element page = document.getElementById("page_" + entry.page());
                if (page != null)
                    page.setInlineStyleProperty("display", entry.available() && entry.page().equals(this.page) ? "flex" : "none");
            }
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
        List<AssistantClientState.ChatEntry> entries = AssistantClient.state().entries();
        boolean redraw = this.revision < 0 || this.templateRevision != CardTemplates.revision()
                || !this.renderedEntries.equals(entries);
        int firstNew = this.revision < 0 || this.templateRevision != CardTemplates.revision()
                ? entries.size() : this.firstNewEntry(entries);
        this.revision = AssistantClient.revision();
        this.templateRevision = CardTemplates.revision();
        this.updateLayout(document, firstNew < entries.size(), () -> {
            text(document.getElementById("brand"), tr("system.ui.assistant"));
            text(document.getElementById("welcome_title"), tr(AssistantClient.system().name()));
            text(document.getElementById("welcome_text"), tr(AssistantClient.system().description()));
            this.updatePage(document);
            Element list = document.getElementById("conversation_entries");
            // Status-only updates keep live nodes so an entrance animation is not interrupted or restarted.
            if (redraw) {
                list.setInnerHTML("");
                Set<UUID> visibleCards = new HashSet<>();
                for (int index = 0; index < entries.size(); index++) {
                    AssistantClientState.ChatEntry entry = entries.get(index);
                    String css = entry.player() ? "message user" : "message assistant";
                    Element row = element(document, "div", css + (index >= firstNew ? " message_new" : ""), "");
                    row.appendChild(element(document, "div", "message_label", tr(entry.player() ? "system.ui.you" : AssistantClient.system().name())));
                    if (!entry.text().isBlank())
                        row.appendChild(element(document, "div", "message_text", entry.text()));
                    AgentResult result = entry.result();
                    if (result != null) {
                        for (CardUpdate card : result.cards()) {
                            UUID id = card.ref().id();
                            visibleCards.add(id);
                            row.appendChild(renderCard(document, card, result.messageKey(), this.cardPages.computeIfAbsent(id, unused -> new CardPageState())));
                        }
                    }
                    list.appendChild(row);
                }
                this.cardPages.keySet().retainAll(visibleCards);
                this.renderedEntries = entries;
            }
            for (Element button : list.querySelectorAll("button[data-requires-idle]"))
                button.setDisabled(AssistantClient.state().busy());
            document.getElementById("welcome").setInlineStyleProperty("display", entries.isEmpty() ? "flex" : "none");
            document.getElementById("conversation").setInlineStyleProperty("display", entries.isEmpty() ? "none" : "flex");
            text(document.getElementById("status"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.ready"));
            text(document.getElementById("send"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.send"));
        });
        // Commit the starting pose first; removing it then starts AUI's bounded paint-only transitions.
        if (firstNew < entries.size())
            for (Element row : document.querySelectorAll(".message_new"))
                row.getClassList().remove("message_new");
    }

    private int firstNewEntry(List<AssistantClientState.ChatEntry> entries) {
        if (this.renderedEntries.isEmpty()) return 0;
        AssistantClientState.ChatEntry previous = this.renderedEntries.getLast();
        // Card revisions may replace a snapshot; its UUID still identifies the same transcript entry.
        for (int index = entries.size() - 1; index >= 0; index--) {
            AssistantClientState.ChatEntry entry = entries.get(index);
            if (entry == previous || entry.result() != null && previous.result() != null
                    && !entry.result().cards().isEmpty() && !previous.result().cards().isEmpty()
                    && entry.result().cards().getFirst().ref().id().equals(previous.result().cards().getFirst().ref().id()))
                return index + 1;
        }
        return entries.size();
    }

    private void updateLayout(Document document, boolean smooth, Runnable update) {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            AssistantPages.commit(document);
            Element list = document.getElementById("conversation");
            double current = list.getScrollTop(), target = list.getTargetScrollTop();
            boolean follow;
            try (GeometryQueryScope geometry = GeometryQueryScope.open()) {
                // A target at the bottom keeps following during easing; scrolling upward cancels following.
                follow = target >= scrollLimit(list) - 2 && target >= current - 2;
            }
            update.run();
            AssistantPages.commit(document);
            // Do not share geometry caches across DOM mutations; setScrollTop also queries the box size.
            try (GeometryQueryScope geometry = GeometryQueryScope.open()) {
                double limit = scrollLimit(list);
                list.setScrollTop(follow ? limit : Math.clamp(target, 0, limit));
                // Let AUI ease toward the new target; initial layout still opens directly at the bottom.
                list.scrollTop = follow && !smooth && target <= current ? limit : Math.clamp(current, 0, limit);
            }
        }
    }

    private static double scrollLimit(Element list) {
        // AUI's layout commit already measured scrollHeight; querying a limit must not measure the subtree again.
        return Math.max(0, list.scrollHeight - Box.of(list).innerSize().height());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
