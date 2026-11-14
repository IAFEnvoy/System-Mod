package com.iafenvoy.system.config.credential;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A named local account; the nested credential owns its authentication fields. */
public record CredentialEntry(String id, String name, Credential credential) {
    public static final Codec<CredentialEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(CredentialEntry::id),
            Codec.STRING.lenientOptionalFieldOf("name", "").forGetter(CredentialEntry::name),
            Credential.CODEC.fieldOf("credential").forGetter(CredentialEntry::credential)
    ).apply(i, CredentialEntry::new));

    public String label() {
        return this.name.isBlank() ? this.id : this.name;
    }
}
