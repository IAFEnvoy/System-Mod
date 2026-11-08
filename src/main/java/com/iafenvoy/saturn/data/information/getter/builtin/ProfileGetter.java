package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.PlayerViewData;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ProfileGetter implements EntityValueGetter<PlayerViewData.Profile> {
    INSTANCE;

    public static final MapCodec<ProfileGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<ProfileGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<PlayerViewData.Profile> valueCodec() {
        return PlayerViewData.Profile.CODEC;
    }

    @Override
    public ViewValue<PlayerViewData.Profile> capture(Entity entity) {
        if (!(entity instanceof Player player)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        Map<String, String> properties = new LinkedHashMap<>();
        player.getGameProfile().getProperties().forEach((name, property) -> {
            properties.put(name, property.value());
            if (property.hasSignature()) properties.put(name + ".signature", property.signature());
        });
        return ViewValue.known(new PlayerViewData.Profile(player.getGameProfile().getName(), player.getDisplayName(), properties), ValueOrigin.CAPTURED);
    }
}
