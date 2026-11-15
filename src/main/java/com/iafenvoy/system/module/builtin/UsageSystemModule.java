package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.CoreModule;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

public final class UsageSystemModule implements CoreModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "usage");
    public static final MapCodec<UsageSystemModule> CODEC = MapCodec.unit(new UsageSystemModule());

    @Override
    public MapCodec<UsageSystemModule> codec() {
        return CODEC;
    }
}
