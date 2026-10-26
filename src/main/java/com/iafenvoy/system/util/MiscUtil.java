package com.iafenvoy.system.util;

public final class MiscUtil {
    public static String clip(String text, int length) {
        return text.length() > length ? text.substring(0, length) : text;
    }
}
