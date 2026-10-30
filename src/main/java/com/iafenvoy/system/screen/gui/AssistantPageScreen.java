package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.screen.AssistantPages;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;

/**
 * Publishes a page only after its styles, bindings and initial content have been laid out.
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
        document.setManuallyRendered(true);
        try (Document.ContextScope ignored = Document.withContext(document)) {
            this.initPage(document);
            document.applyViewport(true);
            AssistantPages.commit(document);
            document.setManuallyRendered(false);
        } catch (RuntimeException | Error error) {
            document.remove();
            throw error;
        }
    }

    protected abstract void initPage(Document document);

    public static void openLink(String url) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            // Reuse vanilla URL validation, chatLinks/chatLinksPrompt and return to the actual active screen.
            ConfirmLinkScreen.confirmLinkNow(minecraft.screen, Util.parseAndValidateUntrustedUri(url));
        } catch (URISyntaxException ignored) { /* Invalid external links remain inert. */ }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Document document = this.getLinkedDocument();
        // Commit content changes before geometry queries, rather than waiting for a mouse hit test.
        if (document != null) AssistantPages.commit(document);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
