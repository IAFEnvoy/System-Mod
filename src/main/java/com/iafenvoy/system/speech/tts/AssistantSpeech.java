package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.config.SpeechSettings;
import com.iafenvoy.system.speech.TtsProviders;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.speech.SpeechException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;

/** UI-owned speech sessions replace synthesis and playback together; stale results never start a sound. */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantSpeech {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
        Thread thread = new Thread(runnable, "system-speech");
        thread.setDaemon(true);
        return thread;
    });
    private static Utterance queued;
    private static SpeechSettings settings;
    private static long generation;
    private static Session owner;
    private static Future<?> pending;
    private static SpeechAudio audio;
    private static SpeechPlayback playback;
    private static boolean previewing;
    private static String status = "idle";
    private static long cooldownUntil;
    private static long deadline;

    private record Utterance(Session source, String text, SpeechSettings settings, boolean preview) {
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
            if (!this.closed) AssistantSpeech.say(this, text);
        }

        public void preview(SpeechSettings current) throws SpeechException {
            if (this.closed) return;
            validate(current);
            stop();
            start(new Utterance(this, AssistantView.tr("system.tts.preview_text"), current, true));
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

    public static SpeechSettings settings() {
        if (settings == null) {
            try {
                settings = SpeechSettings.load();
            } catch (Exception e) {
                settings = SpeechSettings.defaults();
                status = "config";
                SystemMod.LOGGER.warn("Unable to load local speech configuration");
            }
        }
        return settings;
    }

    public static void configure(SpeechSettings next) throws Exception {
        if (!SpeechSettings.LANGUAGES.contains(next.language()) || next.volume() < 0 || next.volume() > 100)
            throw new SpeechException("settings");
        if (next.enabled()) validate(next);
        next.save();
        stop();
        settings = next;
        cooldownUntil = 0;
        status = "saved";
    }

    public static void validate(SpeechSettings next) throws SpeechException {
        if (!SpeechSettings.LANGUAGES.contains(next.language()) || next.volume() < 0 || next.volume() > 100)
            throw new SpeechException("settings");
        try (TtsProvider provider = TtsProviders.create(next.provider())) {
            provider.validate(next.request("Preview", Minecraft.getInstance().options.languageCode));
        }
    }

    private static void say(Session source, String text) {
        // The UI selects the current message; speech must never hold the next message behind old audio.
        stop();
        SpeechSettings current = settings();
        if (!current.enabled() || !current.hasKey() || System.nanoTime() < cooldownUntil) return;
        queued = new Utterance(source, text, current, false);
        status = "queued";
    }

    private static void start(Utterance utterance) {
        Session source = utterance.source();
        if (source.closed) return;
        SpeechSettings current = utterance.settings();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || minecraft.options.getSoundSourceVolume(SoundSource.VOICE) <= 0 || current.volume() == 0) {
            status = "muted";
            return;
        }
        String normalized = utterance.text().replaceAll("§.", "").replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ").strip();
        if (normalized.isEmpty()) return;
        if (normalized.length() > 500) {
            int end = Character.isHighSurrogate(normalized.charAt(499)) ? 499 : 500;
            normalized = normalized.substring(0, end) + "…";
        }
        TtsProvider.Request request = current.request(normalized, minecraft.options.languageCode);
        long epoch = generation;
        owner = source;
        previewing = utterance.preview();
        status = "loading";
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        SpeechUsageTracker accounting = new SpeechUsageTracker(current);
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
                playback = new SpeechPlayback(new SpeechSound(sourceAudio, current.volume()), current.volume());
                status = "starting";
            });
        };
        WORKER.purge();
        pending = WORKER.submit(() -> {
            try (TtsProvider provider = TtsProviders.create(current.provider())) {
                provider.validate(request);
                if (Thread.currentThread().isInterrupted()) return;
                String cacheKey = current.cacheEnabled() ? SpeechAudioCache.key(current.provider(), request) : null;
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
                String code = error instanceof SpeechException speech ? speech.code()
                        : error instanceof TimeoutException || error instanceof HttpTimeoutException ? "timeout" : "network";
                minecraft.execute(() -> {
                    if (generation != epoch || owner != source) return;
                    fail(code);
                });
            } finally {
                accounting.complete(TtsProvider.Usage.UNKNOWN, false);
            }
        });
    }

    public static String status() {
        settings();
        return status;
    }

    private static void stop(Session source) {
        if (queued != null && queued.source() == source) queued = null;
        if (owner == source) cancelCurrent();
        if (owner == null) status = queued == null ? "idle" : "queued";
    }

    public static void stop() {
        queued = null;
        cancelCurrent();
        status = "idle";
    }

    private static void cancelCurrent() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
        if (audio != null) audio.cancel();
        audio = null;
        WORKER.purge();
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

    private static void fail(String code) {
        boolean notify = !previewing;
        stop();
        status = code;
        cooldownUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        if (notify && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("system.tts.failed", Component.translatable("system.tts.status." + code)));
    }

    // Called before SoundEngine tears down its context, including resource/device reload and /stopsound.
    public static void audioReset() {
        stop();
    }

    @SubscribeEvent
    public static void streamingStarted(PlayStreamingSourceEvent event) {
        if (event.getSound() instanceof SpeechSound speech) speech.nativeSourceStarted(event.getChannel());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (owner != null || queued != null) stop();
            return;
        }
        if (owner != null && System.nanoTime() >= deadline) {
            fail("timeout");
            return;
        }
        if (playback != null) {
            SpeechPlayback.State state = playback.tick();
            switch (state) {
                case STARTING -> status = "starting";
                case NATIVE -> status = "playing";
                case OPENAL -> status = "playing_openal";
                case FAILED -> fail("playback");
                case FINISHED, MUTED -> {
                    cancelCurrent();
                    status = state == SpeechPlayback.State.MUTED ? "muted" : "idle";
                }
            }
        }
        if (owner == null && queued != null && !minecraft.isPaused()) {
            Utterance next = queued;
            queued = null;
            start(next);
        }
    }
}
