package com.iafenvoy.system.data.result;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Quest lifecycle event names shared by cards and quest adapters.
 */
public enum QuestEvent implements StringRepresentable {
    COMPLETED, UNLOCKED;
    public static final Codec<QuestEvent> CODEC = StringRepresentable.fromEnum(QuestEvent::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
