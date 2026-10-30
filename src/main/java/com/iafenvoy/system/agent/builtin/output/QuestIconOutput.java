package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Only type IDs and server-prepared text cross the wire; optional FTB icons are resolved on the client.
 */
public record QuestIconOutput(Kind kind, ResourceLocation type, Component title, String amount,
                              List<Component> tooltip) {
    public static final Codec<QuestIconOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(QuestIconOutput::kind),
            ResourceLocation.CODEC.fieldOf("type").forGetter(QuestIconOutput::type),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestIconOutput::title),
            Codec.STRING.fieldOf("amount").forGetter(QuestIconOutput::amount),
            ComponentSerialization.CODEC.listOf().fieldOf("tooltip").forGetter(QuestIconOutput::tooltip)
    ).apply(i, QuestIconOutput::new));

    public QuestIconOutput {
        title = LegacyText.snapshot(title);
        tooltip = tooltip.stream().map(LegacyText::snapshot).toList();
    }

    public enum Kind implements StringRepresentable {
        TASK, REWARD;
        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
