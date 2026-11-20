package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.store.AgentJournalDataStorage;
import com.iafenvoy.system.module.builtin.storage.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Collect module declarations before Saturn freezes its registry; business codecs stay with their owners.
 */
@SuppressWarnings("unused")
public final class SystemDataStorages {
    public static final DeferredRegister<MapCodec<? extends DataStorage>> REGISTRY = DeferredRegister.create(SaturnRegistries.DATA_STORAGE_TYPE, SystemMod.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AnchorDataStorage>> ANCHOR = REGISTRY.register("anchor", () -> AnchorDataStorage.CODEC);

    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<ModuleAccessDataStorage>> MODULE_ACCESS = REGISTRY.register("module_access", () -> ModuleAccessDataStorage.CODEC);

    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AdvancementDataStorage>> ADVANCEMENT = REGISTRY.register("advancement", () -> AdvancementDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AgentJournalDataStorage>> AGENT_JOURNAL = REGISTRY.register("agent_journal", () -> AgentJournalDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<CommerceDataStorage>> COMMERCE = REGISTRY.register("commerce", () -> CommerceDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<SpaceDataStorage>> SPACE = REGISTRY.register("inbox", () -> SpaceDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<SignInDataStorage>> SIGN_IN = REGISTRY.register("sign_in", () -> SignInDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<CommissionDataStorage>> COMMISSION = REGISTRY.register("commission", () -> CommissionDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<OnlineJobsDataStorage>> ONLINE_JOBS = REGISTRY.register("online_jobs", () -> OnlineJobsDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<WalletDataStorage>> ECONOMY = REGISTRY.register("economy", () -> WalletDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<DisplayCardsDataStorage>> DISPLAY_CARDS = REGISTRY.register("display_cards", () -> DisplayCardsDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<FactDataStorage>> FACTS = REGISTRY.register("facts", () -> FactDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<CollectionDataStorage>> COLLECTION = REGISTRY.register("collection", () -> CollectionDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<WorkshopDataStorage>> WORKSHOP = REGISTRY.register("workshop", () -> WorkshopDataStorage.CODEC);
}
