package com.iafenvoy.system.data.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable, server-captured destination; clients select its ID rather than supplying coordinates.
 */
public record TeleportAnchor(String id, String name, ResourceKey<Level> dimension, Vec3 position, float yaw,
                             float pitch) {
    public static final int MAX_NAME_LENGTH = 48;
    public static final Codec<TeleportAnchor> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(TeleportAnchor::id),
            Codec.STRING.fieldOf("name").forGetter(TeleportAnchor::name),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TeleportAnchor::dimension),
            Vec3.CODEC.fieldOf("position").forGetter(TeleportAnchor::position),
            Codec.FLOAT.fieldOf("yaw").forGetter(TeleportAnchor::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(TeleportAnchor::pitch)
    ).apply(i, TeleportAnchor::new));

    public static boolean validName(String name) {
        return !name.isBlank() && name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH
                && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == '§');
    }

    public TeleportAnchor withName(String name) {
        return new TeleportAnchor(this.id, name, this.dimension, this.position, this.yaw, this.pitch);
    }
}
