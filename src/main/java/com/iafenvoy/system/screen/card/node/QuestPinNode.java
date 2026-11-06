package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.card.DisplayCardKey;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Pins a quest through the generic display service; clicks never activate the enclosing quest cell.
 */
@EventBusSubscriber(Dist.CLIENT)
public record QuestPinNode(String quest) implements CardNode {
    public static final MapCodec<QuestPinNode> CODEC = Codec.STRING.fieldOf("quest").xmap(QuestPinNode::new, QuestPinNode::quest);
    private static long pinRevision = -1;
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
            ClientModuleCaches.DISPLAY_CARDS.toggle(new DisplayCardKey(QuestSystemModule.ID, quest));
        });
        return button;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        long revision = ClientModuleCaches.DISPLAY_CARDS.pinRevision();
        if (pinRevision == revision) return;
        pinRevision = revision;
        // Refresh existing buttons only when membership or pending state changes, never on progress-only pushes.
        for (Document document : Document.getAll()) {
            if (document.isDisposed()) continue;
            try (Document.ContextScope ignored = Document.withContext(document)) {
                for (Element element : document.getElements()) {
                    String quest = element.getAttribute("data-quest-pin");
                    if (!element.isConnected() || quest == null || quest.isBlank()) continue;
                    update(element, quest);
                }
            }
        }
    }

    private static void update(Element button, String quest) {
        DisplayCardKey key = new DisplayCardKey(QuestSystemModule.ID, quest);
        boolean pinned = ClientModuleCaches.DISPLAY_CARDS.contains(key);
        boolean pending = ClientModuleCaches.DISPLAY_CARDS.pending(key);
        if (button.isDisabled() != pending) button.setDisabled(pending);
        String label = AssistantView.tr(pinned ? "system.ui.quest_unpin" : "system.ui.quest_pin");
        if (!Boolean.toString(pinned).equals(button.getAttribute("aria-pressed")))
            button.setAttribute("aria-pressed", Boolean.toString(pinned));
        if (!label.equals(button.getAttribute("aria-label"))) button.setAttribute("aria-label", label);
        if (!label.equals(button.getAttribute("title"))) button.setAttribute("title", label);
    }
}
