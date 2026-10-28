package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.speech.LocalSpeechUsage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local accounting outlives playback cancellation and records each cloud request exactly once.
 */
final class SpeechUsageTracker {
    private final LocalSpeechUsage.Ledger ledger = LocalSpeechUsage.current();
    private final String provider, model, region;
    private final AtomicBoolean completed = new AtomicBoolean();
    private boolean started;

    SpeechUsageTracker(String provider, String model, String region) {
        this.provider = provider;
        this.model = model;
        this.region = region;
    }

    void begin() {
        if (this.started) return;
        this.started = true;
        this.ledger.beginSpeech();
    }

    void complete(TtsProvider.Usage usage, boolean successful) {
        if (this.started && this.completed.compareAndSet(false, true))
            this.ledger.completeSpeech(this.provider, this.model, this.region, usage, successful);
    }
}
