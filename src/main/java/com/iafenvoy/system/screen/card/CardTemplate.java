package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.screen.card.node.ActionsNode;
import com.iafenvoy.system.screen.card.node.ItemsNode;
import com.iafenvoy.system.screen.card.node.TextNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * The common envelope owns conditions and repeats; dispatched content owns its layout fields.
 */
public record CardTemplate(String css, boolean showTitle, List<Node> body, Actions actions) {
    public static final Codec<CardTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            CardNode.STYLE_CODEC.optionalFieldOf("style", "result_card").forGetter(CardTemplate::css),
            Codec.BOOL.optionalFieldOf("show_title", true).forGetter(CardTemplate::showTitle),
            Node.CODEC.listOf().fieldOf("body").forGetter(CardTemplate::body),
            Actions.CODEC.optionalFieldOf("actions", Actions.DEFAULT).forGetter(CardTemplate::actions)
    ).apply(i, CardTemplate::new));
    private static final CardTemplate FALLBACK = new CardTemplate("result_card", true, List.of(
            Node.of(new TextNode("{{fallback_text}}", "", List.of(), false)),
            Node.of(new ItemsNode("{{result_items}}", "", List.of(), Optional.empty(), true)),
            Node.of(new ActionsNode("{{result_actions}}"))
    ), Actions.DEFAULT);

    public CardTemplate {
        body = List.copyOf(body);
    }

    public static CardTemplate fallback() {
        return FALLBACK;
    }

    public String label(CardContext context, int index) {
        String label = context.get("actions." + index + ".label", String.class);
        if (this.actions.label().isEmpty()) return label == null ? "" : label;
        return CardRenderer.text(this.actions.label(), this, context, List.of(index), "");
    }

    public record Node(CardNode content, Optional<String> css, String when, String unless, String repeat) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                CardNode.CODEC.forGetter(Node::content),
                CardNode.STYLE_CODEC.optionalFieldOf("style").forGetter(Node::css),
                Codec.STRING.optionalFieldOf("when", "").forGetter(Node::when),
                Codec.STRING.optionalFieldOf("unless", "").forGetter(Node::unless),
                Codec.STRING.optionalFieldOf("repeat", "").forGetter(Node::repeat)
        ).apply(i, Node::new));

        public static Node of(CardNode content) {
            return new Node(content, Optional.empty(), "", "", "");
        }
    }

    /**
     * Shared action presentation for full/simple screens; HUD controls remain code-owned.
     */
    public record Actions(String containerCss, String buttonCss, boolean numbered, List<Node> label) {
        public static final Actions DEFAULT = new Actions("", "card_action", true, List.of());
        public static final Codec<Actions> CODEC = RecordCodecBuilder.create(i -> i.group(
                CardNode.STYLE_CODEC.optionalFieldOf("container_style", "").forGetter(Actions::containerCss),
                CardNode.STYLE_CODEC.optionalFieldOf("button_style", "card_action").forGetter(Actions::buttonCss),
                Codec.BOOL.optionalFieldOf("numbered", true).forGetter(Actions::numbered),
                Node.CODEC.listOf().optionalFieldOf("label", List.of()).forGetter(Actions::label)
        ).apply(i, Actions::new));

        public Actions {
            label = List.copyOf(label);
        }
    }
}
