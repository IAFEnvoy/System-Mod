package com.iafenvoy.system.data.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.registry.SystemSpaceContentTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A durable, player-selected reward. Options cannot contain another choice.
 */
public record ChoiceSpaceContent(Component name, IconReference icon, List<Reward> options) implements SpaceContent {
    private static final Codec<Reward> OPTION = Codec.lazyInitialized(Reward.CODEC::codec).validate(reward ->
            reward.stacks().isEmpty() || reward.contents().stream().anyMatch(stack -> stack.content() instanceof ChoiceSpaceContent)
                    ? DataResult.error(() -> "Choice options need contents and cannot contain nested choices") : DataResult.success(reward));
    public static final MapCodec<ChoiceSpaceContent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("name").forGetter(ChoiceSpaceContent::name),
            IconReference.CODEC.fieldOf("icon").forGetter(ChoiceSpaceContent::icon),
            OPTION.listOf(2, 36).fieldOf("options").forGetter(ChoiceSpaceContent::options)
    ).apply(i, ChoiceSpaceContent::new));

    public ChoiceSpaceContent {
        name = name.copy();
        options = List.copyOf(options);
        if (options.size() < 2 || options.size() > 36 || options.stream().anyMatch(reward -> reward.stacks().isEmpty()
                || reward.contents().stream().anyMatch(stack -> stack.content() instanceof ChoiceSpaceContent)))
            throw new IllegalArgumentException("Invalid choice options");
    }

    @Override
    public SpaceContentType<?> type() {
        return SystemSpaceContentTypes.CHOICE.get();
    }
}
