package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.commerce.CommercePreviewTool;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.lottery.LotteryDrawModule;
import com.iafenvoy.system.agent.builtin.lottery.LotteryDrawTool;
import com.iafenvoy.system.agent.builtin.lottery.LotteryModule;
import com.iafenvoy.system.agent.builtin.lottery.LotteryPreviewModule;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.LotteryPool;
import com.iafenvoy.system.agent.commerce.PurchaseType;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
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
        Optional<List<Either<ResourceKey<LotteryPool.Definition>, TagKey<LotteryPool.Definition>>>> pools) implements CatalogModule<LotteryPool.Definition> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery");
    public static final MapCodec<LotterySystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.LOTTERY_POOLS).optionalFieldOf("pools").forGetter(LotterySystemModule::pools)
    ).apply(i, LotterySystemModule::new));
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    public LotterySystemModule {
        pools = pools.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<LotteryPool.Definition>> catalog() {
        return SystemResourceKeys.LOTTERY_POOLS;
    }

    @Override
    public Optional<List<Either<ResourceKey<LotteryPool.Definition>, TagKey<LotteryPool.Definition>>>> options() {
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
        return context.commerce().runtime(PurchaseType.LOTTERY, STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = context.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(context.commerce()).tool());
        return List.of(new LotteryModule(preview), new LotteryPreviewModule(preview), new LotteryDrawModule(new LotteryDrawTool(context.commerce()).tool()));
    }
}
