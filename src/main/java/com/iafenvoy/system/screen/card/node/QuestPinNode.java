package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

/**
 * Pins a quest locally; clicks never activate the enclosing cell or dispatch a server action.
 */
public record QuestPinNode(String quest) implements CardNode {
    public static final MapCodec<QuestPinNode> CODEC = Codec.STRING.fieldOf("quest").xmap(QuestPinNode::new, QuestPinNode::quest);
    private static final String ICON = """
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M16 9V4l1-1H7l1 1v5l-3 3v2h14v-2l-3-3ZM12 14v7"/>
            </svg>
            """;

    @Override
    public MapCodec<QuestPinNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "quest_card_pin";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String quest = CardRenderer.rawValue(this.quest, view.context(), view.indices());
        if (quest.isBlank()) return null;
        Element button = Element.init(view.element("button", ""));
        button.setAttribute("type", "button");
        button.setAttribute("data-quest-pin", quest);
        button.setInnerHTML(ICON);
        update(button, quest);
        button.addEventListener("click", event -> {
            event.stopPropagation();
            event.preventDefault();
            if (!view.interactive() || !button.isConnected()) return;
            ClientModuleCaches.QUEST_PINS.toggle(quest);
            // History and the HUD can show the same quest; refresh only its existing buttons, not the cards.
            for (Document document : Document.getAll()) {
                if (document.isDisposed()) continue;
                boolean changed = false;
                try (Document.ContextScope ignored = Document.withContext(document)) {
                    for (Element element : document.getElements()) {
                        if (!element.isConnected() || !quest.equals(element.getAttribute("data-quest-pin"))) continue;
                        update(element, quest);
                        changed = true;
                    }
                }
                if (changed) AssistantPages.commit(document);
            }
        });
        return button;
    }

    private static void update(Element button, String quest) {
        boolean pinned = ClientModuleCaches.QUEST_PINS.contains(quest);
        String label = AssistantView.tr(pinned ? "system.ui.quest_unpin" : "system.ui.quest_pin");
        button.setAttribute("aria-pressed", Boolean.toString(pinned));
        button.setAttribute("aria-label", label);
        button.setAttribute("title", label);
    }
}
