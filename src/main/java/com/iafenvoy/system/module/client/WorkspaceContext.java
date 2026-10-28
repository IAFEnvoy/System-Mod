package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.sighs.apricityui.init.Document;

import java.util.function.BooleanSupplier;

public record WorkspaceContext(AssistantScreen screen, Document document, BooleanSupplier available) {
    public void labels(String... ids) {
        for (String id : ids) AssistantView.text(this.document.getElementById(id), AssistantView.tr("system.ui." + id));
    }

    public void click(String id, Runnable action) {
        AssistantView.bind(this.document, id, () -> {
            if (this.available.getAsBoolean()) action.run();
            else AssistantClient.local(AgentResult.error("CAPABILITY_UNAVAILABLE"));
        });
    }

    public void show(String page) {
        this.screen.showPage(page);
    }

    public String query(String id) {
        String value = this.document.getElementById(id).getValue();
        return value == null ? "" : value.strip();
    }

    public void input(String id, String placeholder, String submit) {
        this.document.getElementById(id).setAttribute("placeholder", AssistantView.tr(placeholder));
        this.document.getElementById(id).setAttribute("data-submit", submit);
    }
}
