package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.node.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemCardNodeTypes {
    public static final DeferredRegister<MapCodec<? extends CardNode>> REGISTRY = DeferredRegister.create(SystemResourceKeys.CARD_NODE_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<TextNode>> TEXT = REGISTRY.register("text", () -> TextNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<HeaderNode>> HEADER = REGISTRY.register("header", () -> HeaderNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ContextNode>> CONTEXT = REGISTRY.register("context", () -> ContextNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ContainerNode>> CONTAINER = REGISTRY.register("container", () -> ContainerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<RowNode>> ROW = REGISTRY.register("row", () -> RowNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ColumnNode>> COLUMN = REGISTRY.register("column", () -> ColumnNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<DetailsNode>> DETAILS = REGISTRY.register("details", () -> DetailsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<LinkNode>> LINK = REGISTRY.register("link", () -> LinkNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<DividerNode>> DIVIDER = REGISTRY.register("divider", () -> DividerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ItemsNode>> ITEMS = REGISTRY.register("items", () -> ItemsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ActionsNode>> ACTIONS = REGISTRY.register("actions", () -> ActionsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ButtonNode>> BUTTON = REGISTRY.register("button", () -> ButtonNode.CODEC);
}
