package com.iafenvoy.system.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Incremental UTF-8 SSE framing, including CR/LF boundaries, comments and multiline data fields. */
public final class ServerSentEvents {
    private final int limit;
    private final Listener listener;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final StringBuilder data = new StringBuilder();
    private String event = "message";
    private int eventBytes;
    private boolean skipLf, hasData, firstLine = true;

    public ServerSentEvents(int maxEventBytes, Listener listener) {
        if (maxEventBytes < 1) throw new IllegalArgumentException("A positive SSE event byte limit is required");
        this.limit = maxEventBytes;
        this.listener = Objects.requireNonNull(listener);
    }

    @FunctionalInterface
    public interface Listener {
        void accept(String event, String data) throws Exception;
    }

    public void accept(byte[] bytes) throws Exception {
        for (byte value : bytes) {
            if (this.skipLf) {
                this.skipLf = false;
                if (value == '\n') continue;
            }
            if (value == '\r' || value == '\n') {
                this.readLine();
                this.skipLf = value == '\r';
            } else {
                if (++this.eventBytes > this.limit) throw new IOException("SSE event exceeds byte limit");
                this.line.write(value);
            }
        }
    }

    public void finish() throws Exception {
        if (this.line.size() > 0) this.readLine();
        this.dispatch();
    }

    private void readLine() throws Exception {
        if (this.line.size() == 0) {
            this.dispatch();
            return;
        }
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(this.line.toByteArray())).toString();
        this.line.reset();
        if (this.firstLine) {
            this.firstLine = false;
            if (text.startsWith("\uFEFF")) text = text.substring(1);
        }
        if (text.startsWith(":")) return;
        int colon = text.indexOf(':');
        String field = colon < 0 ? text : text.substring(0, colon);
        String value = colon < 0 ? "" : text.substring(colon + 1);
        if (value.startsWith(" ")) value = value.substring(1);
        if (field.equals("data")) {
            if (this.hasData) this.data.append('\n');
            this.data.append(value);
            this.hasData = true;
        } else if (field.equals("event")) this.event = value;
    }

    private void dispatch() throws Exception {
        if (this.hasData) this.listener.accept(this.event, this.data.toString());
        this.data.setLength(0);
        this.event = "message";
        this.eventBytes = 0;
        this.hasData = false;
    }
}
