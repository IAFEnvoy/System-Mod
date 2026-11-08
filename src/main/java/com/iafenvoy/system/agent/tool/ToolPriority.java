package com.iafenvoy.system.agent.tool;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/** Prefer relevant local reads before workflows and capabilities that may incur an additional API charge. */
public enum ToolPriority implements StringRepresentable {
    LOCAL, WORKFLOW, PAID;

    public static final Codec<ToolPriority> CODEC = StringRepresentable.fromEnum(ToolPriority::values);

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
