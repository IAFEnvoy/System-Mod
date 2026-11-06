package com.iafenvoy.system.compat.ftb.client;

import com.iafenvoy.system.data.output.QuestIconOutput;
import com.iafenvoy.system.screen.AssistantGeometry;
import com.iafenvoy.system.screen.gui.AssistantTooltip;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loaded reflectively only on clients with FTB. Native icon drawing stays inside AUI's transform/clip pass.
 */
public final class FtbQuestIconElement extends MinecraftElement implements AssistantTooltip {
    private final Icon<?> icon;
    private final List<Component> tooltip;

    private FtbQuestIconElement(Document document, Icon<?> icon, List<Component> tooltip) {
        super(document, "div");
        this.icon = icon;
        this.tooltip = tooltip.stream().map(LegacyText::component).toList();
        this.setClassName("quest_type_icon");
    }

    public static Element create(Document document, QuestIconOutput preview) {
        if (preview.kind() == QuestIconOutput.Kind.TASK) {
            TaskType type = TaskTypes.TYPES.get(preview.type());
            return type == null ? null : createType(document, type.getIcon(), type.getDisplayName(), preview);
        }
        RewardType type = RewardTypes.TYPES.get(preview.type());
        return type == null ? null : createType(document, type.getIcon(), type.getDisplayName(), preview);
    }

    private static Element createType(Document document, Icon<?> icon, Component typeName, QuestIconOutput preview) {
        List<Component> lines = new ArrayList<>();
        lines.add(preview.title());
        if (!preview.title().equals(typeName)) lines.add(typeName.copy().withStyle(ChatFormatting.GRAY));
        lines.addAll(preview.tooltip());
        return new FtbQuestIconElement(document, icon, lines);
    }

    public static Element createQuest(Document document, String questId, String title) {
        if (!ClientQuestFile.exists()) return null;
        long id;
        try {
            id = Long.parseUnsignedLong(questId, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
        Quest quest = ClientQuestFile.getInstance().getQuest(id);
        if (quest == null || !quest.isValid()) return null;
        // getIcon honors the configured icon and FTB's theme/task fallback; never replace it with a type icon.
        Icon<?> icon = quest.getIcon();
        if (icon.isEmpty()) return null;
        return new FtbQuestIconElement(document, icon, title.isBlank() ? List.of() : List.of(Component.literal(title)));
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) return;
        Rect rect = this.getLayout().getCommittedRectIfValid();
        if (rect == null) return;
        Position position = rect.getContentPosition();
        Size size = AssistantGeometry.innerSize(rect);
        if (size.width() <= 0 || size.height() <= 0) return;
        Base.commitLocalDraws();
        FtbQuestIconRenderer.draw(this.icon, poseStack, (int) position.x, (int) position.y,
                (int) size.width(), (int) size.height());
    }

    @Override
    public void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font, this.tooltip, Optional.empty(), mouseX, mouseY);
    }
}
