package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.card.CardContext;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traversal only carries loop indices; every node reads the same immutable CardContext.
 */
public final class CardRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z0-9_.*:-]+)}}");

    private CardRenderer() {
    }

    public record Render(Document document, String css, CardTemplate template, CardContext context,
                         List<Integer> indices) {
        public Element element(String tag, String value) {
            return AssistantView.element(this.document, tag, this.css, value);
        }

        public boolean interactive() {
            return !this.document.isDisposed() && (!enabled("notification", this.context, List.of())
                    || this.document.body.getClassNames().contains("interactive"));
        }
    }

    public record Text(CardTemplate template, CardContext context, List<Integer> indices, String separator) {
    }

    public static void render(Document document, Element parent, List<CardTemplate.Node> nodes, CardTemplate template,
                              CardContext context, List<Integer> indices) {
        for (CardTemplate.Node node : nodes)
            expand(node, context, indices, loop -> renderNode(document, parent, node, template, context, loop));
    }

    private static void renderNode(Document document, Element parent, CardTemplate.Node node, CardTemplate template,
                                   CardContext context, List<Integer> indices) {
        Element result = node.content().render(new Render(document, node.css().orElse(node.content().css()), template, context, indices));
        if (result != null) parent.appendChild(result);
    }

    public static String text(List<CardTemplate.Node> nodes, CardTemplate template, CardContext context, List<Integer> indices, String separator) {
        List<String> lines = new ArrayList<>();
        for (CardTemplate.Node node : nodes)
            expand(node, context, indices, loop -> {
                String value = node.content().text(new Text(template, context, loop, separator));
                if (!value.isBlank()) lines.add(value);
            });
        return String.join(separator, lines);
    }

    private static void expand(CardTemplate.Node node, CardContext context, List<Integer> indices, Consumer<List<Integer>> action) {
        if (node.repeat().isEmpty()) show(node, context, indices, action);
        else {
            int count = (int) context.states().getDouble(binding(node.repeat(), indices));
            for (int index = 0; index < count; index++) {
                List<Integer> loop = new ArrayList<>(indices);
                loop.add(index);
                show(node, context, List.copyOf(loop), action);
            }
        }
    }

    private static void show(CardTemplate.Node node, CardContext context, List<Integer> indices, Consumer<List<Integer>> action) {
        if (enabled(node.when(), context, indices) && (node.unless().isEmpty() || !enabled(node.unless(), context, indices)))
            action.accept(indices);
    }

    public static boolean enabled(String reference, CardContext context, List<Integer> indices) {
        return reference.isEmpty() || context.states().getDouble(binding(reference, indices)) != 0;
    }

    /**
     * Asterisks select loop indices in order; keys remain literal map keys, with no reflection or nested lookup.
     */
    public static String binding(String reference, List<Integer> indices) {
        if (reference.startsWith("{{") && reference.endsWith("}}"))
            reference = reference.substring(2, reference.length() - 2);
        for (int index : indices) {
            int offset = reference.indexOf('*');
            if (offset < 0) break;
            reference = reference.substring(0, offset) + index + reference.substring(offset + 1);
        }
        return reference;
    }

    public static String value(String source, CardContext context, List<Integer> indices) {
        return value(source, context, indices, true);
    }

    public static String rawValue(String source, CardContext context, List<Integer> indices) {
        // URLs and other literal attributes must not acquire Minecraft formatting reset codes.
        return value(source, context, indices, false);
    }

    private static String value(String source, CardContext context, List<Integer> indices, boolean formatted) {
        return PLACEHOLDER.matcher(source).replaceAll(match -> Matcher.quoteReplacement(placeholder(
                match.group(1), match.group(), context, indices, formatted)));
    }

    private static String placeholder(String name, String original, CardContext context, List<Integer> indices, boolean formatted) {
        if (name.startsWith("tr:")) return LegacyText.scoped(Component.translatable(name.substring(3)).getString());
        if (name.startsWith("field:")) return Component.translatable("system.field." + name.substring(6)).getString();
        if (name.startsWith("bool:"))
            return Component.translatable(enabled(name.substring(5), context, indices) ? "system.ui.yes" : "system.ui.no").getString();
        if (name.startsWith("state:")) {
            String state = context.get(binding(name.substring(6), indices), String.class);
            return state == null ? original : Component.translatable("system.state." + state.toLowerCase(Locale.ROOT)).getString();
        }
        Object value = context.get(binding(name, indices), Object.class);
        return value instanceof String || value instanceof Number || value instanceof Boolean
                ? formatted ? LegacyText.scoped(value.toString()) : value.toString() : original;
    }
}
