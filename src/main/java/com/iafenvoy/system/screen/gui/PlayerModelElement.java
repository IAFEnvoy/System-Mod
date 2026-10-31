package com.iafenvoy.system.screen.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/**
 * Renders the current client-side player model inside the AUI transform and clipping pass.
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
        float yaw = this.yaw, pitch = this.pitch;
        if (this.followMouse && !client.mouseHandler.isMouseGrabbed()) {
            Position mouse = this.document.getMouseDocumentPosition();
            double[] local = Base.toLocalPoint(this, mouse.x, mouse.y);
            yaw = (float) Math.atan((centerX - local[0]) / 40.0F) * 20.0F;
            pitch = (float) -Math.atan((centerY - local[1]) / 40.0F) * 20.0F;
        }
        float modelScale = this.scale * (float) Math.min((size.width() - 16) / (player.getBbWidth() + 0.5F),
                (size.height() - 16) / (player.getBbHeight() + 0.125F));
        Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(-pitch));
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Quaternionf camera = dispatcher.cameraOrientation();
        float bodyYaw = player.yBodyRot, entityYaw = player.getYRot(), entityPitch = player.getXRot();
        float headYaw = player.yHeadRot, oldHeadYaw = player.yHeadRotO;
        float[] color = RenderSystem.getShaderColor().clone();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        Base.commitLocalDraws();
        // Vanilla inventory helpers replace the scissor with GUI coordinates, losing AUI zoom and scroll transforms.
        float x = (float) position.x, y = (float) position.y;
        float width = (float) size.width(), height = (float) size.height();
        Mask.pushMask(poseStack, x, y, width, height, RenderNode.SQUARE_CLIP_RADII);
        poseStack.pushPose();
        try {
            player.yBodyRot = 180.0F + yaw;
            player.setYRot(180.0F + yaw * 2.0F);
            player.setXRot(pitch);
            player.yHeadRot = player.yHeadRotO = player.getYRot();
            poseStack.translate(centerX, centerY, Base.getGuiItemModelZ());
            // Fit the model in CSS pixels while keeping its depth in AUI's reserved item layer.
            poseStack.scale(modelScale, modelScale, -16.0F);
            poseStack.translate(0, player.getBbHeight() / 2.0F, 0);
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI).mul(tilt));
            dispatcher.overrideCameraOrientation(tilt.conjugate(new Quaternionf()).rotateY((float) Math.PI));
            Lighting.setupForEntityInInventory();
            EntityRenderer<? super AbstractClientPlayer> renderer = dispatcher.getRenderer(player);
            Vec3 offset = renderer.getRenderOffset(player, 1.0F);
            poseStack.translate(offset.x, offset.y, offset.z);
            MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
            // Render the vanilla player and its equipment without world shadows, fire or debug hitboxes.
            RenderSystem.runAsFancy(() -> renderer.render(player, 0.0F, 1.0F, poseStack, buffers, LightTexture.FULL_BRIGHT));
            buffers.endBatch();
        } finally {
            player.yBodyRot = bodyYaw;
            player.setYRot(entityYaw);
            player.setXRot(entityPitch);
            player.yHeadRot = headYaw;
            player.yHeadRotO = oldHeadYaw;
            dispatcher.overrideCameraOrientation(camera);
            poseStack.popPose();
            Lighting.setupFor3DItems();
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            Mask.popMask(poseStack, x, y, width, height, RenderNode.SQUARE_CLIP_RADII);
        }
    }
}
