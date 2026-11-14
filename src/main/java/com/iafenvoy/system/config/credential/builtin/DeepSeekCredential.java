package com.iafenvoy.system.config.credential.builtin;

import com.iafenvoy.system.config.credential.Credential;
import com.iafenvoy.system.config.credential.CredentialWidgets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.NotNull;

public final class DeepSeekCredential implements Credential {
    public static final MapCodec<DeepSeekCredential> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("api_key", "").forGetter(credential -> credential.apiKey)
    ).apply(i, DeepSeekCredential::new));
    private static final String[] ENVIRONMENTS = {"SYSTEM_DEEPSEEK_API_KEY", "DEEPSEEK_API_KEY"};
    private String apiKey;

    public DeepSeekCredential() {
        this("");
    }

    public DeepSeekCredential(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return Credential.resolveEnvironment(this.apiKey, ENVIRONMENTS);
    }

    @Override
    public @NotNull MapCodec<DeepSeekCredential> codec() {
        return CODEC;
    }

    @Override
    public @NotNull Element createWidget() {
        Element widget = CredentialWidgets.create();
        CredentialWidgets.field(widget, "api_key", this.apiKey, true, value -> this.apiKey = value, ENVIRONMENTS);
        return widget;
    }

    @Override
    public @NotNull String toString() {
        return "DeepSeekCredential[redacted]";
    }
}
