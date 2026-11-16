package com.iafenvoy.system.assistant.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.data.space.ExperienceSpaceContent;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ExperienceSpaceType implements SpaceContentType<ExperienceSpaceContent> {
    @Override
    public MapCodec<ExperienceSpaceContent> codec() {
        return ExperienceSpaceContent.CODEC;
    }

    @Override
    public Component name(ExperienceSpaceContent content) {
        return Component.translatable(content.levels() ? "system.reward.experience_levels" : "system.reward.experience_points");
    }

    @Override
    public IconReference icon(ExperienceSpaceContent content) {
        return new IconReference(Either.left(Identifier.withDefaultNamespace("textures/item/experience_bottle.png")));
    }

    @Override
    public String searchId(ExperienceSpaceContent content) {
        return content.levels() ? "system:experience_levels" : "system:experience_points";
    }

    @Override
    public int batchSize(ExperienceSpaceContent content) {
        return content.levels() ? 1 : 100;
    }

    @Override
    public void prepare(ExperienceSpaceContent content, long amount, SpaceTransfer transfer) {
        transfer.experience(amount, content.levels());
    }
}
