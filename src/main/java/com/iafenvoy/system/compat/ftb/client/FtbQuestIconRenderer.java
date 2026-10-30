package com.iafenvoy.system.compat.ftb.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.OutputTargets;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Replay native icon extraction into AUI's active target/projection instead of the vanilla GUI renderer.
 */
final class FtbQuestIconRenderer {
    private static final ByteBufferBuilder VERTICES = new ByteBufferBuilder(16384);

    private FtbQuestIconRenderer() {
    }

    static <T extends Icon<T>> void draw(Icon<T> icon, PoseStack pose, int x, int y, int width, int height) {
        Minecraft client = Minecraft.getInstance();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, 0, 0);
        icon.getRenderer().render(icon.self(), graphics, x, y, width, height);
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        state.forEachElement(element -> drawElement(element, matrix), GuiRenderState.TraverseRange.ALL);
        state.forEachText(text -> text.font.drawInBatch(text.text, text.x, text.y, text.color, text.dropShadow,
                new Matrix4f(pose.last().pose()).mul(expand(text.pose)), client.renderBuffers().bufferSource(),
                Font.DisplayMode.NORMAL, text.backgroundColor, 15728880));
        state.forEachItem(item -> {
            PoseStack itemPose = new PoseStack();
            itemPose.mulPose(pose.last().pose());
            itemPose.mulPose(expand(item.pose()));
            itemPose.translate(item.x() + 8, item.y() + 8, 0);
            itemPose.scale(16, -16, 16);
            client.gameRenderer.getLighting().setupFor(item.itemStackRenderState().usesBlockLight()
                    ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            try {
                item.itemStackRenderState().submit(itemPose, client.gameRenderer.getSubmitNodeStorage(),
                        15728880, OverlayTexture.NO_OVERLAY, 0);
                client.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
            } finally {
                client.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            }
        });
        client.renderBuffers().bufferSource().endBatch();
    }

    private static Matrix4f expand(Matrix3x2fc pose) {
        return new Matrix4f().m00(pose.m00()).m01(pose.m01()).m10(pose.m10()).m11(pose.m11())
                .m30(pose.m20()).m31(pose.m21());
    }

    private static void drawElement(GuiElementRenderState element, Matrix4f matrix) {
        var target = OutputTargets.currentTarget();
        if (target == null) return;
        var color = target.getColorTextureView();
        var depth = target.useDepth ? target.getDepthTextureView() : null;
        if (target == Minecraft.getInstance().getMainRenderTarget()) {
            if (RenderSystem.outputColorTextureOverride != null) color = RenderSystem.outputColorTextureOverride;
            if (RenderSystem.outputDepthTextureOverride != null) depth = RenderSystem.outputDepthTextureOverride;
        }
        if (color == null) return;
        var pipeline = element.pipeline();
        BufferBuilder buffer = new BufferBuilder(VERTICES, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
        element.buildVertices(buffer);
        var mesh = buffer.build();
        if (mesh == null) return;
        try (mesh) {
            // Upload before opening the pass; reuse Minecraft's per-format buffers and the live AUI scissor.
            var vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            var indices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
            var indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
            var transforms = RenderSystem.getDynamicUniforms().writeTransform(matrix,
                    new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "system_ftb_icon", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                TextureSetup textures = element.textureSetup();
                if (textures.texure0() != null) pass.bindTexture("Sampler0", textures.texure0(), textures.sampler0());
                if (textures.texure1() != null) pass.bindTexture("Sampler1", textures.texure1(), textures.sampler1());
                if (textures.texure2() != null) pass.bindTexture("Sampler2", textures.texure2(), textures.sampler2());
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        }
    }
}
