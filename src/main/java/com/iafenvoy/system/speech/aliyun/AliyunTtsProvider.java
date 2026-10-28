package com.iafenvoy.system.speech.aliyun;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.system.data.speech.AliyunTtsMessage;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.HttpTransport;
import com.iafenvoy.system.util.ServerSentEvents;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * DashScope WAV and SSE/PCM adapters, without an SDK, native runtime or distributed model.
 */
public final class AliyunTtsProvider implements TtsProvider {
    public static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    public static final Set<String> LANGUAGES = Set.of("Auto", "Chinese", "English", "German", "Italian", "Portuguese", "Spanish", "Japanese", "Korean", "French", "Russian");
    public static final Set<String> MODELS = Set.of("qwen-audio-3.1-tts-flash", "qwen-audio-3.1-tts-plus", "qwen-audio-3.0-tts-flash", "qwen-audio-3.0-tts-plus");
    private final HttpTransport transport = new HttpTransport();

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public void synthesizeStreaming(Request request, AudioSink audio, Consumer<Usage> observer) throws Exception {
        this.validate(request);
        boolean custom = request.model().startsWith("qwen-audio-");
        String body = CodecIO.write(AliyunTtsMessage.Request.CODEC, AliyunTtsMessage.Request.of(request, "pcm"));
        String path = custom ? "/api/v1/services/audio/tts/SpeechSynthesizer"
                : "/api/v1/services/aigc/multimodal-generation/generation";
        StreamResult result = new StreamResult(request.model(), audio, observer);
        ServerSentEvents events = new ServerSentEvents(2 * 1024 * 1024, result::event);
        HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl(request) + path)).timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + request.apiKey()).header("Content-Type", "application/json")
                .header("X-DashScope-SSE", "enable").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<Void> response = this.transport.stream(http, 16 * 1024 * 1024, (info, bytes) -> {
                checkStatus(info.statusCode());
                if (!info.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("text/event-stream"))
                    throw new SpeechException("response");
                events.accept(bytes);
            });
            checkStatus(response.statusCode());
            events.finish();
            if (!result.finished || result.audioBytes == 0) throw new SpeechException("response");
        } catch (IOException error) {
            if (error.getCause() instanceof SpeechException speech) throw speech;
            throw error;
        }
    }

    private static final class StreamResult {
        private final String model;
        private final AudioSink audio;
        private final Consumer<Usage> observer;
        private int audioBytes;
        private boolean finished;

        private StreamResult(String model, AudioSink audio, Consumer<Usage> observer) {
            this.model = model;
            this.audio = audio;
            this.observer = observer;
        }

        private void event(String event, String data) throws Exception {
            if (data.equals("[DONE]")) return;
            JsonElement json = JsonParser.parseString(data);
            Usage usage = readUsage(json, this.model);
            if (usage.reported()) this.observer.accept(usage);
            AliyunTtsMessage.Response response = AliyunTtsMessage.Response.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            if (event.equals("error") || !response.code().isEmpty()) throw new SpeechException("response");
            if (response.statusCode().isPresent()) checkStatus(response.statusCode().get());
            AliyunTtsMessage.Output output = response.output().orElseThrow(() -> new SpeechException("response"));
            if (output.audio().isPresent()) {
                String encoded = output.audio().get().data();
                if (!encoded.isEmpty()) {
                    if (this.finished || encoded.length() > (MAX_AUDIO_BYTES - this.audioBytes + 2L) / 3 * 4)
                        throw new SpeechException("audio");
                    byte[] pcm;
                    try {
                        pcm = Base64.getDecoder().decode(encoded);
                    } catch (IllegalArgumentException error) {
                        throw new SpeechException("audio");
                    }
                    if (pcm.length > MAX_AUDIO_BYTES - this.audioBytes) throw new SpeechException("audio");
                    this.audioBytes += pcm.length;
                    this.audio.accept(24000, 1, pcm);
                }
            }
            // The final URL is deliberately unused: streamed PCM is already ready for playback.
            if (output.finishReason().equals("stop")) this.finished = true;
        }
    }

    @Override
    public byte[] synthesize(Request request) throws Exception {
        return this.synthesize(request, ignored -> {
        });
    }

    @Override
    public byte[] synthesize(Request request, Consumer<Usage> observer) throws Exception {
        this.validate(request);
        boolean custom = request.model().startsWith("qwen-audio-");
        String body = CodecIO.write(AliyunTtsMessage.Request.CODEC, AliyunTtsMessage.Request.of(request, "wav"));
        String path = custom ? "/api/v1/services/audio/tts/SpeechSynthesizer" : "/api/v1/services/aigc/multimodal-generation/generation";
        HttpResponse<byte[]> response = this.transport.postJson(URI.create(baseUrl(request) + path), body, Map.of("Authorization", "Bearer " + request.apiKey()), Duration.ofSeconds(45), 12 * 1024 * 1024);
        checkStatus(response.statusCode());
        JsonElement json = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8));
        // Billing survives an expired audio URL, cancelled download or later WAV/playback failure.
        observer.accept(readUsage(json, request.model()));
        AliyunTtsMessage.Response result = AliyunTtsMessage.Response.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        if (!result.code().isEmpty()) throw new SpeechException("response");
        AliyunTtsMessage.Audio audio = result.output().flatMap(AliyunTtsMessage.Output::audio).orElseThrow(() -> new SpeechException("response"));
        String url = audio.url();
        if (!url.isBlank()) {
            // The API may return HTTP OSS links. Upgrade to TLS and never forward Authorization to OSS.
            URI source = audioUrl(url);
            HttpResponse<byte[]> download = this.transport.send(HttpRequest.newBuilder(source)
                    .timeout(Duration.ofSeconds(25)).GET().build(), MAX_AUDIO_BYTES);
            checkStatus(download.statusCode());
            return nonempty(download.body());
        }
        String data = audio.data();
        if (data.length() > (MAX_AUDIO_BYTES + 2) / 3 * 4) throw new SpeechException("audio");
        try {
            return nonempty(Base64.getDecoder().decode(data));
        } catch (IllegalArgumentException e) {
            throw new SpeechException("audio");
        }
    }

    private static Usage readUsage(JsonElement response, String model) {
        try {
            return AliyunTtsMessage.Billing.ENVELOPE.parse(JsonOps.INSTANCE, response).result().flatMap(Function.identity()).map(value -> value.counted(model)).orElse(Usage.UNKNOWN);
        } catch (RuntimeException invalidUsage) {
            return Usage.UNKNOWN;
        }
    }

    @Override
    public void validate(Request request) throws SpeechException {
        if (request.apiKey().isBlank()) throw new SpeechException("key");
        if (request.apiKey().length() > 512 || request.apiKey().chars().anyMatch(Character::isWhitespace))
            throw new SpeechException("key");
        if (!Set.of("beijing", "singapore").contains(request.region())) throw new SpeechException("settings");
        if (!request.workspace().isEmpty() && !request.workspace().matches("[A-Za-z0-9-]{1,80}"))
            throw new SpeechException("settings");
        boolean custom = MODELS.contains(request.model());
        if (!custom && !request.model().matches("qwen3-tts-flash(?:-[0-9-]+)?")) throw new SpeechException("model");
        if (!request.voice().matches("[A-Za-z0-9_.-]{1,200}")) throw new SpeechException("voice");
        if (!custom && (request.voice().contains("-vd-") || request.voice().contains("-vc-")))
            throw new SpeechException("voice_model");
        if (custom && request.voice().startsWith("qwen-") && !request.voice().startsWith(request.model() + "-"))
            throw new SpeechException("voice_model");
        if (!LANGUAGES.contains(request.language())) throw new SpeechException("settings");
        if (request.text().isBlank() || request.text().codePointCount(0, request.text().length()) > 600)
            throw new SpeechException("text");
    }

    private static String baseUrl(Request request) {
        if (!request.workspace().isEmpty()) return "https://" + request.workspace()
                + (request.region().equals("singapore") ? ".ap-southeast-1.maas.aliyuncs.com" : ".cn-beijing.maas.aliyuncs.com");
        return request.region().equals("singapore") ? "https://dashscope-intl.aliyuncs.com" : "https://dashscope.aliyuncs.com";
    }

    private static URI audioUrl(String url) throws SpeechException {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.endsWith(".aliyuncs.com") || !host.contains(".oss-") || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 || uri.getFragment() != null || !Set.of("http", "https").contains(uri.getScheme()))
                throw new SpeechException("audio");
            return URI.create("https" + url.substring(url.indexOf(':')));
        } catch (IllegalArgumentException e) {
            throw new SpeechException("audio");
        }
    }

    private static byte[] nonempty(byte[] data) throws SpeechException {
        if (data.length == 0 || data.length > MAX_AUDIO_BYTES) throw new SpeechException("audio");
        return data;
    }

    private static void checkStatus(int status) throws SpeechException {
        if (status >= 200 && status < 300) return;
        throw new SpeechException(status == 401 || status == 403 ? "auth" : status == 429 ? "rate" : "http");
    }

    @Override
    public void close() {
        this.transport.close();
    }
}
