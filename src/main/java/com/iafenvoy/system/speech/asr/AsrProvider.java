package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.config.file.RecognitionConfig;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * Streaming or complete-upload recognition; microphone ownership and Minecraft UI stay with the caller.
 */
public interface AsrProvider extends AutoCloseable {
    String id();

    boolean configured();

    Request request(RecognitionConfig config);

    /**
     * Sockets and transports close without a checked failure; narrow the inherited signature for try-with-resources.
     */
    @Override
    void close();

    void validate(Request request) throws SpeechException;

    /**
     * Signal requestStarted just before submitting recognition, after any credential exchange and connection setup.
     */
    String recognize(Request request, AudioSource audio, Consumer<String> intermediate, Runnable requestStarted) throws Exception;

    /** Providers with duration-based billing report the response duration before returning the text. */
    default String recognize(Request request, AudioSource audio, Consumer<String> intermediate, Runnable requestStarted,
                             Consumer<Usage> observer) throws Exception {
        return this.recognize(request, audio, intermediate, requestStarted);
    }

    record Usage(boolean reported, long durationMillis) {
        public static final Usage UNKNOWN = new Usage(false, 0);

        public Usage {
            if (durationMillis < 0 || !reported && durationMillis != 0)
                throw new IllegalArgumentException("Invalid recognition duration");
        }
    }

    interface AudioSource {
        void start() throws Exception;

        /**
         * Return signed 16-bit little-endian mono PCM at 16 kHz, or -1 after recording stops.
         */
        int read(byte[] buffer) throws Exception;
    }

    record Request(String apiKey, String appKey, String accessKeyId, String accessKeySecret, String region) {
        @Override
        public @NonNull String toString() {
            return "AsrRequest[region=" + this.region + "]";
        }
    }
}
