package com.iafenvoy.system.screen.card;

import java.util.HashMap;
import java.util.Map;

/**
 * Pages, selections and numeric animations belong to one card, including its retained back-navigation view.
 */
public final class CardUiState {
    private final Map<String, Integer> pages = new HashMap<>();
    private final Map<String, String> selections = new HashMap<>();
    private final Map<String, Boolean> expanded = new HashMap<>();
    private final Map<String, Progress> progress = new HashMap<>();

    public Progress progress(String key, double value) {
        Progress progress = this.progress.computeIfAbsent(key, ignored -> new Progress());
        progress.target(value);
        return progress;
    }

    /**
     * Numeric animation state survives DOM replacements without retaining elements or documents.
     */
    public static final class Progress {
        private static final long DURATION = 350_000_000L;
        private double from;
        private double target;
        private long started;

        private void target(double value) {
            if (this.target == value) return;
            this.from = this.started == 0 ? this.from : this.value();
            this.target = value;
            this.started = 0;
        }

        public double value() {
            if (this.started == 0) this.started = System.nanoTime();
            double elapsed = Math.clamp((double) (System.nanoTime() - this.started) / DURATION, 0, 1);
            double eased = 1 - Math.pow(1 - elapsed, 3);
            return this.from + (this.target - this.from) * eased;
        }

        public boolean animating() {
            return this.from != this.target && (this.started == 0 || System.nanoTime() - this.started < DURATION);
        }
    }

    public boolean expanded(String key, boolean fallback) {
        return this.expanded.getOrDefault(key, fallback);
    }

    public void setExpanded(String key, boolean value) {
        this.expanded.put(key, value);
    }

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
