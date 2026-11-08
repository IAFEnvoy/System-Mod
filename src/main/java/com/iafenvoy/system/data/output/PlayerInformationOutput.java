package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.data.information.InformationText;
import com.iafenvoy.saturn.data.information.model.InformationEntry;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

/**
 * Saturn owns row evaluation and formatting; this detached projection also works in JSON history.
 */
public record PlayerInformationOutput(int importance, List<Component> lines) {
    public static final String PARAMETER = "player_information";
    public static final Codec<PlayerInformationOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("importance").forGetter(PlayerInformationOutput::importance),
            ComponentSerialization.CODEC.listOf().fieldOf("lines").forGetter(PlayerInformationOutput::lines)
    ).apply(i, PlayerInformationOutput::new));

    public PlayerInformationOutput {
        lines = lines.stream().map(LegacyText::snapshot).toList();
    }

    public static PlayerInformationOutput of(InformationEntry row) {
        return new PlayerInformationOutput(row.importance(), InformationText.lines(row));
    }
}
