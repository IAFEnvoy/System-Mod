package com.iafenvoy.system.config.credential;

import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.NotNull;

public interface Credential extends com.iafenvoy.assistant.credential.Credential {
    @Override
    @NotNull
    MapCodec<? extends Credential> codec();

    /**
     * Creates an editor in the current Document.withContext scope; edits update this credential.
     */
    @NotNull
    Element createWidget();

    /**
     * Resolve at use time without storing environment secrets in the serialized fields.
     */
    static String resolveEnvironment(String value, String... names) {
        return com.iafenvoy.assistant.credential.Credential.resolveEnvironment(value, names);
    }
}
