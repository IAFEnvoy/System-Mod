package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.speech.LocalSpeechUsage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exactly one local completion per attempted API call, including cancellation and disconnects.
 */
final class RecognitionUsageTracker {
    private final LocalSpeechUsage.Ledger ledger = LocalSpeechUsage.current();
    private final String provider, region;
    private final AtomicBoolean completed = new AtomicBoolean();
    private boolean started;
    private AsrProvider.Usage usage = AsrProvider.Usage.UNKNOWN;

    RecognitionUsageTracker(String provider, String region) {
        this.provider = provider;
        this.region = region;
    }

    void begin() {
        if (this.started) return;
        this.started = true;
        this.ledger.beginRecognition();
    }

    void complete(boolean successful) {
        if (this.started && this.completed.compareAndSet(false, true))
            this.ledger.completeRecognition(this.provider, this.region, this.usage, successful);
    }

    void usage(AsrProvider.Usage usage) {
        this.usage = usage;
    }
}
