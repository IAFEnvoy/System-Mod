package com.iafenvoy.system.screen.card;

/**
 * Text export is an optional capability, independent of rendering and speech.
 */
public interface TextProjectingNode extends CardNode {
    String text(CardRenderer.Text view);
}
