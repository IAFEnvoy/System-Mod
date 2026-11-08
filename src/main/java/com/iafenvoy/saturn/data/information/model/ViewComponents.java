package com.iafenvoy.saturn.data.information.model;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ViewComponents {
    private ViewComponents() {
    }

    // Component.copy() shares siblings and translatable arguments. Plain UI text can be detached without registries.
    public static Component copy(Component source) {
        if (source.getStyle().getHoverEvent() != null || source.getStyle().getClickEvent() != null)
            return serializedCopy(source);
        ComponentContents contents = source.getContents();
        ComponentContents detached;
        switch (contents) {
            case TranslatableContents translation -> {
                Object[] args = translation.getArgs().clone();
                for (int index = 0; index < args.length; index++) {
                    Object value = args[index];
                    if (value instanceof Component component) args[index] = copy(component);
                    else if (!(value instanceof String || value instanceof Number || value instanceof Boolean) && value != null)
                        args[index] = value.toString();
                }
                detached = new TranslatableContents(translation.getKey(), translation.getFallback(), args);
            }
            case KeybindContents keybind -> detached = new KeybindContents(keybind.getName());
            case PlainTextContents unused1 -> detached = contents;
            default -> {
                return serializedCopy(source);
            }
        }
        MutableComponent result = MutableComponent.create(detached).setStyle(source.getStyle());
        for (Component sibling : source.getSiblings()) result.append(copy(sibling));
        return result;
    }

    private static Component serializedCopy(Component source) {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE,
                ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow()).getOrThrow();
    }
}
