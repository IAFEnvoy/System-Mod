package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.builtin.output.QuestIconOutput;
import com.iafenvoy.system.card.CardContext;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Native optional type icons are selected by trusted DTOs, while templates control grouping and placement.
 */
public record IconsNode(String icons, String label) implements CardNode {
    public static final MapCodec<IconsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("icons").forGetter(IconsNode::icons),
            Codec.STRING.optionalFieldOf("label", "").forGetter(IconsNode::label)
    ).apply(i, IconsNode::new));

    // Resolve once, and never link optional FTB classes when the dependency is absent.
    private static final class NativeFactory {
        private static final Method METHOD = factory();
    }

    private static Method factory() {
        if (!ModList.get().isLoaded("ftbquests")) return null;
        try {
            return Class.forName("com.iafenvoy.system.compat.ftb.client.FtbQuestIconElement")
                    .getMethod("create", Document.class, QuestIconOutput.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            SystemMod.LOGGER.warn("FTB quest icons are unavailable; using text previews", error);
            return null;
        }
    }

    @Override
    public MapCodec<IconsNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "quest_icon_group";
    }

    private List<QuestIconOutput> previews(CardContext context, List<Integer> indices) {
        List<?> source = context.get(CardRenderer.binding(this.icons, indices), List.class);
        return source == null ? List.of() : source.stream().filter(QuestIconOutput.class::isInstance).map(QuestIconOutput.class::cast).toList();
    }

    @Override
    public Element render(CardRenderer.Render view) {
        List<QuestIconOutput> previews = this.previews(view.context(), view.indices());
        if (previews.isEmpty()) return null;
        Element result = view.element("div", "");
        String label = CardRenderer.value(this.label, view.context(), view.indices());
        if (!label.isBlank())
            result.appendChild(AssistantView.element(view.document(), "div", "item_group_label", label + ":"));
        Element row = AssistantView.element(view.document(), "div", "quest_icon_row", "");
        for (QuestIconOutput preview : previews) {
            Element icon = null;
            if (NativeFactory.METHOD != null) try {
                icon = (Element) NativeFactory.METHOD.invoke(null, view.document(), preview);
            } catch (ReflectiveOperationException | LinkageError error) {
                SystemMod.LOGGER.warn("Failed to create quest icon for {}", preview.type(), error);
            }
            Element entry = AssistantView.element(view.document(), "div", "quest_icon_entry", "");
            entry.appendChild(Objects.requireNonNullElseGet(icon, () -> AssistantView.element(view.document(), "span", "card_body", this.fallback(preview))));
            if (!preview.amount().isBlank())
                entry.appendChild(AssistantView.element(view.document(), "span", "quest_icon_amount", preview.amount()));
            row.appendChild(entry);
        }
        result.appendChild(row);
        return result;
    }

    @Override
    public String text(CardRenderer.Text view) {
        return String.join(view.separator(), this.previews(view.context(), view.indices()).stream()
                .map(preview -> this.fallback(preview) + (preview.amount().isBlank() ? "" : " · " + preview.amount())).toList());
    }

    private String fallback(QuestIconOutput preview) {
        return String.join("\n", Stream.concat(Stream.of(preview.title()), preview.tooltip().stream())
                .map(LegacyText::formatted).distinct().toList());
    }
}
