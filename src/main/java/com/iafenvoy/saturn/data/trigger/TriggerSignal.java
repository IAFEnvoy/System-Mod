package com.iafenvoy.saturn.data.trigger;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable runtime notification sent to the trigger dispatcher.
 */
public record TriggerSignal(ResourceLocation type, TriggerContext context, @Nullable ResourceLocation source, long gameTime) {
}
