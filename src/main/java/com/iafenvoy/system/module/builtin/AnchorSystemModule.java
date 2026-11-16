package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.anchor.TeleportAnchors;
import com.iafenvoy.system.data.input.AnchorDeleteInput;
import com.iafenvoy.system.data.input.AnchorListInput;
import com.iafenvoy.system.data.input.AnchorSaveInput;
import com.iafenvoy.system.data.input.AnchorTeleportInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.anchor.AnchorDeleteModule;
import com.iafenvoy.system.module.builtin.anchor.AnchorListModule;
import com.iafenvoy.system.module.builtin.anchor.AnchorSaveModule;
import com.iafenvoy.system.module.builtin.anchor.AnchorTeleportModule;
import com.iafenvoy.system.module.builtin.storage.AnchorDataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

public record AnchorSystemModule(NumberProvider limit, int cooldown, boolean crossDimension) implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor");
    public static final MapCodec<AnchorSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("limit", new Constant(3)).forGetter(AnchorSystemModule::limit),
            Codec.intRange(0, 86400).optionalFieldOf("cooldown", 10).forGetter(AnchorSystemModule::cooldown),
            Codec.BOOL.optionalFieldOf("cross_dimension", true).forGetter(AnchorSystemModule::crossDimension)
    ).apply(i, AnchorSystemModule::new));
    public static final ModuleStorage<AnchorDataStorage> STORAGE = new ModuleStorage<>(ID, ID,
            AnchorDataStorage.class, AnchorDataStorage.CODEC, AnchorDataStorage::new);

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
                        .forReasoning("List my personal teleport anchors, exact IDs, unlocked capacity and cooldown without teleporting."));
        ToolRegistry.Tool<AnchorSaveInput, AnchorListOutput> save = context.tool(BuiltinTools.ANCHOR_SAVE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_SAVE, true, false, false, anchors::save));
        ToolRegistry.Tool<AnchorDeleteInput, AnchorListOutput> delete = context.tool(BuiltinTools.ANCHOR_DELETE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_DELETE, true, false, false, anchors::delete));
        ToolRegistry.Tool<AnchorTeleportInput, AnchorListOutput> teleport = context.tool(BuiltinTools.ANCHOR_TELEPORT,
                () -> new ToolRegistry.Tool<>(BuiltinTools.ANCHOR_TELEPORT, true, false, false, anchors::teleport));
        return List.of(new AnchorListModule(query), new AnchorSaveModule(save), new AnchorDeleteModule(delete), new AnchorTeleportModule(teleport));
    }
}
