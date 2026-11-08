package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.screen.card.node.interaction.ButtonNode;
import com.iafenvoy.system.screen.card.node.text.TextContent;
import com.iafenvoy.system.screen.card.node.text.TextNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * The common envelope owns conditions and repeats; dispatched content owns its layout fields.
 */
public record CardTemplate(String css, boolean showTitle, boolean hideSummary, List<Node> body, Actions actions) {
    public static final Codec<List<Node>> NODES = Codec.lazyInitialized(Node.CODEC::listOf);
    public static final Codec<CardTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            CardNode.STYLE_CODEC.optionalFieldOf("style", "result_card").forGetter(CardTemplate::css),
            Codec.BOOL.optionalFieldOf("show_title", true).forGetter(CardTemplate::showTitle),
            Codec.BOOL.optionalFieldOf("hide_summary", false).forGetter(CardTemplate::hideSummary),
            Node.CODEC.listOf().fieldOf("body").forGetter(CardTemplate::body),
            Actions.CODEC.optionalFieldOf("actions", Actions.DEFAULT).forGetter(CardTemplate::actions)
    ).apply(i, CardTemplate::new));
    private static final CardTemplate FALLBACK = new CardTemplate("result_card", true, false, List.of(
            Node.of(new TextNode(TextContent.of("{{summary}}"))),
            ButtonNode.actions()
    ), Actions.DEFAULT);

    public CardTemplate {
        body = List.copyOf(body);
    }

    public static CardTemplate fallback() {
        return FALLBACK;
    }

    public boolean textOnly() {
        return this.body.size() == 1 && this.body.getFirst().content() instanceof PlainMessageNode && this.body.getFirst().repeat().isEmpty();
    }

    public String label(CardContext context, int index) {
        String label = context.get("actions." + index + ".label", String.class);
        if (this.actions.label().isEmpty()) return label == null ? "" : label;
        return CardRenderer.text(this.actions.label(), this, context, List.of(index), "");
    }

    public record Node(CardNode content, Optional<String> css, String when, String unless, String repeat, String id) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                CardNode.CODEC.forGetter(Node::content),
                CardNode.STYLE_CODEC.optionalFieldOf("style").forGetter(Node::css),
                Codec.STRING.optionalFieldOf("when", "").forGetter(Node::when),
                Codec.STRING.optionalFieldOf("unless", "").forGetter(Node::unless),
                Codec.STRING.lenientOptionalFieldOf("repeat", "").forGetter(Node::repeat),
                Codec.STRING.lenientOptionalFieldOf("id", "").forGetter(Node::id)
        ).apply(i, Node::new));

        public static Node of(CardNode content) {
            return new Node(content, Optional.empty(), "", "", "", "");
        }
    }

    /**
     * Shared action presentation for full/simple screens; HUD controls remain code-owned.
     */
    public record Actions(boolean numbered, List<Node> label) {
        public static final Actions DEFAULT = new Actions(true, List.of());
        public static final Codec<Actions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("numbered", true).forGetter(Actions::numbered),
                Node.CODEC.listOf().optionalFieldOf("label", List.of()).forGetter(Actions::label)
        ).apply(i, Actions::new));

        public Actions {
            label = List.copyOf(label);
        }
    }
}
