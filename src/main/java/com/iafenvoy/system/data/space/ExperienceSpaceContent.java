package com.iafenvoy.system.data.space;

import com.iafenvoy.system.registry.SystemSpaceContentTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public record ExperienceSpaceContent(boolean levels) implements SpaceContent {
    public static final MapCodec<ExperienceSpaceContent> CODEC = Codec.BOOL.optionalFieldOf("levels", false)
            .xmap(ExperienceSpaceContent::new, ExperienceSpaceContent::levels);

    @Override
    public SpaceContentType<ExperienceSpaceContent> type() {
        return SystemSpaceContentTypes.EXPERIENCE.get();
    }
}
