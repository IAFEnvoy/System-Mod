package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Resolves a server-selected quest's native icon from FTB's synced client definitions, without linking FTB here.
 */
public record QuestIconNode(String quest, String title) implements CardNode {
    public static final MapCodec<QuestIconNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("quest").forGetter(QuestIconNode::quest),
            Codec.STRING.lenientOptionalFieldOf("title", "").forGetter(QuestIconNode::title)
    ).apply(i, QuestIconNode::new));

    private static final class NativeFactory {
        private static final Method METHOD = factory();
    }

    private static Method factory() {
        if (!ModList.get().isLoaded("ftbquests")) return null;
        try {
            return Class.forName("com.iafenvoy.system.compat.ftb.client.FtbQuestIconElement")
                    .getMethod("createQuest", Document.class, String.class, String.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            SystemMod.LOGGER.warn("FTB quest icons are unavailable", error);
            return null;
        }
    }

    @Override
    public MapCodec<QuestIconNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "quest_card_icon";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        if (NativeFactory.METHOD == null) return null;
        String quest = CardRenderer.rawValue(this.quest, view.context(), view.indices());
        if (quest.isBlank()) return null;
        try {
            Element icon = (Element) NativeFactory.METHOD.invoke(null, view.document(), quest,
                    CardRenderer.value(this.title, view.context(), view.indices()));
            if (icon == null) return null;
            Element result = view.element("div", "");
            result.appendChild(icon);
            return result;
        } catch (ReflectiveOperationException | LinkageError error) {
            SystemMod.LOGGER.warn("Failed to create icon for quest {}", quest, error);
            return null;
        }
    }
}
