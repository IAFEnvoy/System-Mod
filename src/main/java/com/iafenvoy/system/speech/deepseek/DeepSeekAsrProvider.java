package com.iafenvoy.system.speech.deepseek;

import com.iafenvoy.system.data.speech.DeepSeekAsrMessage;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.speech.PcmWav;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.HttpTransport;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** DeepSeek accepts one complete WAV upload; microphone collection remains session-owned and cancellable. */
public final class DeepSeekAsrProvider implements AsrProvider {
    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/v1/tools/asr");
    private static final int MAX_PCM_BYTES = 16000 * 2 * 60;
    private final HttpTransport transport = new HttpTransport();
    private final String key;

    public DeepSeekAsrProvider(DeepSeekCredential credential) {
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
    public Request request(RecognitionConfig config) {
        return new Request(this.key, "", "", "", "global");
    }

    @Override
    public void validate(Request request) throws SpeechException {
        if (request.apiKey().isBlank() || request.apiKey().chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new SpeechException("key");
    }

    @Override
    public String recognize(Request request, AudioSource audio, Consumer<String> intermediate, Runnable requestStarted) throws Exception {
        return this.recognize(request, audio, intermediate, requestStarted, _ -> {
        });
    }

    @Override
    public String recognize(Request request, AudioSource audio, Consumer<String> intermediate, Runnable requestStarted,
                            Consumer<Usage> observer) throws Exception {
        this.validate(request);
        audio.start();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        byte[] buffer = new byte[3200];
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (System.nanoTime() >= deadline) throw new TimeoutException();
            int count = audio.read(buffer);
            if (count < 0) break;
            if (count > buffer.length || (count & 1) != 0 || count > MAX_PCM_BYTES - pcm.size()) throw new SpeechException("audio");
            pcm.write(buffer, 0, count);
        }
        if (pcm.size() == 0) throw new SpeechException("empty");
        byte[] wav = PcmWav.encode(pcm.toByteArray(), 16000);
        String boundary = "system-asr-" + UUID.randomUUID();
        byte[] header = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] ending = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpRequest http = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + request.apiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(header),
                        HttpRequest.BodyPublishers.ofByteArray(wav), HttpRequest.BodyPublishers.ofByteArray(ending))).build();
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        // Recording alone is not billable. Start the ledger only when the complete upload is submitted.
        requestStarted.run();
        HttpResponse<byte[]> response = this.transport.send(http, 65536);
        if (response.statusCode() != 200) throw new SpeechException(switch (response.statusCode()) {
            case 401, 403 -> "auth";
            case 402 -> "quota";
            case 429 -> "rate_limit";
            case 408, 504 -> "timeout";
            case 400, 413 -> "audio";
            default -> "network";
        });
        DeepSeekAsrMessage result;
        try {
            result = CodecIO.parse(DeepSeekAsrMessage.CODEC, new String(response.body(), StandardCharsets.UTF_8));
            if (result.durationMillis() < 0) throw new IllegalArgumentException();
        } catch (RuntimeException invalidResponse) {
            // Decoding errors must not leak the upstream response into logs or the assistant chat.
            throw new SpeechException("response");
        }
        observer.accept(new Usage(true, result.durationMillis()));
        return result.text();
    }

    @Override
    public void close() {
        this.transport.close();
    }
}
