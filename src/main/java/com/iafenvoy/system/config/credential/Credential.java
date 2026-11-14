package com.iafenvoy.system.config.credential;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface Credential {
    Codec<Credential> CODEC = SystemRegistries.CREDENTIAL_TYPE.byNameCodec().dispatch("type", Credential::codec, Function.identity());

    @NotNull
    MapCodec<? extends Credential> codec();

    /** Creates an editor in the current Document.withContext scope; edits update this credential. */
    @NotNull
    Element createWidget();

    /** Resolve at use time without storing environment secrets in the serialized fields. */
    static String resolveEnvironment(String value, String... names) {
        if (!value.isBlank()) return value.strip();
        for (String name : names) {
            String environment = System.getenv(name);
            if (environment != null && !environment.isBlank()) return environment.strip();
        }
        return "";
    }
}
