package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemTicketTypes {
    public static final DeferredRegister<TicketType> REGISTRY = DeferredRegister.create(Registries.TICKET_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<TicketType, TicketType> ANCHOR = REGISTRY.register("anchor", () -> new TicketType(200, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
}
