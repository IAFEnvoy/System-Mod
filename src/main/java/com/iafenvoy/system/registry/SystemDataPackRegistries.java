package com.iafenvoy.system.registry;

import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.SystemDefinition;
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
        event.dataPackRegistry(SystemResourceKeys.SIGNIN_REWARDS, SignInRewardEntry.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SHOP_OFFERS, ShopOfferEntry.CODEC);
        event.dataPackRegistry(SystemResourceKeys.LOTTERY_POOLS, LotteryPool.CODEC);
    }
}
