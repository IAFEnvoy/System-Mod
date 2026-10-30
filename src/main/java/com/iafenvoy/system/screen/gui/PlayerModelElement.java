package com.iafenvoy.system.screen.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * Inventory-style entity submission inside AUI's active transform, clip and output target.
 */
public final class PlayerModelElement extends MinecraftElement {
    private final UUID playerId;
    private final boolean followMouse;
    private final float yaw;
    private final float pitch;
    private final float scale;
    private final float offsetX;
    private final float offsetY;
    private final Element unavailable;
    private boolean available;

    public PlayerModelElement(Document document, UUID playerId, boolean followMouse, float yaw, float pitch,
                              float scale, float offsetX, float offsetY) {
        super(document, "div");
        this.playerId = playerId;
        this.followMouse = followMouse;
        this.yaw = yaw;
        this.pitch = pitch;
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.unavailable = init(AssistantView.element(document, "div", "card_context player_preview_empty",
                AssistantView.tr("system.ui.player_preview_unavailable")));
        this.appendChild(this.unavailable);
        this.updateAvailability();
    }

    private @Nullable AbstractClientPlayer player() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getUUID().equals(this.playerId)) return client.player;
        return client.level != null && client.level.getPlayerByUUID(this.playerId) instanceof AbstractClientPlayer player ? player : null;
    }

    private void updateAvailability() {
        boolean available = this.player() != null;
        if (available == this.available) return;
        this.available = available;
        this.unavailable.setInlineStyleProperty("display", available ? "none" : "flex");
    }

    @Override
    public void tick() {
        super.tick();
        this.updateAvailability();
        if (this.available) this.requestRepaint();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) return;
        AbstractClientPlayer player = this.player();
        if (player == null) return;
        Position position = Rect.of(this).getContentPosition();
        Size size = Box.of(this).innerSize();
        if (size.width() <= 16 || size.height() <= 16) return;
        Minecraft client = Minecraft.getInstance();
        float centerX = (float) (position.x + size.width() / 2) + this.offsetX;
        float centerY = (float) (position.y + size.height() / 2) + this.offsetY;
        float yaw = this.yaw;
        float pitch = this.pitch;
        boolean following = this.followMouse && !client.mouseHandler.isMouseGrabbed();
        if (following) {
            // Undo viewport and CSS transforms before comparing the cursor with the painted model center.
            Position mouse = this.document.getMouseDocumentPosition();
            double[] local = Base.toLocalPoint(this, mouse.x, mouse.y);
            yaw = (float) Math.atan((centerX - local[0]) / 40.0F) * 20.0F;
            pitch = (float) -Math.atan((centerY - local[1]) / 40.0F) * 20.0F;
        }
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        EntityRenderState state = dispatcher.getRenderer(player).createRenderState(player, client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (!(state instanceof LivingEntityRenderState living)) return;
        // Change only the detached render state, never the actual player's rotation or pose.
        living.bodyRot = 180.0F + yaw;
        living.yRot = following ? yaw : 0.0F;
        living.xRot = pitch;
        living.boundingBoxWidth /= living.scale;
        living.boundingBoxHeight /= living.scale;
        living.scale = 1.0F;
        living.shadowPieces.clear();
        living.outlineColor = 0;
        living.nameTag = null;
        living.scoreText = null;
        living.displayFireAnimation = false;
        living.lightCoords = 15728880;
        float scale = this.scale * (float) Math.min((size.width() - 16) / (living.boundingBoxWidth + 0.5F),
                (size.height() - 16) / (living.boundingBoxHeight + 0.125F));
        Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(-pitch));
        CameraRenderState camera = new CameraRenderState();
        camera.orientation = tilt.conjugate(new Quaternionf()).rotateY((float) Math.PI);

        Base.commitLocalDraws();
        float x = (float) position.x;
        float y = (float) position.y;
        float width = (float) size.width();
        float height = (float) size.height();
        Mask.pushMask(poseStack, x, y, width, height, RenderNode.SQUARE_CLIP_RADII);
        poseStack.pushPose();
        try {
            poseStack.translate(centerX, centerY, Base.getGuiItemModelZ());
            // Keep model depth in AUI's item band regardless of the CSS preview height.
            poseStack.scale(scale, scale, -16.0F);
            poseStack.translate(0, living.boundingBoxHeight / 2, 0);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI).mul(tilt));
            client.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            FeatureRenderDispatcher features = client.gameRenderer.getFeatureRenderDispatcher();
            dispatcher.submit(living, camera, 0, 0, 0, poseStack, features.getSubmitNodeStorage());
            features.renderAllFeatures();
            client.renderBuffers().bufferSource().endBatch();
        } finally {
            client.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            poseStack.popPose();
            Mask.popMask(poseStack, x, y, width, height, RenderNode.SQUARE_CLIP_RADII);
        }
    }
}
