package com.iafenvoy.system.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Normalize FTB-style ampersand codes before vanilla rendering or plain-text narration.
 */
public final class LegacyText {
    private LegacyText() {
    }

    public static String formatted(Component component) {
        StringBuilder result = new StringBuilder();
        component.visit((style, text) -> {
            result.append(ChatFormatting.RESET);
            if (style.getColor() != null) {
                String hex = String.format("%06x", style.getColor().getValue() & 0xffffff);
                result.append("§x");
                for (char digit : hex.toCharArray()) result.append('§').append(digit);
            }
            if (style.isBold()) result.append(ChatFormatting.BOLD);
            if (style.isItalic()) result.append(ChatFormatting.ITALIC);
            if (style.isUnderlined()) result.append(ChatFormatting.UNDERLINE);
            if (style.isStrikethrough()) result.append(ChatFormatting.STRIKETHROUGH);
            if (style.isObfuscated()) result.append(ChatFormatting.OBFUSCATED);
            result.append(text).append(ChatFormatting.RESET);
            return Optional.empty();
        }, Style.EMPTY);
        return result.toString();
    }

    public static String normalize(String text) {
        if (text.indexOf('&') < 0) return text;
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            // Escaped ampersands remain literal; ordinary ampersands and unknown codes are untouched.
            if (current == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '&') {
                result.append('&');
                i++;
            } else if (current == '&' && i + 1 < text.length() && ChatFormatting.getByCode(text.charAt(i + 1)) != null) {
                result.append(ChatFormatting.PREFIX_CODE);
            } else result.append(current);
        }
        return result.toString();
    }

    public static void runs(String value, Style base,
                            BiConsumer<String, Style> sink) {
        String normalized = normalize(value);
        Style style = base;
        StringBuilder run = new StringBuilder();
        for (int index = 0; index < normalized.length(); ) {
            char ch = normalized.charAt(index);
            if (ch == '\n') {
                if (!run.isEmpty()) sink.accept(run.toString(), style);
                run.setLength(0);
                sink.accept("\n", base);
                style = base;
                index++;
                continue;
            }
            if (ch == '§' && index + 1 < normalized.length()) {
                char code = Character.toLowerCase(normalized.charAt(index + 1));
                ChatFormatting format = ChatFormatting.getByCode(code);
                Integer rgb = null;
                int consumed = 2;
                if (code == 'x' && index + 14 <= normalized.length()) {
                    StringBuilder hex = new StringBuilder();
                    for (int digit = index + 2; digit < index + 14 && normalized.charAt(digit) == '§'; digit += 2)
                        hex.append(normalized.charAt(digit + 1));
                    if (hex.length() == 6) try {
                        rgb = Integer.parseInt(hex.toString(), 16);
                        consumed = 14;
                    } catch (NumberFormatException ignored) { /* Preserve invalid source codes as text. */ }
                }
                if (rgb != null || format != null) {
                    if (!run.isEmpty()) sink.accept(run.toString(), style);
                    run.setLength(0);
                    style = rgb != null ? base.withColor(rgb) : format == ChatFormatting.RESET ? base : style.applyLegacyFormat(format);
                    index += consumed;
                    continue;
                }
            }
            int point = normalized.codePointAt(index);
            run.appendCodePoint(point);
            index += Character.charCount(point);
        }
        if (!run.isEmpty()) sink.accept(run.toString(), style);
    }

    public static Component snapshot(Component source) {
        return transform(source, false);
    }

    public static Component component(Component source) {
        return transform(source, true);
    }

    private static Component transform(Component source, boolean parseLegacy) {
        ComponentContents contents = source.getContents();
        MutableComponent result;
        if (parseLegacy && contents instanceof PlainTextContents literal) {
            result = Component.empty().setStyle(source.getStyle());
            runs(literal.text(), source.getStyle(), (run, style) -> result.append(Component.literal(run).setStyle(style)));
        } else {
            if (contents instanceof TranslatableContents translation) {
                Object[] arguments = translation.getArgs().clone();
                for (int i = 0; i < arguments.length; i++) {
                    if (arguments[i] instanceof Component argument) arguments[i] = transform(argument, parseLegacy);
                    else if (parseLegacy && arguments[i] instanceof String text && normalize(text).indexOf('§') >= 0)
                        arguments[i] = transform(Component.literal(text), true);
                }
                contents = new TranslatableContents(translation.getKey(), translation.getFallback(), arguments);
            }
            result = MutableComponent.create(contents).setStyle(source.getStyle());
        }
        for (Component sibling : source.getSiblings()) result.append(transform(sibling, parseLegacy));
        return result;
    }

    public static String plain(String text) {
        StringBuilder result = new StringBuilder();
        runs(text, Style.EMPTY, (run, unused1) -> result.append(run));
        return result.toString();
    }

    public static String scoped(String text) {
        // A formatted source field must not style the separators or fields appended by the UI.
        // Keep the source unchanged so escaped ampersands are normalized only once at the rendering boundary.
        return normalize(text).indexOf(ChatFormatting.PREFIX_CODE) < 0 ? text : text + ChatFormatting.RESET;
    }
}
