package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.config.RecognitionSettings;
import com.iafenvoy.system.network.payload.RecognitionUsageReportC2SPayload;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Exactly one completion per attempted API call, including cancellation on the original connection. */
final class RecognitionUsageTracker {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final ClientPacketListener connection = this.minecraft.getConnection();
    private final UUID id = UUID.randomUUID();
    private final String provider, region;
    private final AtomicBoolean completed = new AtomicBoolean();
    private boolean started;

    RecognitionUsageTracker(RecognitionSettings settings) {
        this.provider = settings.provider();
        this.region = settings.region();
    }

    void begin() {
        this.started = true;
        this.send(false, false);
    }

    void complete(boolean successful) {
        if (this.started && this.completed.compareAndSet(false, true)) this.send(true, successful);
    }

    private void send(boolean complete, boolean successful) {
        RecognitionUsageReportC2SPayload report = new RecognitionUsageReportC2SPayload(this.id, complete, this.provider, this.region, successful);
        this.minecraft.execute(() -> {
            if (this.connection != null && this.minecraft.getConnection() == this.connection) this.connection.send(report);
        });
    }
}
