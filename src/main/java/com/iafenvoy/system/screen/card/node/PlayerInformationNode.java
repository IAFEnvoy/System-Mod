package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.data.output.PlayerInformationOutput;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Displays Saturn-prepared rows without field or group allowlists; JSON only sets an exclusive threshold.
 */
public record PlayerInformationNode(int importance) implements CardNode {
    public static final MapCodec<PlayerInformationNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.lenientOptionalFieldOf("importance", 0).forGetter(PlayerInformationNode::importance)
    ).apply(i, PlayerInformationNode::new));

    @Override
    public MapCodec<PlayerInformationNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_column";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Element result = view.element("div", "");
        for (Component line : this.lines(view.context()))
            result.appendChild(AssistantView.element(view.document(), "div", "card_body", LegacyText.formatted(line)));
        return result;
    }

    @Override
    public String text(CardRenderer.Text view) {
        return String.join(view.separator(), this.lines(view.context()).stream().map(LegacyText::formatted).toList());
    }

    private List<Component> lines(CardContext context) {
        List<?> source = context.get(PlayerInformationOutput.PARAMETER, List.class);
        List<Component> lines = source == null ? List.of() : source.stream()
                .filter(PlayerInformationOutput.class::isInstance).map(PlayerInformationOutput.class::cast)
                .filter(row -> row.importance() > this.importance)
                .flatMap(row -> row.lines().stream()).toList();
        return lines.isEmpty() ? List.of(Component.translatable("system.ui.player_information_empty")) : lines;
    }
}
