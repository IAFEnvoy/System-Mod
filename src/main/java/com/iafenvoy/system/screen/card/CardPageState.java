package com.iafenvoy.system.screen.card;

import java.util.HashMap;
import java.util.Map;

/**
 * Local page positions belong to one displayed notification, including its retained back-navigation view.
 */
public final class CardPageState {
    private final Map<String, Integer> pages = new HashMap<>();

    public int page(String key) {
        return this.pages.getOrDefault(key, 0);
    }

    public void page(String key, int page) {
        this.pages.put(key, page);
    }
}
