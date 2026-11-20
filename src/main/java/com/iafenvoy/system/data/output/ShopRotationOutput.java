package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.catalog.ShopRotation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Only stock refresh metadata crosses the wire, never the selection seed.
 */
public record ShopRotationOutput(Identifier source, ShopRotation.Period period, int count,
                                 long refreshAt, String zone, boolean perPlayer) {
    public static final Codec<ShopRotationOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("source").forGetter(ShopRotationOutput::source),
            ShopRotation.Period.CODEC.fieldOf("period").forGetter(ShopRotationOutput::period),
            Codec.INT.fieldOf("count").forGetter(ShopRotationOutput::count),
            Codec.LONG.fieldOf("refreshAt").forGetter(ShopRotationOutput::refreshAt),
            Codec.STRING.fieldOf("zone").forGetter(ShopRotationOutput::zone),
            Codec.BOOL.fieldOf("perPlayer").forGetter(ShopRotationOutput::perPlayer)
    ).apply(i, ShopRotationOutput::new));

    public Component description() {
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of(this.zone)).format(Instant.ofEpochMilli(this.refreshAt));
        return Component.translatable("system.shop.rotation", Component.translatable("system.shop.rotation." + this.period.getSerializedName()),
                this.count, date, Component.translatable(this.perPlayer ? "system.shop.rotation.personal" : "system.shop.rotation.shared"));
    }
}
