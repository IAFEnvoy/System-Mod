package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.util.LegacyText;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;

/**
 * Translate vanilla and FTB ampersand formatting into trusted AUI nodes, never dynamic HTML.
 */
public final class AssistantText {
    private AssistantText() {
    }

    public static void set(Element element, String value) {
        if (element == null) return;
        if (value != null) value = LegacyText.normalize(value);
        if (value == null || value.indexOf('§') < 0) {
            element.setTextContent(value);
            return;
        }
        List<Run> runs = new ArrayList<>();
        boolean firstLine = true;
        // Hard line breaks separate independent entries; soft wrapping still keeps the current style.
        for (String line : value.split("\n", -1)) {
            if (!firstLine) runs.add(new Run("\n", Style.EMPTY));
            firstLine = false;
            StringBuilder text = new StringBuilder();
            Style[] current = {Style.EMPTY};
            StringDecomposer.iterateFormatted(line, Style.EMPTY, (_, style, codePoint) -> {
                if (!current[0].equals(style)) {
                    if (!text.isEmpty()) runs.add(new Run(text.toString(), current[0]));
                    text.setLength(0);
                    current[0] = style;
                }
                text.appendCodePoint(codePoint);
                return true;
            });
            if (!text.isEmpty()) runs.add(new Run(text.toString(), current[0]));
        }

        element.setTextContent("");
        // Keep one flex item for buttons; spans inherit the page font, wrapping and reset colour.
        Element content = element.document.createElement("span");
        content.setClassName("minecraft_text");
        for (Run run : runs) {
            Element span = element.document.createElement("span");
            span.setTextContent(run.text());
            Style style = run.style();
            if (style.getColor() != null)
                span.setInlineStyleProperty("color", String.format("#%06x", style.getColor().getValue()));
            if (style.isBold()) span.setInlineStyleProperty("font-weight", "700");
            if (style.isItalic()) span.setInlineStyleProperty("font-style", "oblique");
            if (style.isUnderlined() || style.isStrikethrough())
                span.setInlineStyleProperty("text-decoration", style.isUnderlined()
                        ? (style.isStrikethrough() ? "underline line-through" : "underline") : "line-through");
            content.appendChild(span);
        }
        element.appendChild(content);
    }

    private record Run(String text, Style style) {
    }
}
