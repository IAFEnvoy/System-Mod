package com.iafenvoy.system.registry;

import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.catalog.*;
import com.iafenvoy.system.data.emotion.EmotionDefinition;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.progress.AchievementDefinition;
import com.iafenvoy.system.data.progress.FactRewardRule;
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
        event.dataPackRegistry(SystemResourceKeys.ACHIEVEMENT, AchievementDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.TELEPORT_DESTINATION, TeleportDestination.CODEC);
        event.dataPackRegistry(SystemResourceKeys.EMOTION, EmotionDefinition.CODEC, EmotionDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.CURRENCY, CurrencyDefinition.DIRECT_CODEC, CurrencyDefinition.DIRECT_CODEC);
        event.dataPackRegistry(SystemResourceKeys.ONLINE_JOB, OnlineJobEntry.CODEC.codec());
        event.dataPackRegistry(SystemResourceKeys.SYSTEM, SystemDefinition.CODEC, SystemDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SYSTEM_MODULE, SystemModule.CODEC, SystemModule.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SIGNIN_REWARD, SignInRewardEntry.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SHOP_OFFER, ShopOfferEntry.CODEC);
        event.dataPackRegistry(SystemResourceKeys.LOTTERY_POOL, LotteryPool.CODEC);
        event.dataPackRegistry(SystemResourceKeys.FACT_REWARD, FactRewardRule.CODEC);
        event.dataPackRegistry(SystemResourceKeys.COLLECTION, CollectionDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SALVAGE_RULE, SalvageRule.CODEC);
    }
}
