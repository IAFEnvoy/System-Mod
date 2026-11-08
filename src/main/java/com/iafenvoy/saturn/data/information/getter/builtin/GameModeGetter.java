package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

public enum GameModeGetter implements EntityValueGetter<String> {
    INSTANCE;

    public static final MapCodec<GameModeGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<GameModeGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public ViewValue<String> capture(Entity entity) {
        return entity instanceof ServerPlayer server
                ? ViewValue.known(server.gameMode.getGameModeForPlayer().getName(), ValueOrigin.CAPTURED)
                : ViewValue.missing(MissingReason.UNSUPPORTED);
    }

    @Override
    public ViewValue<String> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "playerGameType", data -> {
            int mode = data.getInt("playerGameType");
            if (mode < 0 || mode >= GameType.values().length)
                throw new IllegalArgumentException("Invalid saved game mode");
            return GameType.byId(mode).getName();
        });
    }
}
