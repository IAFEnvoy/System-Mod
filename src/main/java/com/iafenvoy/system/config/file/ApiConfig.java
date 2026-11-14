package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.config.credential.Credential;
import com.iafenvoy.system.config.credential.CredentialEntry;
import com.iafenvoy.system.config.credential.CredentialManager;
import com.iafenvoy.system.config.credential.builtin.AliyunNlsCredential;
import com.iafenvoy.system.config.credential.builtin.DashScopeCredential;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.*;

/** Process-local credentials; never include this configuration in network or player-state codecs. */
public record ApiConfig(List<CredentialEntry> credentials, Map<String, String> bindings) {
    public static final String FILE_NAME = "system-api.json";
    public static final Codec<ApiConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            CredentialEntry.CODEC.listOf().lenientOptionalFieldOf("credentials", List.of()).forGetter(ApiConfig::credentials),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).lenientOptionalFieldOf("bindings", Map.of()).forGetter(ApiConfig::bindings)
    ).apply(i, ApiConfig::new));
    public static final ConfigInstance<ApiConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, defaults());

    public ApiConfig {
        credentials = List.copyOf(credentials);
        bindings = Map.copyOf(bindings);
    }

    public static ApiConfig defaults() {
        return new ApiConfig(List.of(
                new CredentialEntry("deepseek", "DeepSeek", new DeepSeekCredential()),
                new CredentialEntry("dashscope", "DashScope", new DashScopeCredential()),
                new CredentialEntry("aliyun_nls", "Aliyun NLS", new AliyunNlsCredential())),
                Map.of("model", "deepseek", "search", "deepseek", "tts", "dashscope", "asr", "aliyun_nls"));
    }

    public Optional<CredentialEntry> entry(String id) {
        // Ambiguous IDs must never silently select another account.
        List<CredentialEntry> matches = this.credentials.stream().filter(entry -> entry.id().equals(id)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public String binding(String usage) {
        return this.bindings.getOrDefault(usage, "");
    }

    public Optional<Credential> selected(String usage) {
        return this.entry(this.binding(usage)).map(CredentialEntry::credential);
    }

    /** Extensions can use their own usage IDs and typed service managers. */
    public <S> Optional<S> create(String usage, CredentialManager<S> manager) {
        return this.selected(usage).flatMap(manager::create);
    }

    public ApiConfig bind(String usage, String credential) {
        Map<String, String> updated = new HashMap<>(this.bindings);
        updated.put(usage, credential);
        return new ApiConfig(this.credentials, updated);
    }

    public ApiConfig put(CredentialEntry credential) {
        List<CredentialEntry> updated = new ArrayList<>(this.credentials);
        int index = this.entry(credential.id()).map(this.credentials::indexOf).orElse(-1);
        updated.removeIf(entry -> entry.id().equals(credential.id()));
        updated.add(index < 0 ? updated.size() : Math.min(index, updated.size()), credential);
        return new ApiConfig(updated, this.bindings);
    }

    public ApiConfig remove(String id) {
        Map<String, String> updated = new HashMap<>(this.bindings);
        updated.replaceAll((_, credential) -> credential.equals(id) ? "" : credential);
        return new ApiConfig(this.credentials.stream().filter(entry -> !entry.id().equals(id)).toList(), updated);
    }

    @Override
    public @NonNull String toString() {
        return "ApiConfig[credentials=" + this.credentials.size() + ", bindings=" + this.bindings + "]";
    }
}
