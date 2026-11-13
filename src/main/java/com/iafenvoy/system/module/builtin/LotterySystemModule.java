package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.tool.CommercePreviewTool;
import com.iafenvoy.system.assistant.tool.lottery.LotteryDrawTool;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.lottery.LotteryDrawModule;
import com.iafenvoy.system.module.builtin.lottery.LotteryModule;
import com.iafenvoy.system.module.builtin.lottery.LotteryPreviewModule;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record LotterySystemModule(
        Optional<List<Either<ResourceKey<LotteryPool>, TagKey<LotteryPool>>>> pools) implements CatalogModule<LotteryPool> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery");
    public static final MapCodec<LotterySystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.LOTTERY_POOL).optionalFieldOf("pools").forGetter(LotterySystemModule::pools)
    ).apply(i, LotterySystemModule::new));
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    public LotterySystemModule {
        pools = pools.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<LotteryPool>> catalog() {
        return SystemResourceKeys.LOTTERY_POOL;
    }

    @Override
    public Optional<List<Either<ResourceKey<LotteryPool>, TagKey<LotteryPool>>>> options() {
        return this.pools;
    }

    @Override
    public MapCodec<LotterySystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return context.get(ContextTypes.COMMERCE).runtime(PurchaseType.LOTTERY, STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = context.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(context.get(ContextTypes.COMMERCE)).tool());
        return List.of(new LotteryModule(context.get(ContextTypes.CATALOGS)), new LotteryPreviewModule(context.get(ContextTypes.CATALOGS), preview), new LotteryDrawModule(new LotteryDrawTool(context.get(ContextTypes.COMMERCE)).tool()));
    }
}
