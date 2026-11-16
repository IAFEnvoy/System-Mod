package com.iafenvoy.system.data.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.registry.SystemSpaceContentTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

/**
 * Trusted datapack behavior, never an action supplied by a button or the model.
 */
public record ActionSpaceContent(Component name, IconReference icon, EntityAction action) implements SpaceContent {
    public static final MapCodec<ActionSpaceContent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("name").forGetter(ActionSpaceContent::name),
            IconReference.CODEC.fieldOf("icon").forGetter(ActionSpaceContent::icon),
            EntityAction.CODEC.fieldOf("action").forGetter(ActionSpaceContent::action)
    ).apply(i, ActionSpaceContent::new));

    public ActionSpaceContent {
        name = name.copy();
    }

    @Override
    public SpaceContentType<ActionSpaceContent> type() {
        return SystemSpaceContentTypes.ACTION.get();
    }
}
