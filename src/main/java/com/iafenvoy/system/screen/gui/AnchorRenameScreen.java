package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.card.CardAction;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardReference;
import com.iafenvoy.system.data.card.client.CardController;
import com.iafenvoy.system.data.card.client.ClientCards;
import com.iafenvoy.system.data.store.TeleportAnchor;
import com.iafenvoy.system.util.CardEventValue;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.GeometryQueryScope;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Local text entry reuses the assistant's IME lifecycle; only confirmation sends a server card event.
 */
public final class AnchorRenameScreen extends AssistantInputScreen {
    private final @Nullable Screen parent;
    private final CardReference source;
    private final String anchor;
    private String name;
    private boolean selectName = true;

    public AnchorRenameScreen(@Nullable Screen parent, CardReference source, String anchor, String name) {
        super("system/anchor-rename.html", "");
        this.parent = parent;
        this.source = source;
        this.anchor = anchor;
        this.name = name;
        this.setPauseGame(false);
        this.setShowDefaultBackground(false);
        if (parent instanceof AssistantInputScreen screen) {
            Document document = screen.getLinkedDocument();
            Element message = document == null ? null : document.getElementById("message");
            if (message != null) screen.draft = message.getValue();
        }
    }

    @Override
    protected void initPage(Document document) {
        super.initPage(document);
        Element input = document.getElementById("anchor_name");
        input.setValue(this.name);
        input.addEventListener("input", _ -> {
            this.name = input.getValue();
            this.updateSave(document);
        });
        AssistantView.bind(document, "anchor_rename_save", this::save);
        AssistantView.bind(document, "anchor_rename_cancel", this::onClose);
        this.updateSave(document);
        input.focus();
    }

    @Override
    protected void layoutReady(Document document) {
        super.layoutReady(document);
        if (!this.selectName || !(document.getElementById("anchor_name") instanceof AbstractText input)
                || input.getLayout().getCommittedRectIfValid() == null) return;
        this.selectName = false;
        try (GeometryQueryScope _ = GeometryQueryScope.open()) {
            input.setSelectionRange(0, this.name.length());
        }
    }

    @Override
    public void tick() {
        super.tick();
        Document document = this.getLinkedDocument();
        if (document != null) this.updateSave(document);
    }

    private void updateSave(Document document) {
        boolean disabled = !ClientCards.active(this.source) || AssistantClient.state().busy()
                || !TeleportAnchor.validName(this.name.strip());
        Element save = document.getElementById("anchor_rename_save");
        if (save.isDisabled() != disabled) save.setDisabled(disabled);
    }

    private void save() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.name = document.getElementById("anchor_name").getValue();
        this.updateSave(document);
        if (document.getElementById("anchor_rename_save").isDisabled()) return;
        CardButton button = new CardButton("rename." + this.anchor, "system.action.anchor_rename",
                new CardAction.Server(CardEventValue.encode(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_rename"),
                        Map.of("anchor", this.anchor, "name", this.name.strip()))),
                CardButton.Placement.BODY, CardButton.Behavior.REPLACE);
        this.onClose();
        CardController.activate(this.source, button);
    }

    @Override
    public void onClose() {
        super.onClose();
        this.minecraft.setScreen(this.parent);
    }
}
