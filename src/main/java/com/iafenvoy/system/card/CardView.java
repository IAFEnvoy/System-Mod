package com.iafenvoy.system.card;

import com.iafenvoy.system.util.LegacyText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Immutable presentation description; DOM nodes and executable callbacks stay client-owned.
 */
public record CardView(Identifier template, String titleKey, Component summary, CardContext context,
                       List<CardButton> buttons, SpeechContent speech) {
    public CardView {
        summary = LegacyText.snapshot(summary);
        buttons = List.copyOf(buttons);
    }
}
