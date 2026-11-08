package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.AssistantThemes;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import org.jspecify.annotations.NonNull;

import java.net.URISyntaxException;

/**
 * Builds page bindings and observes layout published by AUI, without running a second render lifecycle.
 */
public abstract class AssistantPageScreen extends ApricityScreen {
    private final String templatePath;

    protected AssistantPageScreen(String templatePath) {
        super(templatePath);
        this.templatePath = templatePath;
    }

    @Override
    protected final void init() {
        AssistantPages.prepare(this.templatePath);
        super.init();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            AssistantThemes.apply(document);
            this.initPage(document);
        } catch (RuntimeException | Error error) {
            document.remove();
            throw error;
        }
    }

    protected abstract void initPage(Document document);

    protected void layoutReady(Document document) {
    }

    public static void openLink(String url) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            // Reuse vanilla URL validation, chatLinks/chatLinksPrompt and return to the actual active screen.
            ConfirmLinkScreen.confirmLinkNow(minecraft.screen, Util.parseAndValidateUntrustedUri(url));
        } catch (URISyntaxException ignored) { /* Invalid external links remain inert. */ }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Document document = this.getLinkedDocument();
        if (document != null && !document.isDisposed()) {
            // Resource reloads may replace the body. Never advance layout or motion during extraction.
            AssistantThemes.apply(document);
            if (!document.getLayout().needsInitialFullCommit() && !document.hasPendingRenderState())
                try (Document.ContextScope ignored = Document.withContext(document)) {
                    this.layoutReady(document);
                }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
