package com.iafenvoy.system.screen.card.node.media;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.init.Element;
import net.minecraft.resources.Identifier;

/**
 * Local resource images use AUI's asynchronous loader; rendering never performs an HTTP request.
 */
public record ImageNode(String source, String alt, int width, int height) implements TextProjectingNode {
    public static final MapCodec<ImageNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("source").forGetter(ImageNode::source),
            Codec.STRING.lenientOptionalFieldOf("alt", "").forGetter(ImageNode::alt),
            Codec.INT.lenientOptionalFieldOf("width", 32).forGetter(ImageNode::width),
            Codec.INT.lenientOptionalFieldOf("height", 32).forGetter(ImageNode::height)
    ).apply(i, ImageNode::new));

    @Override
    public MapCodec<ImageNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_image";
    }

    public static Element render(CardRenderer.Render view, String source, String css, int width, int height) {
        Identifier id = Identifier.tryParse(source);
        if (id == null) return null;
        Img image = new Img(view.document());
        image.setClassName(css);
        image.setAttribute("src", "/" + id.getNamespace() + "/" + id.getPath());
        image.setInlineStyleProperty("width", Math.max(1, width) + "px");
        image.setInlineStyleProperty("height", Math.max(1, height) + "px");
        return image;
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Element image = render(view, CardRenderer.rawValue(this.source, view.context(), view.indices()), view.css(), this.width, this.height);
        if (image != null)
            image.setAttribute("alt", this.text(new CardRenderer.Text(view.template(), view.context(), view.indices(), "")));
        return image;
    }

    @Override
    public String text(CardRenderer.Text view) {
        return CardRenderer.value(this.alt, view.context(), view.indices());
    }
}
