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
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<MarkdownNode>> MARKDOWN = REGISTRY.register("markdown", () -> MarkdownNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<TitleNode>> TITLE = REGISTRY.register("title", () -> TitleNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ContextNode>> CONTEXT = REGISTRY.register("context", () -> ContextNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ContainerNode>> CONTAINER = REGISTRY.register("container", () -> ContainerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<RowNode>> ROW = REGISTRY.register("row", () -> RowNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ColumnNode>> COLUMN = REGISTRY.register("column", () -> ColumnNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<DetailsNode>> DETAILS = REGISTRY.register("details", () -> DetailsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<LinkNode>> LINK = REGISTRY.register("link", () -> LinkNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<DividerNode>> DIVIDER = REGISTRY.register("divider", () -> DividerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ItemsNode>> ITEMS = REGISTRY.register("items", () -> ItemsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ItemSourcesNode>> ITEM_SOURCES = REGISTRY.register("item_sources", () -> ItemSourcesNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<OfferGridNode>> OFFER_GRID = REGISTRY.register("offer_grid", () -> OfferGridNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<PrizeGridNode>> PRIZE_GRID = REGISTRY.register("prize_grid", () -> PrizeGridNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<PlayerNode>> PLAYER = REGISTRY.register("player", () -> PlayerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<PlayerInformationNode>> PLAYER_INFORMATION = REGISTRY.register("player_information", () -> PlayerInformationNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<IconsNode>> ICONS = REGISTRY.register("icons", () -> IconsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<QuestIconNode>> QUEST_ICON = REGISTRY.register("quest_icon", () -> QuestIconNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<QuestPinNode>> QUEST_PIN = REGISTRY.register("quest_pin", () -> QuestPinNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ProgressNode>> PROGRESS = REGISTRY.register("progress", () -> ProgressNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ActionsNode>> ACTIONS = REGISTRY.register("actions", () -> ActionsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<GroupedActionsNode>> GROUPED_ACTIONS = REGISTRY.register("grouped_actions", () -> GroupedActionsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ButtonNode>> BUTTON = REGISTRY.register("button", () -> ButtonNode.CODEC);
}
