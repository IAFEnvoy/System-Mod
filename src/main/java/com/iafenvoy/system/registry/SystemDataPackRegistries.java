package com.iafenvoy.system.registry;

import com.iafenvoy.system.agent.commerce.CatalogEntry;
import com.iafenvoy.system.agent.commerce.LotteryPool;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.SystemModule;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Systems and modules synchronize to clients; commerce definitions remain server-owned registries.
 */
@EventBusSubscriber
public final class SystemDataPackRegistries {
    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SystemResourceKeys.SYSTEM, SystemDefinition.CODEC, SystemDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SYSTEM_MODULE, SystemModule.CODEC, SystemModule.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SIGNIN_REWARDS, CatalogEntry.REWARD);
        event.dataPackRegistry(SystemResourceKeys.SHOP_OFFERS, CatalogEntry.OFFER);
        event.dataPackRegistry(SystemResourceKeys.LOTTERY_POOLS, LotteryPool.Definition.CODEC);
    }
}
