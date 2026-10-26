package com.iafenvoy.system.compat.voicechat;

import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.RecognitionCapture;
import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.voice.client.microphone.ALMicrophone;
import de.maxhenkel.voicechat.voice.client.microphone.JavaxMicrophone;
import de.maxhenkel.voicechat.voice.client.microphone.Microphone;
import de.maxhenkel.voicechat.voice.client.microphone.MicrophoneManager;

import java.util.concurrent.TimeUnit;

/**
 * Version-sensitive SVC internals stay here; a separate device never enters its Opus/send pipeline.
 */
public final class VoicechatRecognitionCapture implements RecognitionCapture {
    private static final int FRAME_SAMPLES = 320;
    private static volatile boolean recording;
    private static volatile long suppressUntil;
    private static volatile boolean registered;
    private Microphone microphone;
    private volatile boolean stopped;
    private long deadline;

    public VoicechatRecognitionCapture() {
    }

    public static void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, event -> {
            if (suppressVoiceChat()) event.cancel();
        });
        registered = true;
    }

    @Override
    public void open() throws SpeechException {
        if (!registered) throw new SpeechException("voicechat");
        // Use the selected device and backend, but capture natively at ASR's rate without resampling.
        recording = true;
        try {
            String device = VoicechatClient.CLIENT_CONFIG.microphone.get();
            if (!MicrophoneManager.useJavaImplementation()) {
                this.microphone = new ALMicrophone(16000, FRAME_SAMPLES, device);
                try {
                    this.microphone.open();
                    return;
                } catch (Exception | LinkageError error) {
                    this.microphone.close();
                }
            }
            this.microphone = new JavaxMicrophone(16000, FRAME_SAMPLES, device);
            this.microphone.open();
        } catch (Exception | LinkageError error) {
            throw new SpeechException("microphone");
        }
    }

    @Override
    public void start() throws SpeechException {
        if (this.stopped || this.microphone == null) throw new SpeechException("cancelled");
        this.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        this.microphone.start();
    }

    @Override
    public int read(byte[] buffer) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (this.stopped || System.nanoTime() >= this.deadline) return -1;
        if (buffer.length < FRAME_SAMPLES * 2) throw new SpeechException("audio");
        if (this.microphone.available() < FRAME_SAMPLES) {
            TimeUnit.MILLISECONDS.sleep(5);
            return 0;
        }
        short[] samples = this.microphone.read();
        if (samples.length != FRAME_SAMPLES) throw new SpeechException("audio");
        for (int i = 0; i < samples.length; i++) {
            buffer[i * 2] = (byte) samples[i];
            buffer[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        return samples.length * 2;
    }

    @Override
    public void finish() {
        this.stopped = true;
    }

    @Override
    public void close() {
        try {
            if (this.microphone != null) this.microphone.close();
        } finally {
            this.microphone = null;
            // Let the voice-chat capture thread drain its last frames before broadcasting again.
            suppressUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
            recording = false;
        }
    }

    private static boolean suppressVoiceChat() {
        return recording || System.nanoTime() < suppressUntil;
    }
}
