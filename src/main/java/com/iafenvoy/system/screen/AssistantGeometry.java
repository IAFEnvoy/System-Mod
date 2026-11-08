package com.iafenvoy.system.screen;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;

/**
 * Input and scroll observers read committed geometry; they must never start another layout traversal.
 */
public final class AssistantGeometry {
    private AssistantGeometry() {
    }

    public static Element.DOMRect bounds(Element element) {
        Rect rect = element.getLayout().getCommittedRectIfValid();
        if (rect == null || !rect.isDisplayed()) return null;
        double x = rect.position.x + rect.box.getMarginLeft();
        double y = rect.position.y + rect.box.getMarginTop();
        Size size = rect.getElementSize();
        if (Base.hasVisualTransform(element) && element.getLayout().getCommittedWorldTransformIfValid() == null)
            return null;
        double[] visual = Base.visualBounds(element, x, y, size.width(), size.height());
        return visual == null ? new Element.DOMRect(x, y, size.width(), size.height())
                : new Element.DOMRect(visual[0], visual[1], visual[2], visual[3]);
    }

    public static Size innerSize(Rect rect) {
        // Box.innerSize() calls Size.of(), which can recursively measure the subtree even after a commit.
        return new Size(Math.max(0, rect.getElementSize().width() - rect.box.getBorderHorizontal()
                - rect.box.getPaddingHorizontal() - rect.element.getVerticalScrollbarGutter()),
                Math.max(0, rect.getElementSize().height() - rect.box.getBorderVertical()
                        - rect.box.getPaddingVertical() - rect.element.getHorizontalScrollbarGutter()));
    }

    public static double scrollLimit(Element element, boolean horizontal) {
        Rect rect = element.getLayout().getCommittedRectIfValid();
        if (rect == null || !rect.isDisplayed()) return 0;
        Size size = element == element.document.documentElement
                ? new Size(element.document.getViewport().layoutWidth() - element.getVerticalScrollbarGutter(),
                element.document.getViewport().layoutHeight() - element.getHorizontalScrollbarGutter()) : innerSize(rect);
        return Math.max(0, horizontal ? element.scrollWidth - size.width() : element.scrollHeight - size.height());
    }

    public static boolean hasScrollRange(Element element, boolean horizontal) {
        return (horizontal ? element.canScrollHorizontally() : element.canScrollVertically())
                && scrollLimit(element, horizontal) > 0.5;
    }
}
