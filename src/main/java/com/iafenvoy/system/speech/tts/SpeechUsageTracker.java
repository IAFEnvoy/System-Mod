package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.config.SpeechSettings;
import com.iafenvoy.system.network.payload.SpeechUsageReportC2SPayload;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Service accounting outlives playback cancellation, but never crosses the original server connection. */
final class SpeechUsageTracker {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final ClientPacketListener connection = this.minecraft.getConnection();
    private final UUID id = UUID.randomUUID();
    private final String provider, model, region;
    private final AtomicBoolean completed = new AtomicBoolean();
    private boolean started;

    SpeechUsageTracker(SpeechSettings settings) {
        this.provider = settings.provider();
        this.model = settings.model();
        this.region = settings.region();
    }

    void begin() {
        this.started = true;
        this.send(false, TtsProvider.Usage.UNKNOWN, false);
    }

    void complete(TtsProvider.Usage usage, boolean successful) {
        if (this.started && this.completed.compareAndSet(false, true)) this.send(true, usage, successful);
    }

    private void send(boolean complete, TtsProvider.Usage usage, boolean successful) {
        SpeechUsageReportC2SPayload report = new SpeechUsageReportC2SPayload(this.id, complete, this.provider, this.model, this.region, usage, successful);
        this.minecraft.execute(() -> {
            if (this.connection != null && this.minecraft.getConnection() == this.connection) this.connection.send(report);
        });
    }
}
