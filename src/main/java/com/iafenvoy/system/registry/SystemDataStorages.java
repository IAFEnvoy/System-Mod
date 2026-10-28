package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.CommerceDataStorage;
import com.iafenvoy.system.module.ModuleAccessDataStorage;
import com.iafenvoy.system.module.advancement.AdvancementDataStorage;
import com.iafenvoy.system.module.core.AgentJournalDataStorage;
import com.iafenvoy.system.module.inbox.InboxDataStorage;
import com.iafenvoy.system.module.signin.SignInDataStorage;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Collect module declarations before Saturn freezes its registry; business codecs stay with their owners.
 */
@SuppressWarnings("unused")
public final class SystemDataStorages {
    public static final DeferredRegister<MapCodec<? extends DataStorage>> REGISTRY = DeferredRegister.create(SaturnRegistries.DATA_STORAGE_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<ModuleAccessDataStorage>> MODULE_ACCESS = REGISTRY.register("module_access", () -> ModuleAccessDataStorage.CODEC);

    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AdvancementDataStorage>> ADVANCEMENT = REGISTRY.register("advancement", () -> AdvancementDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AgentJournalDataStorage>> AGENT_JOURNAL = REGISTRY.register("agent_journal", () -> AgentJournalDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<CommerceDataStorage>> COMMERCE = REGISTRY.register("commerce", () -> CommerceDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<InboxDataStorage>> INBOX = REGISTRY.register("inbox", () -> InboxDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<SignInDataStorage>> SIGN_IN = REGISTRY.register("sign_in", () -> SignInDataStorage.CODEC);
}
