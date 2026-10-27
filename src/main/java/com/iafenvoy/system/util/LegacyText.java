package com.iafenvoy.system.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringDecomposer;

/**
 * Normalize FTB-style ampersand codes before vanilla rendering or plain-text narration.
 */
public final class LegacyText {
    private LegacyText() {
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

    public static String plain(String text) {
        return StringDecomposer.getPlainText(Component.literal(normalize(text)));
    }

    public static String scoped(String text) {
        // A formatted source field must not style the separators or fields appended by the UI.
        // Keep the source unchanged so escaped ampersands are normalized only once at the rendering boundary.
        return normalize(text).indexOf(ChatFormatting.PREFIX_CODE) < 0 ? text : text + ChatFormatting.RESET;
    }
}
