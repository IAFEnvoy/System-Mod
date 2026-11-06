package com.iafenvoy.system.screen.card.node.media;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.FtbIconData;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * A single native FTB icon. Optional-class resolution and failure fallback have one entry point.
 */
public record FtbIconNode(String icon) implements TextProjectingNode {
    public static final MapCodec<FtbIconNode> CODEC = Codec.STRING.fieldOf("icon").xmap(FtbIconNode::new, FtbIconNode::icon);

    private static final class NativeFactory {
        private static final Method METHOD = resolve();
    }

    private static Method resolve() {
        if (!ModList.get().isLoaded("ftbquests")) return null;
        try {
            return Class.forName("com.iafenvoy.system.compat.ftb.client.FtbQuestIconElement").getMethod("create", Document.class, FtbIconData.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            SystemMod.LOGGER.warn("FTB icons are unavailable", error);
            return null;
        }
    }

    @Override
    public MapCodec<FtbIconNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "quest_icon_entry";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        FtbIconData icon = view.context().get(CardRenderer.binding(this.icon, view.indices()), FtbIconData.class);
        if (icon == null) return null;
        Element image = null;
        if (NativeFactory.METHOD != null) try {
            image = (Element) NativeFactory.METHOD.invoke(null, view.document(), icon);
        } catch (ReflectiveOperationException | LinkageError error) {
            SystemMod.LOGGER.warn("Failed to create FTB icon", error);
        }
        Element root = view.element("div", "");
        root.appendChild(image == null ? AssistantView.element(view.document(), "span", "card_body", LegacyText.formatted(icon.title())) : image);
        if (!icon.amount().isBlank())
            root.appendChild(AssistantView.element(view.document(), "span", "quest_icon_amount", icon.amount()));
        return root;
    }

    @Override
    public String text(CardRenderer.Text view) {
        FtbIconData icon = view.context().get(CardRenderer.binding(this.icon, view.indices()), FtbIconData.class);
        return icon == null ? "" : LegacyText.formatted(icon.title()) + (icon.amount().isBlank() ? "" : " · " + icon.amount());
    }
}
