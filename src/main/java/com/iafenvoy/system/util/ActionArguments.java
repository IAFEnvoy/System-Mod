package com.iafenvoy.system.util;

import com.google.gson.JsonObject;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentException;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * String arguments cross the UI boundary; each workflow supplies its own typed decoder.
 */
public record ActionArguments(Map<String, String> values) {
    public ActionArguments {
        values = Map.copyOf(values);
    }

    public String text(String... keys) {
        for (String key : keys) if (this.values.containsKey(key)) return this.values.get(key);
        return "";
    }

    public String required(String... keys) {
        String value = this.text(keys);
        if (value.isBlank()) throw new AgentException("INVALID_INPUT");
        return value;
    }

    public Optional<String> optional(String... keys) {
        String value = this.text(keys);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public int number(int fallback, String... keys) {
        try {
            String value = this.text(keys);
            int number = value.isBlank() ? fallback : Integer.parseInt(value);
            if (number < 0) throw new AgentException("INVALID_INPUT");
            return number;
        } catch (NumberFormatException error) {
            throw new AgentException("INVALID_INPUT");
        }
    }

    public ResourceLocation identifier(String... keys) {
        ResourceLocation id = ResourceLocation.tryParse(this.required(keys));
        if (id == null) throw new AgentException("INVALID_INPUT");
        return id;
    }

    public boolean flag(String... keys) {
        String value = this.text(keys);
        if (value.isEmpty() || value.equals("false")) return false;
        if (value.equals("true")) return true;
        throw new AgentException("INVALID_INPUT");
    }

    /**
     * Only trusted Java-generated shortcut/page DTOs are encoded here.
     */
    public static Map<String, String> encode(AgentAction call) {
        JsonObject encoded = AgentAction.CODEC.encodeStart(JsonOps.INSTANCE, call).getOrThrow().getAsJsonObject();
        encoded.remove("moduleId");
        Map<String, String> values = new LinkedHashMap<>();
        encoded.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive())
                throw new IllegalArgumentException("Card arguments must be scalar");
            values.put(entry.getKey(), entry.getValue().getAsString());
        });
        return Map.copyOf(values);
    }
}
