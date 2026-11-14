package com.iafenvoy.system.config.credential.builtin;

import com.iafenvoy.system.config.credential.Credential;
import com.iafenvoy.system.config.credential.CredentialWidgets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.NotNull;

public final class AliyunNlsCredential implements Credential {
    public static final MapCodec<AliyunNlsCredential> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("app_key", "").forGetter(credential -> credential.appKey),
            Codec.STRING.lenientOptionalFieldOf("access_key_id", "").forGetter(credential -> credential.accessKeyId),
            Codec.STRING.lenientOptionalFieldOf("access_key_secret", "").forGetter(credential -> credential.accessKeySecret)
    ).apply(i, AliyunNlsCredential::new));
    private static final String[] APP_KEY_ENVIRONMENTS = {"ALIYUN_NLS_APP_KEY"};
    private static final String[] ACCESS_KEY_ID_ENVIRONMENTS = {"ALIYUN_AK_ID", "ALIBABA_CLOUD_ACCESS_KEY_ID"};
    private static final String[] ACCESS_KEY_SECRET_ENVIRONMENTS = {"ALIYUN_AK_SECRET", "ALIBABA_CLOUD_ACCESS_KEY_SECRET"};
    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;

    public AliyunNlsCredential() {
        this("", "", "");
    }

    public AliyunNlsCredential(String appKey, String accessKeyId, String accessKeySecret) {
        this.appKey = appKey;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    public String getAppKey() {
        return Credential.resolveEnvironment(this.appKey, APP_KEY_ENVIRONMENTS);
    }

    public String getAccessKeyId() {
        return Credential.resolveEnvironment(this.accessKeyId, ACCESS_KEY_ID_ENVIRONMENTS);
    }

    public String getAccessKeySecret() {
        return Credential.resolveEnvironment(this.accessKeySecret, ACCESS_KEY_SECRET_ENVIRONMENTS);
    }

    @Override
    public @NotNull MapCodec<AliyunNlsCredential> codec() {
        return CODEC;
    }

    @Override
    public @NotNull Element createWidget() {
        Element widget = CredentialWidgets.create();
        CredentialWidgets.field(widget, "app_key", this.appKey, false, value -> this.appKey = value, APP_KEY_ENVIRONMENTS);
        CredentialWidgets.field(widget, "access_key_id", this.accessKeyId, false, value -> this.accessKeyId = value, ACCESS_KEY_ID_ENVIRONMENTS);
        CredentialWidgets.field(widget, "access_key_secret", this.accessKeySecret, true, value -> this.accessKeySecret = value, ACCESS_KEY_SECRET_ENVIRONMENTS);
        return widget;
    }

    @Override
    public @NotNull String toString() {
        return "AliyunNlsCredential[redacted]";
    }
}
