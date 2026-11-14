package com.iafenvoy.system.config.credential.builtin;

import com.iafenvoy.system.config.credential.Credential;
import com.iafenvoy.system.config.credential.CredentialWidgets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.NotNull;

public final class DashScopeCredential implements Credential {
    public static final MapCodec<DashScopeCredential> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("api_key", "").forGetter(credential -> credential.apiKey),
            Codec.STRING.lenientOptionalFieldOf("workspace", "").forGetter(credential -> credential.workspace)
    ).apply(i, DashScopeCredential::new));
    private static final String[] API_KEY_ENVIRONMENTS = {"DASHSCOPE_API_KEY"};
    private static final String[] WORKSPACE_ENVIRONMENTS = {"DASHSCOPE_WORKSPACE_ID"};
    private String apiKey;
    private String workspace;

    public DashScopeCredential() {
        this("", "");
    }

    public DashScopeCredential(String apiKey, String workspace) {
        this.apiKey = apiKey;
        this.workspace = workspace;
    }

    public String getApiKey() {
        return Credential.resolveEnvironment(this.apiKey, API_KEY_ENVIRONMENTS);
    }

    public String getWorkspace() {
        return Credential.resolveEnvironment(this.workspace, WORKSPACE_ENVIRONMENTS);
    }

    @Override
    public @NotNull MapCodec<DashScopeCredential> codec() {
        return CODEC;
    }

    @Override
    public @NotNull Element createWidget() {
        Element widget = CredentialWidgets.create();
        CredentialWidgets.field(widget, "api_key", this.apiKey, true, value -> this.apiKey = value, API_KEY_ENVIRONMENTS);
        CredentialWidgets.field(widget, "workspace", this.workspace, false, value -> this.workspace = value, WORKSPACE_ENVIRONMENTS);
        return widget;
    }

    @Override
    public @NotNull String toString() {
        return "DashScopeCredential[redacted]";
    }
}
