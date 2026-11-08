package com.iafenvoy.system.compat.ftb.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

/**
 * Replays FTB's icon draw into the GUI batch AUI is currently building; 1.21.1 draws inside AUI's transform/clip pass
 * instead of replaying recorded GUI render states.
 */
final class FtbQuestIconRenderer {
    private FtbQuestIconRenderer() {
    }

    static void draw(Icon icon, PoseStack pose, int x, int y, int width, int height) {
        Minecraft client = Minecraft.getInstance();
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        graphics.pose().last().pose().set(pose.last().pose());
        graphics.pose().last().normal().set(pose.last().normal());
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            icon.draw(graphics, x, y, width, height);
            graphics.flush();
        } finally {
            // GuiGraphics.flush enables depth testing; restore AUI's state before the next card layer.
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }
}