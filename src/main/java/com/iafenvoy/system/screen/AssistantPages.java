package com.iafenvoy.system.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.Minecraft;

/**
 * Shared stylesheet preparation and layout commits for assistant pages and HUD documents.
 */
public final class AssistantPages {
    private AssistantPages() {
    }

    public static void prepare(String path) {
        HTML.TemplateResources template = findTemplate(path);
        if (template == null && HTML.reload(path)) template = findTemplate(path);
        if (template == null) return;
        ApricityViewport viewport = ApricityViewport.spec(path).createState(path).resolve(Minecraft.getInstance().getWindow());
        // DOM readyState does not wait for external CSS; prepare local styles before Document.create.
        StyleAsyncHandler.INSTANCE.warmUpTemplateStyles(template.path(), template.externalStyleSrcs(),
                template.inlineStyles(), new Size(viewport.layoutWidth(), viewport.layoutHeight()));
    }

    private static HTML.TemplateResources findTemplate(String path) {
        for (HTML.TemplateResources template : HTML.preparedTemplateResources())
            if (template.path().equals(path)) return template;
        return null;
    }

    public static void commit(Document document) {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            document.commitStyleRecalc();
            if (document.hasPendingRenderState()) document.commitRenderState();
        }
    }
}
