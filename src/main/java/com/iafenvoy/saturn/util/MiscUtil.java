package com.iafenvoy.saturn.util;

public class MiscUtil {
    public static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    public static String clip(String text, int length) {
        return text.length() > length ? text.substring(0, length) : text;
    }
}
