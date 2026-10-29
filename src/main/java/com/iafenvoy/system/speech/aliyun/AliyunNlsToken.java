package com.iafenvoy.system.speech.aliyun;

import com.iafenvoy.system.data.speech.AliyunNlsTokenMessage;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.HttpTransport;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Client-local POP signing and a single in-memory token shared across recognition sessions.
 */
final class AliyunNlsToken {
    private static final String ENDPOINT = "https://nls-meta.cn-shanghai.aliyuncs.com/";
    private static final long REFRESH_AHEAD_SECONDS = 120;
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static Cached cached;

    private AliyunNlsToken() {
    }

    static AliyunNlsTokenMessage.Token get(AsrProvider.Request request, HttpTransport transport) throws Exception {
        // Serialize refreshes without keeping a map of old credentials or blocking the Minecraft thread.
        LOCK.lockInterruptibly();
        try {
            String fingerprint = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest((request.accessKeyId() + "\0" + request.accessKeySecret()).getBytes(StandardCharsets.UTF_8)));
            if (cached != null && cached.fingerprint.equals(fingerprint) && usable(cached.token)) return cached.token;
            cached = null;
            AliyunNlsTokenMessage.Token token = fetch(request, transport);
            cached = new Cached(fingerprint, token);
            return token;
        } finally {
            LOCK.unlock();
        }
    }

    static void invalidate(AliyunNlsTokenMessage.Token token) {
        LOCK.lock();
        try {
            // An old session must not discard a replacement token belonging to a newer session.
            if (cached != null && cached.token == token) cached = null;
        } finally {
            LOCK.unlock();
        }
    }

    private static boolean usable(AliyunNlsTokenMessage.Token token) {
        return token.expireTime() > Instant.now().getEpochSecond() + REFRESH_AHEAD_SECONDS;
    }

    private static AliyunNlsTokenMessage.Token fetch(AsrProvider.Request request, HttpTransport transport) throws Exception {
        TreeMap<String, String> parameters = new TreeMap<>();
        parameters.put("AccessKeyId", request.accessKeyId());
        parameters.put("Action", "CreateToken");
        parameters.put("Format", "JSON");
        // Token issuance always uses Shanghai, independently of the recognition gateway's region.
        parameters.put("RegionId", "cn-shanghai");
        parameters.put("SignatureMethod", "HMAC-SHA1");
        parameters.put("SignatureNonce", UUID.randomUUID().toString());
        parameters.put("SignatureVersion", "1.0");
        parameters.put("Timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        parameters.put("Version", "2019-02-28");
        String query = parameters.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String signature;
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec((request.accessKeySecret() + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            signature = Base64.getEncoder().encodeToString(hmac.doFinal(("GET&" + encode("/") + "&" + encode(query)).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new SpeechException("token_request");
        }
        HttpRequest http = HttpRequest.newBuilder(URI.create(ENDPOINT + "?" + query + "&Signature=" + encode(signature)))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
        HttpResponse<byte[]> response = transport.send(http, 65536);
        if (response.statusCode() == 429) throw new SpeechException("rate_limit");
        AliyunNlsTokenMessage.Response body;
        try {
            body = CodecIO.parse(AliyunNlsTokenMessage.Response.CODEC, new String(response.body(), StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            // Codec errors may contain response values; expose only a stable local error code.
            throw new SpeechException("token_request");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403 || body.code().startsWith("InvalidAccessKeyId")
                || body.code().contains("Signature") || body.code().startsWith("Forbidden"))
            throw new SpeechException("accesskey");
        if (response.statusCode() != 200 || !body.code().isEmpty()) throw new SpeechException("token_request");
        AliyunNlsTokenMessage.Token token = body.token().orElseThrow(() -> new SpeechException("token_request"));
        if (token.id().isBlank() || token.id().chars().anyMatch(c -> c <= 32 || c >= 127) || !usable(token))
            throw new SpeechException("token_request");
        return token;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private record Cached(String fingerprint, AliyunNlsTokenMessage.Token token) {
    }
}
