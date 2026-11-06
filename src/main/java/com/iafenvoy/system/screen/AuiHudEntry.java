package com.iafenvoy.system.screen;

import com.iafenvoy.saturn.screen.hud.AbstractHudEntry;
import com.iafenvoy.saturn.screen.hud.HudEditScreen;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared GUI/document positioning observes committed geometry without owning card content or input.
 */
public abstract class AuiHudEntry extends AbstractHudEntry {
    public record Layout(String template, String panel, double width, double height, double widthLimit,
                         double heightLimit) {
    }

    private final Layout layout;
    private double cssWidth;
    private double cssHeight;
    private int viewportWidth;
    private int width;
    private int height;
    private float scale = 1;
    private Placement applied;

    protected AuiHudEntry(String key, Layout layout) {
        super(key, 0, 0);
        this.layout = layout;
        this.cssWidth = layout.width();
        this.cssHeight = layout.height();
    }

    protected abstract String previewKey();

    @Override
    public int layoutWidth() {
        return this.width;
    }

    @Override
    public int layoutHeight() {
        return this.height;
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    @Override
    public void refreshPlacement() {
        // Resolve the same CSS viewport even when no notice exists, so the editor always has a usable frame.
        this.resize(ApricityViewport.spec(this.layout.template()).createState(this.layout.template()).resolve(Minecraft.getInstance().getWindow()));
        super.refreshPlacement();
    }

    private void resize(ApricityViewport viewport) {
        if (this.viewportWidth != viewport.layoutWidth()) {
            this.viewportWidth = viewport.layoutWidth();
            this.cssWidth = Math.min(this.layout.width(), this.viewportWidth * this.layout.widthLimit());
        }
        this.scale = viewport.renderScale();
        this.setSize((int) Math.ceil(this.cssWidth * this.scale),
                (int) Math.ceil(Math.min(this.cssHeight, viewport.layoutHeight() * this.layout.heightLimit()) * this.scale));
    }

    public void apply(Document document) {
        try (Document.ContextScope ignored = Document.withContext(document)) {
            Element panel = document.getElementById(this.layout.panel());
            if (panel == null) return;
            this.resize(document.getViewport());
            // Read committed, untransformed geometry only: scaleX must not shrink the editor's bounds,
            // and observing placement must never start another recursive layout measurement.
            Rect rect = panel.getLayout().getCommittedRectIfValid();
            if (rect != null) {
                Size size = rect.getElementSize();
                if (size.width() > 0 && size.height() > 0) {
                    this.cssWidth = size.width();
                    this.cssHeight = size.height();
                    this.resize(document.getViewport());
                }
            }
            super.refreshPlacement();
            Position top = document.guiToDocumentPosition(new Position(this.x(), this.y()));
            boolean bottom = this.anchor().atBottom();
            Position end = document.guiToDocumentPosition(new Position(0, this.y() + this.layoutHeight()));
            Position window = document.guiToDocumentPosition(new Position(0, Minecraft.getInstance().getWindow().getGuiScaledHeight()));
            Placement next = new Placement(panel, top.x, bottom ? window.y - end.y : top.y, bottom);
            if (next.equals(this.applied)) return;
            this.applied = next;
            panel.setInlineStyleProperty("left", next.left() + "px");
            panel.setInlineStyleProperty("top", bottom ? "auto" : next.edge() + "px");
            panel.setInlineStyleProperty("bottom", bottom ? next.edge() + "px" : "auto");
        }
    }

    public void detach() {
        this.applied = null;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof HudEditScreen)) return;
        // A passive preview keeps editing independent of queue contents, narration and AUI mouse handlers.
        int x = this.x(), y = this.y();
        int padding = Math.max(5, Math.round(22 * this.scale));
        graphics.fill(x, y, x + this.width, y + this.height, 0xDD0B0E14);
        // Saturn already draws the entry title above the preview frame.
        int lineY = y + padding;
        for (FormattedCharSequence line : minecraft.font.split(Component.translatable(this.previewKey()), Math.max(1, this.width - padding * 2))) {
            if (lineY + minecraft.font.lineHeight > y + this.height - padding) break;
            graphics.text(minecraft.font, line, x + padding, lineY, 0xFFE8E8E8, false);
            lineY += minecraft.font.lineHeight + 2;
        }
    }

    private record Placement(Element panel, double left, double edge, boolean bottom) {
    }
}
