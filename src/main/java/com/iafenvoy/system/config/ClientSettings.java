package com.iafenvoy.system.config;

import com.mojang.serialization.Codec;

/** Presentation preference only; model and speech credentials use separate private files. */
public record ClientSettings(boolean simpleMode) {
    public static final Codec<ClientSettings> CODEC = Codec.BOOL.lenientOptionalFieldOf("simpleMode", false)
            .xmap(ClientSettings::new, ClientSettings::simpleMode).codec();
}
