package com.iafenvoy.system.compat.ftb.client;

import com.iafenvoy.system.agent.builtin.output.QuestIconOutput;
import com.iafenvoy.system.screen.gui.AssistantTooltip;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loaded reflectively only on clients with FTB. Native icon drawing stays inside AUI's transform/clip pass.
 */
public final class FtbQuestIconElement extends MinecraftElement implements AssistantTooltip {
    private final Icon icon;
    private final List<Component> tooltip;

    private FtbQuestIconElement(Document document, Icon icon, Component typeName, QuestIconOutput preview) {
        super(document, "div");
        this.icon = icon;
        List<Component> lines = new ArrayList<>();
        lines.add(preview.title());
        if (!preview.title().equals(typeName)) lines.add(typeName.copy().withStyle(ChatFormatting.GRAY));
        lines.addAll(preview.tooltip());
        this.tooltip = lines.stream().map(LegacyText::component).toList();
        this.setClassName("quest_type_icon");
    }

    public static Element create(Document document, QuestIconOutput preview) {
        if (preview.kind() == QuestIconOutput.Kind.TASK) {
            TaskType type = TaskTypes.TYPES.get(preview.type());
            return type == null ? null : new FtbQuestIconElement(document, type.getIconSupplier(), type.getDisplayName(), preview);
        }
        RewardType type = RewardTypes.TYPES.get(preview.type());
        return type == null ? null : new FtbQuestIconElement(document, type.getIconSupplier(), type.getDisplayName(), preview);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) return;
        Position position = Rect.of(this).getContentPosition();
        Size size = Box.of(this).innerSize();
        if (size.width() <= 0 || size.height() <= 0) return;
        Base.commitLocalDraws();
        Minecraft client = Minecraft.getInstance();
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        graphics.pose().last().pose().set(poseStack.last().pose());
        graphics.pose().last().normal().set(poseStack.last().normal());
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            this.icon.draw(graphics, (int) position.x, (int) position.y, (int) size.width(), (int) size.height());
            graphics.flush();
        } finally {
            // GuiGraphics.flush enables depth testing; restore AUI's state before the next card layer.
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.renderTooltip(Minecraft.getInstance().font, this.tooltip, Optional.empty(), mouseX, mouseY);
    }
}
