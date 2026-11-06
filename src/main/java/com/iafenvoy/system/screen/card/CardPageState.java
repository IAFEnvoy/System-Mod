package com.iafenvoy.system.screen.card;

import java.util.HashMap;
import java.util.Map;

/**
 * Local pages and selected groups belong to one card, including its retained back-navigation view.
 */
public final class CardPageState {
    private final Map<String, Integer> pages = new HashMap<>();
    private final Map<String, String> selections = new HashMap<>();

    public int page(String key) {
        return this.pages.getOrDefault(key, 0);
    }

    public void page(String key, int page) {
        this.pages.put(key, page);
    }

    public String selection(String key) {
        return this.selections.getOrDefault(key, "");
    }

    public void selection(String key, String selected) {
        this.selections.put(key, selected);
    }
}
