package com.iafenvoy.system.data.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Only server adapters create facts. Occurrence must be stable across retries and reconnects.
 */
public record PlayerFact(Identifier event, Identifier source, String subject, String occurrence, long timestamp) {
    public static final Codec<PlayerFact> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("event").forGetter(PlayerFact::event),
            Identifier.CODEC.fieldOf("source").forGetter(PlayerFact::source),
            Codec.STRING.fieldOf("subject").forGetter(PlayerFact::subject),
            Codec.STRING.fieldOf("occurrence").forGetter(PlayerFact::occurrence),
            Codec.LONG.fieldOf("timestamp").forGetter(PlayerFact::timestamp)
    ).apply(i, PlayerFact::new));

    public PlayerFact {
        if (subject.isBlank() || subject.length() > 512 || occurrence.isBlank() || occurrence.length() > 256 || timestamp < 0)
            throw new IllegalArgumentException("Invalid server fact identity");
    }

    public String key() {
        return key(this.event.toString(), this.source.toString(), this.subject, this.occurrence);
    }

    public static String key(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
