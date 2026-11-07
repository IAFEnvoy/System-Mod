package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.module.client.ClientModuleBindingsCache;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.screen.AssistantGeometry;
import com.iafenvoy.system.screen.card.CardTemplates;
import com.iafenvoy.system.screen.card.CardUiState;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.style.Transition;

import java.util.*;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * AUI only receives plain text and code-created actions, never model HTML.
 */
public final class AssistantScreen extends AssistantInputScreen {
    // Keep independent card pages across transcript redraws and window resizing, only for this screen's lifetime.
    private final Map<UUID, CardUiState> cardPages = new HashMap<>();
    private List<AssistantClientState.ChatEntry> renderedEntries = List.of();
    private final List<Element> renderedRows = new ArrayList<>();
    private PendingScroll pendingScroll;
    private long revision = -1;
    private long templateRevision = -1;
    private String page = "chat";

    private record PendingScroll(double current, double target, boolean follow, boolean smooth) {
    }

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
        this.pendingScroll = null;
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
                display(navigation, entry.available() ? "block" : "none");
                String css = !entry.page().isBlank() && entry.page().equals(this.page) ? "nav active" : "nav";
                if (!css.equals(navigation.getClassName())) navigation.setClassName(css);
            }
            if (!entry.page().isBlank()) {
                // A module may be present in the registry while its page is omitted by a custom template.
                Element page = document.getElementById("page_" + entry.page());
                if (page != null)
                    display(page, entry.available() && entry.page().equals(this.page) ? "flex" : "none");
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
        boolean reload = this.revision < 0 || this.templateRevision != CardTemplates.revision();
        boolean redraw = reload || !this.renderedEntries.equals(entries);
        int firstNew = reload ? entries.size() : this.firstNewEntry(entries);
        this.revision = AssistantClient.revision();
        this.templateRevision = CardTemplates.revision();
        this.updateLayout(document, firstNew < entries.size(), () -> {
            text(document.getElementById("brand"), tr("system.ui.assistant"));
            text(document.getElementById("welcome_title"), tr(AssistantClient.system().name()));
            text(document.getElementById("welcome_text"), tr(AssistantClient.system().description()));
            this.updatePage(document);
            Element list = document.getElementById("conversation_entries");
            // Append/replace only changed rows; rebuilding the transcript also rebuilds every nested card grid.
            if (redraw) {
                Map<AssistantClientState.ChatEntry, Deque<Element>> reusable = new HashMap<>();
                // Resource reloads and screen reinitialization have a different DOM/template generation.
                for (int index = 0; index < this.renderedRows.size(); index++) {
                    Element row = this.renderedRows.get(index);
                    if (!reload && row.isConnected() && row.document == document)
                        reusable.computeIfAbsent(this.renderedEntries.get(index), _ -> new ArrayDeque<>()).add(row);
                }
                List<Element> rows = new ArrayList<>();
                Set<UUID> visibleCards = new HashSet<>();
                for (int index = 0; index < entries.size(); index++) {
                    AssistantClientState.ChatEntry entry = entries.get(index);
                    Deque<Element> candidates = reusable.get(entry);
                    Element row = candidates == null || candidates.isEmpty() ? this.renderEntry(document, entry, index >= firstNew) : candidates.removeFirst();
                    rows.add(row);
                    AgentResult result = entry.result();
                    if (result != null) for (CardUpdate card : result.cards()) visibleCards.add(card.ref().id());
                }
                reconcile(list, rows);
                this.renderedRows.clear();
                this.renderedRows.addAll(rows);
                this.cardPages.keySet().retainAll(visibleCards);
                this.renderedEntries = entries;
            }
            for (Element button : list.querySelectorAll("button[data-requires-idle]")) {
                boolean disabled = AssistantClient.state().busy() || "true".equals(button.getAttribute("data-card-disabled"));
                if (button.isDisabled() != disabled) button.setDisabled(disabled);
            }
            display(document.getElementById("welcome"), entries.isEmpty() ? "flex" : "none");
            display(document.getElementById("conversation"), entries.isEmpty() ? "none" : "grid");
            text(document.getElementById("status"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.ready"));
            text(document.getElementById("send"), tr(AssistantClient.state().busy() ? "system.ui.working" : "system.ui.send"));
        });
    }

    private Element renderEntry(Document document, AssistantClientState.ChatEntry entry, boolean animate) {
        String css = entry.player() ? "message user" : "message assistant";
        Element row = Element.init(element(document, "div", css + (animate ? " message_new" : ""), ""));
        row.appendChild(element(document, "div", "message_label", tr(entry.player() ? "system.ui.you" : AssistantClient.system().name())));
        if (!entry.text().isBlank()) row.appendChild(element(document, "div", "message_text", entry.text()));
        AgentResult result = entry.result();
        if (result != null) for (CardUpdate card : result.cards())
            row.appendChild(renderCard(document, card, result.messageKey(), this.cardPages.computeIfAbsent(card.ref().id(), _ -> new CardUiState())));
        return row;
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
            Element list = document.getElementById("conversation");
            double current = list.getScrollTop(), target = list.getTargetScrollTop();
            // Capture the old viewport before mutation; restore once the next frame has committed the new one.
            if (this.pendingScroll == null) {
                boolean atBottom = list.getLayout().getCommittedRectIfValid() == null
                        ? target <= 2 : target >= AssistantGeometry.scrollLimit(list, false) - 2;
                this.pendingScroll = new PendingScroll(current, target,
                        atBottom && target >= current - 2, smooth);
            }
            update.run();
        }
    }

    @Override
    protected void layoutReady(Document document) {
        super.layoutReady(document);
        Element list = document.getElementById("conversation");
        if (this.pendingScroll != null && list.getLayout().getCommittedRectIfValid() != null) {
            PendingScroll pending = this.pendingScroll;
            this.pendingScroll = null;
            try (Document.ContextScope ignored = Document.withContext(document); GeometryQueryScope _ = GeometryQueryScope.open()) {
                double limit = AssistantGeometry.scrollLimit(list, false);
                list.setScrollTop(pending.follow ? limit : Math.clamp(pending.target, 0, limit));
                double current = pending.follow && !pending.smooth && pending.target <= pending.current ? limit : Math.clamp(pending.current, 0, limit);
                if (Double.compare(list.scrollTop, current) != 0) {
                    list.scrollTop = current;
                    list.getLayout().invalidateScrollVersion();
                    document.markDirty(list, Drawer.REPAINT | Drawer.HITTEST);
                }
            }
        }
        // Measure new cards once before arming motion; settled history has no transform transitions to rebuild.
        for (Element row : this.renderedRows) {
            Element.DOMTokenList classes = row.getClassList();
            if (classes.contains("message_new")) {
                classes.remove("message_new");
                classes.add("message_entering");
            } else if (classes.contains("message_entering") && !Transition.isActive(row))
                classes.remove("message_entering");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
