package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.screen.gui.AssistantView;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Local paging shared by action lists and grids; only the current slice creates DOM and item renderers.
 */
public final class CardPagination {
    private CardPagination() {
    }

    public static <T> Element render(CardRenderer.Render view, String key, List<T> entries, int pageSize,
                                     boolean keepControls, BiConsumer<Element, List<T>> content) {
        // AUI replaces raw tags on insertion, so callbacks must capture the initialized live container.
        Element root = Element.init(AssistantView.element(view.document(), "div", "card_paged", ""));
        CardPageState state = view.context().get("local_pages", CardPageState.class);
        CardPageState pages = state == null ? new CardPageState() : state;
        renderPage(view, root, key, pages, entries, Math.max(1, pageSize), keepControls, content);
        return root;
    }

    private static <T> void renderPage(CardRenderer.Render view, Element root, String key, CardPageState pages,
                                       List<T> entries, int pageSize, boolean keepControls,
                                       BiConsumer<Element, List<T>> content) {
        int total = entries.isEmpty() ? 1 : (entries.size() - 1) / pageSize + 1;
        int page = Math.clamp(pages.page(key), 0, total - 1);
        pages.page(key, page);
        root.setInnerHTML("");
        Element body = AssistantView.element(view.document(), "div", "", "");
        int from = page * pageSize;
        content.accept(body, entries.subList(from, from + Math.min(pageSize, entries.size() - from)));
        root.appendChild(body);
        if (!keepControls && total == 1) return;
        Element controls = AssistantView.element(view.document(), "div", "card_pagination", "");
        pageButton(view, root, controls, key, pages, entries, pageSize, keepControls, content,
                page - 1, page > 0, "system.ui.card_previous_page");
        controls.appendChild(AssistantView.element(view.document(), "span", "card_page_label",
                Component.translatable("system.ui.card_page", page + 1, total).getString()));
        pageButton(view, root, controls, key, pages, entries, pageSize, keepControls, content,
                page + 1, page + 1 < total, "system.ui.card_next_page");
        root.appendChild(controls);
    }

    private static <T> void pageButton(CardRenderer.Render view, Element root, Element controls, String key,
                                       CardPageState pages, List<T> entries, int pageSize, boolean keepControls,
                                       BiConsumer<Element, List<T>> content, int page, boolean enabled, String label) {
        Element button = AssistantView.element(view.document(), "button", "card_action", AssistantView.tr(label));
        button.setAttribute("type", "button");
        button.setDisabled(!enabled);
        button.addEventListener("click", event -> {
            event.stopPropagation();
            if (!enabled || !view.interactive() || !root.isConnected()) return;
            pages.page(key, page);
            renderPage(view, root, key, pages, entries, pageSize, keepControls, content);
            Element scroll = view.document().getElementById("notification_scroll");
            if (scroll != null) scroll.scrollTop = scroll.targetScrollTop = 0;
            // Queue layout for AUI's frame boundary, never traverse the document inside click dispatch.
            root.getLayout().invalidateLayoutSubtree();
        });
        controls.appendChild(button);
    }
}
