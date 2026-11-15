package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.screen.AssistantGeometry;
import com.iafenvoy.system.screen.emotion.EmotionFrame;
import com.iafenvoy.system.screen.emotion.EmotionStateMachine;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.spi.TextureKey;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Changing speech frames only repaints this fixed-size element; no attributes or CSS change per tick.
 */
public final class EmotionElement extends MinecraftElement {
    private final BooleanSupplier speaking;
    private final EmotionStateMachine machine;
    private EmotionFrame frame;

    public EmotionElement(Document document, UUID card, EmotionStateMachine machine) {
        this(document, machine, () -> AssistantSpeech.speaking(card));
    }

    public EmotionElement(Document document, EmotionStateMachine machine, BooleanSupplier speaking) {
        super(document, "div");
        this.machine = machine;
        this.speaking = speaking;
    }

    @Override
    public void tick() {
        super.tick();
        EmotionFrame frame = this.machine.frame(this.speaking.getAsBoolean());
        if (!Objects.equals(frame, this.frame)) {
            this.frame = frame;
            this.requestRepaint();
        }
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) return;
        Rect rect = this.getLayout().getCommittedRectIfValid();
        if (rect == null) return;
        EmotionFrame frame = this.machine.frame(this.speaking.getAsBoolean());
        Position origin = rect.getContentPosition();
        Size size = AssistantGeometry.innerSize(rect);
        // Offsets only affect drawing within the existing clip/transform; the DOM box never moves.
        float edge = (float) Math.min(size.width(), size.height());
        if (edge <= 0) return;
        ImageDrawer.draw(poseStack, TextureKey.of(frame.texture().toString()),
                (float) (origin.x + (size.width() - edge) / 2) + frame.offset().x(),
                (float) (origin.y + (size.height() - edge) / 2) + frame.offset().y(), edge, edge, true);
    }
}
