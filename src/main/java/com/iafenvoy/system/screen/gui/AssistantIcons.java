package com.iafenvoy.system.screen.gui;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.List;

/**
 * Synchronized icon references use AUI's existing image and native item renderers.
 */
public final class AssistantIcons {
    private AssistantIcons() {
    }

    public static Element create(Document document, IconReference icon, int size) {
        return icon.value().map(texture -> {
            Img image = new Img(document);
            image.setAttribute("src", "/" + texture.getNamespace() + "/" + texture.getPath());
            image.setInlineStyleProperty("width", size + "px");
            image.setInlineStyleProperty("height", size + "px");
            return image;
        }, item -> AssistantItems.entry(document, ItemPreviewOutput.of(item), List.of(), size, false));
    }
}
