package com.iafenvoy.system.registry;

import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.module.SystemModule;
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
        event.dataPackRegistry(SystemResourceKeys.CURRENCY, CurrencyDefinition.DIRECT_CODEC, CurrencyDefinition.DIRECT_CODEC);
        event.dataPackRegistry(SystemResourceKeys.ONLINE_JOB, OnlineJobEntry.CODEC.codec());
        event.dataPackRegistry(SystemResourceKeys.SYSTEM, SystemDefinition.CODEC, SystemDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SYSTEM_MODULE, SystemModule.CODEC, SystemModule.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SIGNIN_REWARD, SignInRewardEntry.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SHOP_OFFER, ShopOfferEntry.CODEC);
        event.dataPackRegistry(SystemResourceKeys.LOTTERY_POOL, LotteryPool.CODEC);
    }
}
