package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.node.container.ContainerNode;
import com.iafenvoy.system.screen.card.node.container.DetailsNode;
import com.iafenvoy.system.screen.card.node.container.GroupsNode;
import com.iafenvoy.system.screen.card.node.display.DividerNode;
import com.iafenvoy.system.screen.card.node.display.ProgressNode;
import com.iafenvoy.system.screen.card.node.integration.PlayerInformationNode;
import com.iafenvoy.system.screen.card.node.integration.RecipeNode;
import com.iafenvoy.system.screen.card.node.interaction.ButtonNode;
import com.iafenvoy.system.screen.card.node.interaction.LinkNode;
import com.iafenvoy.system.screen.card.node.layout.FlexNode;
import com.iafenvoy.system.screen.card.node.layout.GridNode;
import com.iafenvoy.system.screen.card.node.media.FtbIconNode;
import com.iafenvoy.system.screen.card.node.media.ImageNode;
import com.iafenvoy.system.screen.card.node.media.ItemNode;
import com.iafenvoy.system.screen.card.node.media.PlayerNode;
import com.iafenvoy.system.screen.card.node.text.MarkdownNode;
import com.iafenvoy.system.screen.card.node.text.TextNode;
import com.iafenvoy.system.screen.card.node.text.TitleNode;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemCardNodeTypes {
    public static final DeferredRegister<MapCodec<? extends CardNode>> REGISTRY = DeferredRegister.create(SystemResourceKeys.CARD_NODE_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<TextNode>> TEXT = REGISTRY.register("text", () -> TextNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<MarkdownNode>> MARKDOWN = REGISTRY.register("markdown", () -> MarkdownNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<TitleNode>> TITLE = REGISTRY.register("title", () -> TitleNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ImageNode>> IMAGE = REGISTRY.register("image", () -> ImageNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ItemNode>> ITEM = REGISTRY.register("item", () -> ItemNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<PlayerNode>> PLAYER = REGISTRY.register("player", () -> PlayerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<FtbIconNode>> FTB_ICON = REGISTRY.register("ftb_icon", () -> FtbIconNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<FlexNode>> FLEX = REGISTRY.register("flex", () -> FlexNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<GridNode>> GRID = REGISTRY.register("grid", () -> GridNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ContainerNode>> CONTAINER = REGISTRY.register("container", () -> ContainerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<GroupsNode>> GROUPS = REGISTRY.register("groups", () -> GroupsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<DetailsNode>> DETAILS = REGISTRY.register("details", () -> DetailsNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ButtonNode>> BUTTON = REGISTRY.register("button", () -> ButtonNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<LinkNode>> LINK = REGISTRY.register("link", () -> LinkNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<DividerNode>> DIVIDER = REGISTRY.register("divider", () -> DividerNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<ProgressNode>> PROGRESS = REGISTRY.register("progress", () -> ProgressNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<PlayerInformationNode>> PLAYER_INFORMATION = REGISTRY.register("player_information", () -> PlayerInformationNode.CODEC);
    public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<RecipeNode>> RECIPE = REGISTRY.register("recipe", () -> RecipeNode.CODEC);
}
