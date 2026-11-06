package com.iafenvoy.system.screen.card;

import com.sighs.apricityui.init.Element;

/**
 * A client-owned state source can update a button without rebuilding its card.
 */
@FunctionalInterface
public interface CardButtonBinding {
    void bind(Element button);
}
