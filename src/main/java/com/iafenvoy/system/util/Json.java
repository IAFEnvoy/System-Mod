package com.iafenvoy.system.util;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.iafenvoy.system.agent.AgentException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public final class Json {
    private Json() {
    }

    public static JsonObject object(String text, int limit) {
        if (text == null || text.length() > limit) throw new AgentException("INVALID_INPUT");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = read(reader, 0);
            if (!result.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new AgentException("INVALID_INPUT");
            return result.getAsJsonObject();
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new AgentException("INVALID_INPUT");
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 24) throw new AgentException("INVALID_INPUT");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject();
                Set<String> keys = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key) || keys.size() > 8192) throw new AgentException("INVALID_INPUT");
                    result.add(key, read(reader, depth + 1));
                }
                reader.endObject();
                yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (result.size() >= 8192) throw new AgentException("INVALID_INPUT");
                    result.add(read(reader, depth + 1));
                }
                reader.endArray();
                yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new AgentException("INVALID_INPUT");
        };
    }

    public static String clip(String text, int length) {
        return text.length() > length ? text.substring(0, length) : text;
    }
}
