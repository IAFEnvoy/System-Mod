package com.iafenvoy.system.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Wrap bounded mono, signed 16-bit little-endian PCM without codecs, files or native dependencies. */
public final class PcmWav {
    private PcmWav() {
    }

    public static byte[] encode(byte[] pcm, int sampleRate) throws SpeechException {
        if (pcm.length == 0 || (pcm.length & 1) != 0 || sampleRate <= 0) throw new SpeechException("audio");
        return ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm).array();
    }
}
