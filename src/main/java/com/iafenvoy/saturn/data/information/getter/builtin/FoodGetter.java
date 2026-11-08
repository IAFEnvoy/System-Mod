package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public enum FoodGetter implements EntityValueGetter<PlayerViewData.Food> {
    INSTANCE;

    public static final MapCodec<FoodGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<FoodGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<PlayerViewData.Food> valueCodec() {
        return PlayerViewData.Food.CODEC;
    }

    @Override
    public ViewValue<PlayerViewData.Food> capture(Entity entity) {
        if (!(entity instanceof Player player)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        CompoundTag data = new CompoundTag();
        player.getFoodData().addAdditionalSaveData(data);
        return ViewValue.known(new PlayerViewData.Food(player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                data.getDouble("foodExhaustionLevel")), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<PlayerViewData.Food> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "foodLevel", data -> new PlayerViewData.Food(
                data.getInt("foodLevel"), data.getDouble("foodSaturationLevel"),
                data.getDouble("foodExhaustionLevel")));
    }
}
