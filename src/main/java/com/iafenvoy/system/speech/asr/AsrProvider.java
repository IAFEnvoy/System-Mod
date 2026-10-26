package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.speech.SpeechException;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * Streaming recognition protocol; microphone ownership and Minecraft UI stay with the caller.
 */
public interface AsrProvider extends AutoCloseable {
    void validate(Request request) throws SpeechException;

    String recognize(Request request, AudioSource audio, Consumer<String> intermediate) throws Exception;

    interface AudioSource {
        void start() throws Exception;

        /**
         * Return signed 16-bit little-endian mono PCM at 16 kHz, or -1 after recording stops.
         */
        int read(byte[] buffer) throws Exception;
    }

    record Request(String appKey, String token, String region) {
        @Override
        public @NonNull String toString() {
            return "AsrRequest[region=" + this.region + "]";
        }
    }

    @Override
    void close();
}
