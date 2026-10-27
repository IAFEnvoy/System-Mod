package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.*;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
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
    protected void initPage(Document document) {
        super.initPage(document);
        for (String id : List.of("brand", "nav_chat", "nav_player", "nav_items", "nav_quests", "nav_signin", "nav_inbox",
                "privacy_heading", "privacy_text", "send", "close", "forget", "history", "welcome_title", "welcome_text", "item_search",
                "signin_title", "signin_text", "signin_action", "quest_refresh", "quest_claimable",
                "nav_shop", "shop_search", "shop_refresh", "shop_text", "nav_lottery", "lottery_search", "lottery_refresh", "lottery_text", "nav_usage", "nav_settings"))
            text(document.getElementById(id), tr("system.ui." + id));
        Element input = document.getElementById("message");
        input.setAttribute("placeholder", tr("system.ui.message_placeholder"));
        document.getElementById("item_query").setAttribute("placeholder", tr("system.ui.item_placeholder"));
        document.getElementById("shop_query").setAttribute("placeholder", tr("system.ui.shop_placeholder"));
        document.getElementById("lottery_query").setAttribute("placeholder", tr("system.ui.lottery_placeholder"));
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
            AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", false)), "");
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
        bind(document, "nav_lottery", () -> {
            this.show("lottery");
            AssistantClient.send(ResultTypes.LOTTERY_ACTION.of(new LotteryQueryInput("", 0)), "");
        });
        bind(document, "lottery_refresh", () -> AssistantClient.send(ResultTypes.LOTTERY_ACTION.of(new LotteryQueryInput("", 0)), ""));
        bind(document, "lottery_search", () -> {
            String query = document.getElementById("lottery_query").getValue();
            AssistantClient.send(ResultTypes.LOTTERY_ACTION.of(new LotteryQueryInput(query == null ? "" : query.strip(), 0)), "");
        });
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
        bind(document, "quest_refresh", () -> AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", false)), ""));
        bind(document, "quest_claimable", () -> AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", true)), ""));
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
        for (String value : List.of("chat", "items", "quests", "signin", "shop", "lottery"))
            document.getElementById("page_" + value).setInlineStyleProperty("display", value.equals(next) ? "flex" : "none");
        for (String value : List.of("chat", "items", "quests", "signin", "shop", "lottery"))
            document.getElementById("nav_" + value).setClassName(value.equals(next) ? "nav active" : "nav");
        // The result stream remains visible beneath each specialised toolbar.
        document.getElementById("conversation").setInlineStyleProperty("display", "block");
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
        text(document.getElementById("brand"), tr("system.ui.assistant"));
        text(document.getElementById("welcome_title"), tr(AssistantClient.system().name()));
        text(document.getElementById("welcome_text"), tr(AssistantClient.system().description()));
        document.getElementById("nav_quests").setInlineStyleProperty("display", AssistantClient.state().questAvailable() ? "block" : "none");
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
