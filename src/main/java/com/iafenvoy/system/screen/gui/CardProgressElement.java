package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.screen.card.CardUiState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.render.Rect;

/**
 * Paint-only progress animation keeps the full-size fill's layout stable throughout the transition.
 */
public final class CardProgressElement extends MinecraftElement {
    private final CardUiState.Progress progress;
    private boolean animating = true;

    public CardProgressElement(Document document, CardUiState.Progress progress) {
        super(document, "div");
        this.progress = progress;
        this.setClassName("card_progress_fill");
    }

    @Override
    public void tick() {
        super.tick();
        // Keep the final repaint as the animation settles, then leave idle HUD cards untouched.
        if (this.animating) this.requestRepaint();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rect = this.getLayout().getCommittedRectIfValid();
        if (rect == null) return;
        double value = this.progress.value();
        this.animating = this.progress.animating();
        if (value <= 0) return;
        Position origin = rect.getBodyRectPosition();
        poseStack.pushPose();
        try {
            poseStack.translate(origin.x, origin.y, 0);
            PoseMatrices.scale2D(poseStack, (float) (value / 100), 1);
            poseStack.translate(-origin.x, -origin.y, 0);
            super.drawPhase(poseStack, phase);
        } finally {
            poseStack.popPose();
        }
    }
}
