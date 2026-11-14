package com.iafenvoy.system.speech.deepseek;

import com.iafenvoy.system.data.speech.DeepSeekTtsMessage;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.speech.PcmWav;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.HttpTransport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** One HTTP request streams PCM directly into the existing cancellable playback pipeline. */
public final class DeepSeekTtsProvider implements TtsProvider {
    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/v1/tools/tts");
    private static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    private final HttpTransport transport = new HttpTransport();
    private final String key;

    public DeepSeekTtsProvider(DeepSeekCredential credential) {
        this.key = credential.getApiKey();
    }

    @Override
    public String id() {
        return "deepseek";
    }

    @Override
    public boolean configured() {
        return !this.key.isBlank();
    }

    @Override
    public Request request(SpeechConfig config, String text, String clientLanguage) {
        return new Request(text, this.key, "global", "", "tts", config.voice(), "Auto");
    }

    @Override
    public SpeechConfig defaults(SpeechConfig current) {
        return current.withVoice("tts", "stella");
    }

    @Override
    public void validate(Request request) throws SpeechException {
        if (request.apiKey().isBlank() || request.apiKey().chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new SpeechException("key");
        if (!Set.of("stella", "tide").contains(request.voice())) throw new SpeechException("voice");
        if (request.text().isBlank() || characters(request.text()) > 10000) throw new SpeechException("text");
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public void synthesizeStreaming(Request request, AudioSink audio, Consumer<Usage> observer) throws Exception {
        this.validate(request);
        // Callers already supply plain narration; disabling filtering makes the locally counted input explicit.
        String body = CodecIO.write(DeepSeekTtsMessage.CODEC, new DeepSeekTtsMessage(request.text(), request.voice(), "pcm", false));
        if (body.getBytes(StandardCharsets.UTF_8).length > 65536) throw new SpeechException("text");
        HttpRequest http = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + request.apiKey()).header("Content-Type", "application/json")
                .header("Accept", "audio/pcm").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        int[] received = {0};
        try {
            HttpResponse<Void> response = this.transport.stream(http, MAX_AUDIO_BYTES, (info, bytes) -> {
                checkStatus(info.statusCode());
                if (!info.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("audio/pcm"))
                    throw new SpeechException("response");
                received[0] += bytes.length;
                audio.accept(24000, 1, bytes);
            });
            checkStatus(response.statusCode());
            if (received[0] == 0 || (received[0] & 1) != 0) throw new SpeechException("audio");
            // No usage is returned by this API. Only a complete stream earns a local character estimate.
            observer.accept(new Usage(Unit.CHARACTERS, false, characters(request.text()), 0, 0, 0));
        } catch (IOException error) {
            if (error.getCause() instanceof SpeechException speech) throw speech;
            throw error;
        }
    }

    @Override
    public byte[] synthesize(Request request) throws Exception {
        return this.synthesize(request, _ -> {
        });
    }

    @Override
    public byte[] synthesize(Request request, Consumer<Usage> observer) throws Exception {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        this.synthesizeStreaming(request, (_, _, bytes) -> pcm.writeBytes(bytes), observer);
        return PcmWav.encode(pcm.toByteArray(), 24000);
    }

    private static long characters(String text) {
        return text.codePoints().mapToLong(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN ? 2 : 1).sum();
    }

    private static void checkStatus(int status) throws SpeechException {
        if (status == 200) return;
        throw new SpeechException(switch (status) {
            case 401, 403 -> "auth";
            case 429 -> "rate";
            case 408, 504 -> "timeout";
            default -> "http";
        });
    }

    @Override
    public void close() {
        this.transport.close();
    }
}
