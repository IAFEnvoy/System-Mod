package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.anchor.TeleportAnchors;
import com.iafenvoy.system.data.catalog.TeleportDestination;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.anchor.*;
import com.iafenvoy.system.module.builtin.storage.AnchorDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record AnchorSystemModule(NumberProvider limit, int cooldown, boolean crossDimension, List<Cost> costs,
                                 Optional<List<Either<ResourceKey<TeleportDestination>, TagKey<TeleportDestination>>>> destinations) implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor");
    public static final MapCodec<AnchorSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("limit", new Constant(3)).forGetter(AnchorSystemModule::limit),
            Codec.intRange(0, 86400).optionalFieldOf("cooldown", 10).forGetter(AnchorSystemModule::cooldown),
            Codec.BOOL.optionalFieldOf("cross_dimension", true).forGetter(AnchorSystemModule::crossDimension),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(AnchorSystemModule::costs),
            RegistryCodecs.keyOrTagList(SystemResourceKeys.TELEPORT_DESTINATION).optionalFieldOf("destinations").forGetter(AnchorSystemModule::destinations)
    ).apply(i, AnchorSystemModule::new));
    public static final ModuleStorage<AnchorDataStorage> STORAGE = new ModuleStorage<>(ID, ID,
            AnchorDataStorage.class, AnchorDataStorage.CODEC, AnchorDataStorage::new);

    public AnchorSystemModule {
        costs = List.copyOf(costs);
        destinations = destinations.map(List::copyOf);
    }

    public boolean allows(RegistryAccess access, Identifier id) {
        return this.destinations.map(options -> RegistryCodecs.matchesKeys(options, access.lookupOrThrow(SystemResourceKeys.TELEPORT_DESTINATION), id)).orElse(true);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {
            @Override
            public Session login(ServerPlayer player) {
                return data -> ModuleData.get(data, STORAGE).recover();
            }
        };
    }

    @Override
    public MapCodec<AnchorSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        TeleportAnchors anchors = new TeleportAnchors(context.engine(), context.get(ContextTypes.GAME), this);
        ToolRegistry.Tool<AnchorListInput, AnchorListOutput> query = context.tool(BuiltinTools.ANCHOR_LIST,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_LIST, false, true, false, (execution, _) -> anchors.query(execution))
                        .forReasoning("List my personal anchors and public destination selectors, unlocked capacity and cooldown without teleporting."));
        ToolRegistry.Tool<AnchorSaveInput, AnchorListOutput> save = context.tool(BuiltinTools.ANCHOR_SAVE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_SAVE, true, false, false, anchors::save));
        ToolRegistry.Tool<AnchorDeleteInput, AnchorListOutput> delete = context.tool(BuiltinTools.ANCHOR_DELETE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_DELETE, true, false, false, anchors::delete));
        ToolRegistry.Tool<AnchorRenameInput, AnchorListOutput> rename = context.tool(BuiltinTools.ANCHOR_RENAME,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_RENAME, true, false, false, anchors::rename));
        ToolRegistry.Tool<AnchorTeleportInput, AnchorListOutput> teleport = context.tool(BuiltinTools.ANCHOR_TELEPORT,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_TELEPORT, true, false, false, anchors::teleport));
        return List.of(new AnchorListModule(query), new AnchorSaveModule(save), new AnchorDeleteModule(delete), new AnchorRenameModule(rename), new AnchorTeleportModule(teleport));
    }
}
