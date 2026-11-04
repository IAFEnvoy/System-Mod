package com.iafenvoy.system.agent.builtin.output;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One cell of a shelf card: the label under the previews and the item previews drawn inside it. Shop offers and
 * lottery pools share the shelf so both catalogues render through the same node.
 */
public interface ShelfEntry {
    Component label();

    List<ItemPreviewOutput> items();

    /**
     * Lines appended after the vanilla item tooltip of one preview; empty by default.
     */
    default List<Component> tooltip(int index) {
        return List.of();
    }
}
