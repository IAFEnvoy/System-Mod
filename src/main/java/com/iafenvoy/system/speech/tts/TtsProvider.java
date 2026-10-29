package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.speech.SpeechException;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Provider protocol only; credentials, requests and audio have no Minecraft dependency.
 */
public interface TtsProvider extends AutoCloseable {
    /**
     * Validate locally, without a network request or billable operation.
     */
    void validate(Request request) throws SpeechException;

    /**
     * Return a complete PCM 16-bit little-endian mono/stereo WAV, without starting playback.
     */
    byte[] synthesize(Request request) throws Exception;

    /**
     * Report API usage once before downloading audio; legacy providers leave usage unknown.
     */
    default byte[] synthesize(Request request, Consumer<Usage> observer) throws Exception {
        return this.synthesize(request);
    }

    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Stream signed 16-bit little-endian PCM; usage callbacks are cumulative snapshots, never deltas.
     */
    default void synthesizeStreaming(Request request, AudioSink audio, Consumer<Usage> observer) throws Exception {
        throw new UnsupportedOperationException("Streaming speech is not supported");
    }

    @FunctionalInterface
    interface AudioSink {
        void accept(int sampleRate, int channels, byte[] pcm) throws Exception;
    }

    enum Unit {UNKNOWN, CHARACTERS, TOKENS}

    /**
     * Only provider-reported billing units; character and token totals are never interchanged.
     */
    record Usage(Unit unit, boolean reported, long characters, long inputTokens, long outputTokens, long totalTokens) {
        public static final Usage UNKNOWN = new Usage(Unit.UNKNOWN, false, 0, 0, 0, 0);

        public Usage {
            if (unit == null || characters < 0 || inputTokens < 0 || outputTokens < 0 || totalTokens < inputTokens
                    || outputTokens > totalTokens - inputTokens || (!reported || unit == Unit.UNKNOWN)
                    && (characters != 0 || inputTokens != 0 || outputTokens != 0 || totalTokens != 0)
                    || reported && unit == Unit.UNKNOWN || !reported && unit != Unit.UNKNOWN || unit == Unit.CHARACTERS && totalTokens != 0
                    || unit == Unit.TOKENS && characters != 0)
                throw new IllegalArgumentException("Invalid speech usage");
        }
    }

    record Request(String text, String apiKey, String region, String workspace, String model, String voice,
                   String language) {
        @Override
        public @NotNull String toString() {
            return "TtsRequest[model=" + this.model + ", voice=" + this.voice + "]";
        }
    }

    @Override
    void close();
}
