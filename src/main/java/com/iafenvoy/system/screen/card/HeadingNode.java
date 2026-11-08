package com.iafenvoy.system.screen.card;

import java.util.Optional;

/**
 * The element decides whether its text can serve as the card heading.
 */
public interface HeadingNode extends TextProjectingNode {
    Optional<String> cardHeading(CardRenderer.Text view);
}
