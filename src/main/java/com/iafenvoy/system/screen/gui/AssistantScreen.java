package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.runtime.client.AssistantClient;
import com.iafenvoy.system.util.Json;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.util.List;
import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * AUI only receives plain text and code-created actions, never model HTML.
 */
public final class AssistantScreen extends AssistantInputScreen {
    private long revision = -1;
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
    protected void init() {
        super.init();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        for (String id : List.of("brand", "subtitle", "nav_chat", "nav_player", "nav_items", "nav_quests", "nav_signin", "nav_inbox",
                "privacy_heading", "privacy_text", "send", "close", "forget", "history", "welcome_title", "welcome_text", "item_search",
                "signin_title", "signin_text", "signin_action", "quest_refresh", "quest_claimable",
                "nav_shop", "shop_search", "shop_refresh", "shop_text", "nav_usage", "nav_settings"))
            text(document.getElementById(id), tr("system.ui." + id));
        Element input = document.getElementById("message");
        input.setAttribute("placeholder", tr("system.ui.message_placeholder"));
        document.getElementById("item_query").setAttribute("placeholder", tr("system.ui.item_placeholder"));
        document.getElementById("shop_query").setAttribute("placeholder", tr("system.ui.shop_placeholder"));
        bind(document, "close", this::onClose);
        bind(document, "send", this::sendMessage);
        bind(document, "nav_chat", () -> this.show("chat"));
        bind(document, "nav_player", () -> {
            this.show("chat");
            AssistantClient.send(ResultTypes.PLAYER_ACTION.of(EmptyInput.INSTANCE), "");
        });
        bind(document, "nav_items", () -> this.show("items"));
        bind(document, "nav_quests", () -> {
            this.show("quests");
            AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", 0, false)), "");
        });
        bind(document, "nav_signin", () -> this.show("signin"));
        bind(document, "nav_inbox", () -> {
            this.show("chat");
            AssistantClient.send(ResultTypes.INBOX_ACTION.of(EmptyInput.INSTANCE), "");
        });
        bind(document, "nav_shop", () -> {
            this.show("shop");
            AssistantClient.send(ResultTypes.SHOP_ACTION.of(new ShopQueryInput("", 0)), "");
        });
        bind(document, "nav_usage", () -> AssistantClient.command("usage"));
        bind(document, "nav_settings", () -> this.minecraft.setScreen(new AssistantSettingsScreen(this, AssistantSettingsScreen.Tab.GENERAL)));
        bind(document, "shop_refresh", () -> AssistantClient.send(ResultTypes.SHOP_ACTION.of(new ShopQueryInput("", 0)), ""));
        bind(document, "shop_search", () -> {
            String query = document.getElementById("shop_query").getValue();
            AssistantClient.send(ResultTypes.SHOP_ACTION.of(new ShopQueryInput(query == null ? "" : query.strip(), 0)), "");
        });
        bind(document, "signin_action", () -> {
            this.show("chat");
            AssistantClient.send(ResultTypes.SIGN_IN_ACTION.of(EmptyInput.INSTANCE), "");
        });
        bind(document, "item_search", () -> {
            String query = document.getElementById("item_query").getValue();
            if (query != null && !query.isBlank())
                AssistantClient.send(ResultTypes.ITEMS_ACTION.of(new ItemQueryInput(query.strip(), 0)), "");
        });
        bind(document, "quest_refresh", () -> AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", 0, false)), ""));
        bind(document, "quest_claimable", () -> AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", 0, true)), ""));
        bind(document, "forget", () -> AssistantClient.send(AgentRequest.Control.FORGET));
        bind(document, "history", () -> {
            this.show("chat");
            AssistantClient.send(AgentRequest.Control.HISTORY);
        });
        this.show(this.page);
        this.revision = -1;
        this.renderEntries();
    }

    private void show(String next) {
        this.page = next;
        Document document = this.getLinkedDocument();
        if (document == null) return;
        for (String value : List.of("chat", "items", "quests", "signin", "shop"))
            document.getElementById("page_" + value).setInlineStyleProperty("display", value.equals(next) ? "flex" : "none");
        for (String value : List.of("chat", "items", "quests", "signin", "shop"))
            document.getElementById("nav_" + value).setClassName(value.equals(next) ? "nav active" : "nav");
        // The result stream remains visible beneath each specialised toolbar.
        document.getElementById("conversation").setInlineStyleProperty("display", "flex");
    }

    @Override
    public void tick() {
        super.tick();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        if (this.revision != AssistantClient.revision()) this.renderEntries();
    }

    private void renderEntries() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.revision = AssistantClient.revision();
        document.getElementById("nav_quests").setInlineStyleProperty("display", AssistantClient.questAvailable() ? "block" : "none");
        Element list = document.getElementById("conversation");
        list.setInnerHTML("");
        List<AssistantClient.ChatEntry> entries = AssistantClient.entries();
        for (AssistantClient.ChatEntry entry : entries) {
            Element row = element(document, "div", entry.player() ? "message user" : "message assistant", "");
            row.appendChild(element(document, "div", "message_label", tr(entry.player() ? "system.ui.you" : "system.ui.assistant")));
            if (!entry.text().isBlank()) row.appendChild(element(document, "div", "message_text", entry.text()));
            AgentResult result = entry.result();
            if (result != null) {
                row.appendChild(element(document, "div", "message_text", tr(result.messageKey())));
                if (!result.text().isBlank()) row.appendChild(element(document, "div", "message_text", result.text()));
                for (AgentResult.Card card : result.cards()) {
                    Element cardElement = element(document, "div", "result_card", "");
                    if (!card.titleKey().equals(result.messageKey()))
                        cardElement.appendChild(element(document, "div", "card_title", tr(card.titleKey())));
                    String formatted = formatCard(card);
                    if (!formatted.isBlank())
                        cardElement.appendChild(element(document, "div", "card_body",
                                card.titleKey().equals("system.result.quest") ? formatted : Json.clip(formatted, 6000)));
                    for (AgentResult.Action action : card.actions()) {
                        Element button = element(document, "button", card.data().value() instanceof ResultTypes.QuestList ? "card_action quest_row" : "card_action", actionLabel(card, action));
                        button.addEventListener("click", event -> AssistantClient.send(action, ""));
                        cardElement.appendChild(button);
                    }
                    row.appendChild(cardElement);
                }
            }
            list.appendChild(row);
        }
        document.getElementById("welcome").setInlineStyleProperty("display", entries.isEmpty() ? "flex" : "none");
        text(document.getElementById("status"), tr(AssistantClient.busy() ? "system.ui.working" : "system.ui.ready"));
        text(document.getElementById("send"), tr(AssistantClient.busy() ? "system.ui.working" : "system.ui.send"));
        list.scrollTop = list.scrollHeight;
        list.targetScrollTop = list.scrollHeight;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
