package com.iafenvoy.system.config.credential;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared form construction only; no configuration persistence or service binding.
 */
public final class CredentialWidgets {
    private CredentialWidgets() {
    }

    public static Element create() {
        Document document = Objects.requireNonNull(Document.getContextDocument(), "Credential widgets require Document.withContext");
        return document.createElement("div");
    }

    public static void field(Element parent, String name, String value, boolean secret, Consumer<String> changed, String... environments) {
        Document document = parent.document;
        Element row = document.createElement("div");
        row.setClassName("api_field");
        Element label = document.createElement("label");
        Element caption = document.createElement("span");
        caption.setTextContent(Component.translatable("system.api.field." + name).getString());
        Element input = Element.init(document.createElement("input"));
        input.setAttribute("name", name);
        input.setAttribute("type", secret ? "password" : "text");
        input.setAttribute("autocomplete", "off");
        input.setAttribute("spellcheck", "false");
        input.setAttribute("placeholder", Component.translatable("system.api.value_hint").getString());
        input.setValue(value);
        input.addEventListener("input", _ -> changed.accept(input.getValue()));
        label.appendChild(caption);
        label.appendChild(input);
        row.appendChild(label);
        if (environments.length > 0) {
            Element hint = document.createElement("p");
            hint.setClassName("card_context");
            hint.setTextContent(Component.translatable("system.api.environment_auto").getString() + " " + String.join(" / ", environments));
            row.appendChild(hint);
        }
        parent.appendChild(row);
    }
}
