package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.TtsProviders;
import com.iafenvoy.system.util.LegacyText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI-owned speech sessions replace synthesis and playback together; stale results never start a sound.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantSpeech {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), runnable -> {
        Thread thread = new Thread(runnable, "system-speech");
        thread.setDaemon(true);
        return thread;
    });
    private static Utterance queued;
    private static long generation;
    private static Session owner;
    private static Future<?> pending;
    private static SpeechAudio audio;
    private static SpeechPlayback playback;
    private static NotificationSound notification;
    private static boolean previewing;
    private static SpeechState status = SpeechState.IDLE;
    private static long cooldownUntil;
    private static long deadline;

    private record Utterance(Session source, String text, boolean preview, boolean notification) {
    }

    // A closed UI session cannot enqueue again, including after a delayed streaming callback.
    public static final class Session implements AutoCloseable {
        private boolean closed;

        public void result(AgentResult result) {
            stop(this);
            String text = SpeechText.result(result);
            if (!text.isBlank()) this.say(text);
        }

        public void say(String text) {
            if (!this.closed) AssistantSpeech.say(this, text, false);
        }

        public void notification(String text) {
            if (!this.closed) AssistantSpeech.say(this, text, true);
        }

        public void preview(SpeechConfig current) throws SpeechException {
            if (this.closed) return;
            validate(current);
            stop();
            start(new Utterance(this, AssistantView.tr("system.tts.preview_text"), true, false), current);
        }

        @Override
        public void close() {
            this.closed = true;
            stop(this);
        }
    }

    static {
        WORKER.allowCoreThreadTimeOut(true);
    }

    private AssistantSpeech() {
    }

    public static void configure(SpeechConfig next) throws Exception {
        if (!SpeechConfig.LANGUAGES.contains(next.language()) || next.volume() < 0 || next.volume() > 100)
            throw new SpeechException("settings");
        // Credentials and model fields can be edited independently; actual synthesis validates the complete request.
        SpeechConfig.INSTANCE.set(next);
        stop();
        cooldownUntil = 0;
        if (!SpeechConfig.INSTANCE.save()) throw new SpeechException("config");
        status = SpeechState.SAVED;
    }

    public static void validate(SpeechConfig next) throws SpeechException {
        if (!SpeechConfig.LANGUAGES.contains(next.language()) || next.volume() < 0 || next.volume() > 100)
            throw new SpeechException("settings");
        try (TtsProvider provider = TtsProviders.create(next.provider())) {
            provider.validate(next.request("Preview", Minecraft.getInstance().options.languageCode));
        }
    }

    private static boolean canSpeak() {
        return SpeechConfig.INSTANCE.get().enabled() && SpeechConfig.INSTANCE.get().hasKey() && System.nanoTime() >= cooldownUntil;
    }

    private static void say(Session source, String text, boolean notification) {
        // The UI selects the current message; speech must never hold the next message behind old audio.
        stop();
        if (!notification && !canSpeak()) return;
        queued = new Utterance(source, text, false, notification);
        status = SpeechState.QUEUED;
    }

    private static void start(Utterance utterance, SpeechConfig current) {
        Session source = utterance.source();
        if (source.closed) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || minecraft.options.getSoundSourceVolume(SoundSource.VOICE) <= 0 || current.volume() == 0) {
            status = SpeechState.MUTED;
            return;
        }
        String normalized = normalize(utterance.text());
        if (normalized.isEmpty() && !utterance.notification()) return;
        long epoch = generation;
        owner = source;
        previewing = utterance.preview();
        status = utterance.notification() ? SpeechState.STARTING : SpeechState.LOADING;
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        if (utterance.notification() && current.notificationSoundEnabled()) {
            NotificationSound cue = new NotificationSound(current.volume());
            notification = cue;
            try {
                // 1.21.1 reports no play result, so a source that never becomes active is treated as failed.
                minecraft.getSoundManager().play(cue);
                if (!minecraft.getSoundManager().isActive(cue)) {
                    cue.close();
                    notification = null;
                }
            } catch (RuntimeException error) {
                cue.close();
                notification = null;
                SystemMod.LOGGER.warn("Unable to play notification cue ({})", error.getClass().getSimpleName());
            }
        }
        // Local cues also work with automatic speech disabled, missing credentials or a cloud cooldown.
        if (normalized.isEmpty() || !utterance.preview() && !canSpeak()) return;
        String language = minecraft.options.languageCode;
        String providerId = current.provider();
        int volume = current.volume();
        boolean cacheEnabled = current.cacheEnabled();
        SpeechUsageTracker accounting = new SpeechUsageTracker(providerId, current.model(), current.region());
        SpeechAudio sourceAudio = new SpeechAudio();
        audio = sourceAudio;
        AtomicBoolean playbackQueued = new AtomicBoolean();
        Runnable startPlayback = () -> {
            if (!sourceAudio.ready() || !playbackQueued.compareAndSet(false, true)) return;
            minecraft.execute(() -> {
                if (generation != epoch || owner != source || source.closed) return;
                if (minecraft.player == null || minecraft.level == null) {
                    stop();
                    return;
                }
                playback = new SpeechPlayback(new SpeechSound(sourceAudio, volume), volume);
                status = SpeechState.STARTING;
            });
        };
        WORKER.purge();
        pending = WORKER.submit(() -> {
            try (TtsProvider provider = TtsProviders.create(providerId)) {
                // Cards already carry server-prepared narration; synthesis starts without another quest request.
                TtsProvider.Request request = current.request(normalized, language);
                provider.validate(request);
                if (Thread.currentThread().isInterrupted()) return;
                String cacheKey = cacheEnabled ? SpeechAudioCache.key(providerId, request) : null;
                if (cacheKey != null) {
                    SpeechAudio.Snapshot cached = SpeechAudioCache.read(cacheKey);
                    if (Thread.currentThread().isInterrupted()) return;
                    if (cached != null) {
                        sourceAudio.append(cached.sampleRate(), cached.channels(), cached.pcm());
                        sourceAudio.finish();
                        startPlayback.run();
                        return;
                    }
                }
                // Cache playback is local: only an actual cloud request starts usage accounting.
                accounting.begin();
                if (provider.supportsStreaming()) {
                    AtomicReference<TtsProvider.Usage> usage = new AtomicReference<>(TtsProvider.Usage.UNKNOWN);
                    boolean successful = false;
                    try {
                        provider.synthesizeStreaming(request, (sampleRate, channels, pcm) -> {
                            sourceAudio.append(sampleRate, channels, pcm);
                            startPlayback.run();
                        }, usage::set);
                        sourceAudio.finish();
                        successful = true;
                    } finally {
                        // SSE usage is cumulative: report the latest snapshot once, even after cancellation.
                        accounting.complete(usage.get(), successful);
                    }
                } else {
                    byte[] wav = provider.synthesize(request, usage -> accounting.complete(usage, true));
                    accounting.complete(TtsProvider.Usage.UNKNOWN, true);
                    sourceAudio.loadWav(wav);
                }
                startPlayback.run();
                if (cacheKey != null && !Thread.currentThread().isInterrupted()) {
                    SpeechAudio.Snapshot snapshot = sourceAudio.snapshot();
                    if (snapshot != null) SpeechAudioCache.write(cacheKey, snapshot);
                }
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) return;
                SpeechState failure = error instanceof SpeechException speech ? SpeechState.fromCode(speech.code())
                        : error instanceof TimeoutException || error instanceof HttpTimeoutException ? SpeechState.TIMEOUT : SpeechState.NETWORK;
                minecraft.execute(() -> {
                    if (generation != epoch || owner != source) return;
                    fail(failure);
                });
            } finally {
                accounting.complete(TtsProvider.Usage.UNKNOWN, false);
            }
        });
    }

    public static SpeechState status() {
        return status;
    }

    private static String normalize(String text) {
        String normalized = LegacyText.plain(text).replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ").strip();
        if (normalized.length() <= 500) return normalized;
        int end = Character.isHighSurrogate(normalized.charAt(499)) ? 499 : 500;
        return normalized.substring(0, end) + "…";
    }

    private static void stop(Session source) {
        if (queued != null && queued.source() == source) queued = null;
        if (owner == source) cancelCurrent();
        if (owner == null) status = queued == null ? SpeechState.IDLE : SpeechState.QUEUED;
    }

    public static void stop() {
        queued = null;
        cancelCurrent();
        status = SpeechState.IDLE;
    }

    private static void cancelCurrent() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
        if (audio != null) audio.cancel();
        audio = null;
        WORKER.purge();
        if (notification != null) notification.close();
        notification = null;
        if (playback != null) {
            try {
                playback.close();
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Unable to release speech playback ({})", error.getClass().getSimpleName());
            }
        }
        playback = null;
        owner = null;
    }

    private static void fail(SpeechState failure) {
        boolean notify = !previewing;
        stop();
        status = failure;
        cooldownUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        if (notify && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("system.tts.failed", Component.translatable(failure.translationKey())));
    }

    // Called before SoundEngine tears down its context, including resource/device reload and /stopsound.
    public static void audioReset() {
        stop();
    }

    @SubscribeEvent
    public static void streamingStarted(PlayStreamingSourceEvent event) {
        if (event.getSound() instanceof SpeechSound speech) speech.nativeSourceStarted(event.getChannel());
        if (event.getSound() instanceof NotificationSound cue) cue.nativeSourceStarted(event.getChannel());
    }

    @SubscribeEvent
    public static void soundStarted(PlaySoundSourceEvent event) {
        if (event.getSound() instanceof NotificationSound cue) cue.nativeSourceStarted(event.getChannel());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (owner != null || queued != null) stop();
            return;
        }
        if (owner != null && System.nanoTime() >= deadline) {
            fail(SpeechState.TIMEOUT);
            return;
        }
        if (notification != null && !minecraft.getSoundManager().isActive(notification)) {
            notification.close();
            notification = null;
        }
        // Synthesis runs while the cue plays; buffered speech starts only after the actual sound ends.
        if (playback != null && notification == null) {
            SpeechState state = playback.tick();
            switch (state) {
                case FAILED -> fail(state);
                case FINISHED, MUTED -> {
                    cancelCurrent();
                    status = state == SpeechState.MUTED ? SpeechState.MUTED : SpeechState.IDLE;
                }
                default -> status = state;
            }
        }
        if (owner != null && audio == null && notification == null) {
            cancelCurrent();
            status = SpeechState.IDLE;
        }
        if (owner == null && queued != null && !minecraft.isPaused()) {
            Utterance next = queued;
            queued = null;
            start(next, SpeechConfig.INSTANCE.get());
        }
    }
}
