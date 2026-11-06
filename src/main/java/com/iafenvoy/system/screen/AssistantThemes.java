package com.iafenvoy.system.screen;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.file.ClientConfig;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Shares AUI's document-local theme selection across System pages without retaining their documents.
 */
public final class AssistantThemes {
    private static final String REQUESTED = "data-system-theme";
    // Use AUI's marker so its switcher can retire sheets installed by this bridge too.
    private static final String SHEET_MARK = "data-theme-css";

    private AssistantThemes() {
    }

    public static boolean select(String theme) {
        ClientConfig settings = ClientConfig.INSTANCE.get();
        ClientConfig.INSTANCE.set(new ClientConfig(settings.simpleMode(), theme, settings.cardAnimations(), settings.cardsOnOtherScreens()));
        boolean saved = ClientConfig.INSTANCE.save();
        for (Document document : List.copyOf(Document.getAll()))
            if (document.isActive() && document.getPath().startsWith("system/"))
                apply(document);
        return saved;
    }

    public static void apply(Document document) {
        if (document.isDisposed() || !document.getPath().startsWith("system/") || document.body == null || document.head == null)
            return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            // AUI 1.2.9 normalizes away authored <html> attributes; copy the HUD marker from the retained body.
            if (document.body.getClassList().contains("system_hud") && !document.documentElement.getClassList().contains("system_hud"))
                document.documentElement.getClassList().add("system_hud");
            install(document);
        }
    }

    private static void install(Document document) {
        String theme = ClientConfig.INSTANCE.get().theme();
        if (theme.equals(document.body.getAttribute(REQUESTED))) return;
        document.body.setAttribute(REQUESTED, theme);
        String href = "/apricityui/theme/" + theme + "/" + theme + ".css";
        // The settings switcher has already loaded and adopted its own sheet before emitting its event.
        if (document.body.getClassList().contains(theme + "-theme")
                && document.head.querySelectorAll("link[data-theme-css]").stream()
                .anyMatch(link -> href.equals(link.getAttribute("href")))) return;

        Element link = Element.init(document.createElement("link"));
        link.setAttribute("rel", "stylesheet");
        link.setAttribute(SHEET_MARK, "1");
        link.setAttribute("href", href);
        link.addEventListener("load", event -> {
            if (document.isDisposed() || !link.isConnected()) return;
            // A newer choice wins even when an earlier asynchronous load finishes later.
            if (!theme.equals(ClientConfig.INSTANCE.get().theme())) {
                link.remove();
                return;
            }
            adopt(document, theme);
            for (Element previous : new ArrayList<>(document.head.querySelectorAll("link[data-theme-css]")))
                if (previous != link) previous.remove();
        });
        link.addEventListener("error", event -> {
            link.remove();
            // Retain the previous skin; do not retry a missing resource on every layout commit.
            SystemMod.LOGGER.warn("Unable to load assistant theme {}", theme);
        });
        document.head.appendChild(link);
    }

    private static void adopt(Document document, String theme) {
        Element.DOMTokenList classes = document.body.getClassList();
        for (String name : new ArrayList<>(document.body.getClassNames()))
            if (name.endsWith("-theme")) classes.remove(name);
        if (theme.equals(ClientConfig.DEFAULT_THEME)) classes.add("t08-arwes-theme");
        classes.add(theme + "-theme");
    }
}
