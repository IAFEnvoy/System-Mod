package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.data.AiStatus;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * The composer owns one small menu; permissions and selection are acknowledged by the server.
 */
final class ConversationModeSelector {
    private final Document document;
    private final Element root, trigger, menu;
    private final Map<ConversationMode, Element> options = new EnumMap<>(ConversationMode.class);
    private final Map<ConversationMode, Element> reasons = new EnumMap<>(ConversationMode.class);
    private boolean open;
    private AiStatus displayedStatus;
    private boolean displayedBusy;

    ConversationModeSelector(Document document) {
        this.document = document;
        this.root = document.getElementById("chat_mode_selector");
        this.trigger = document.getElementById("chat_mode_trigger");
        this.menu = document.getElementById("chat_mode_menu");
        this.trigger.setAttribute("aria-label", tr("system.chat_mode.select"));
        this.trigger.addEventListener("click", unused1 -> {
            if (!this.trigger.isDisabled()) this.open(!this.open);
        });
        for (ConversationMode mode : ConversationMode.values()) {
            // Retain initialized instances: AUI replaces raw tags when they enter the document.
            Element option = Element.init(element(document, "button", "chat_mode_option", ""));
            option.setAttribute("type", "button");
            option.setAttribute("role", "menuitemradio");
            option.setAttribute("aria-checked", "false");
            option.appendChild(element(document, "span", "chat_mode_name", tr(mode.labelKey())));
            option.appendChild(element(document, "span", "chat_mode_description", tr(mode.labelKey() + ".description")));
            Element reason = Element.init(element(document, "span", "chat_mode_reason", ""));
            option.appendChild(reason);
            this.options.put(mode, option);
            this.reasons.put(mode, reason);
            option.addEventListener("click", unused2 -> {
                if (option.isDisabled() || !AssistantClient.state().allowsMode(mode)) return;
                this.open(false);
                AssistantClient.selectMode(mode);
                document.getElementById("message").focus();
            });
            this.menu.appendChild(option);
        }
        document.addEventListener("click", event -> {
            if (this.open && event.target instanceof Element target && !this.root.contains(target)) this.open(false);
        });
        this.root.addEventListener("keydown", event -> {
            if (event instanceof KeyEvent key) this.key(key);
        }, true);
        this.open(false);
    }

    void update() {
        AssistantClientState state = AssistantClient.state();
        boolean busy = state.busy() || state.serverState().system().isEmpty();
        if (state.serverState().ai().equals(this.displayedStatus) && busy == this.displayedBusy) return;
        this.displayedStatus = state.serverState().ai();
        this.displayedBusy = busy;
        Element label = this.document.getElementById("chat_mode_label");
        text(label, tr(state.mode().labelKey()));
        this.trigger.setAttribute("title", tr(state.mode().labelKey() + ".description"));
        if (this.trigger.isDisabled() != busy) this.trigger.setDisabled(busy);
        for (ConversationMode mode : ConversationMode.values()) {
            Element option = this.options.get(mode);
            boolean allowed = state.allowsMode(mode);
            boolean selected = state.mode() == mode;
            String checked = Boolean.toString(selected);
            if (!checked.equals(option.getAttribute("aria-checked"))) option.setAttribute("aria-checked", checked);
            if (option.isDisabled() == allowed) option.setDisabled(!allowed);
            AiStatus permissions = state.serverState().ai();
            String reason = allowed ? "" : !permissions.modelConfigured() ? tr("system.chat_mode.model_missing")
                    : !permissions.allowAi() ? tr("system.chat_mode.ai_denied")
                    : permissions.modelCallsPerDay() == 0 ? tr("system.chat_mode.no_calls")
                    : mode.chats() && !permissions.allowFreeChat() ? tr("system.chat_mode.chat_denied")
                    : Component.translatable("system.chat_mode.mode_limited", Component.translatable(permissions.maxMode().labelKey())).getString();
            text(this.reasons.get(mode), reason);
            display(this.reasons.get(mode), allowed ? "none" : "block");
        }
        if (busy) this.open(false);
    }

    private void open(boolean open) {
        this.open = open;
        display(this.menu, open ? "flex" : "none");
        String expanded = Boolean.toString(open);
        if (!expanded.equals(this.trigger.getAttribute("aria-expanded"))) this.trigger.setAttribute("aria-expanded", expanded);
    }

    private void key(KeyEvent event) {
        if (event.keyCode == GLFW.GLFW_KEY_ESCAPE && this.open) {
            event.preventDefault();
            event.stopPropagation();
            this.open(false);
            this.trigger.focus();
        } else if ((event.keyCode == GLFW.GLFW_KEY_DOWN || event.keyCode == GLFW.GLFW_KEY_UP) && !this.trigger.isDisabled()) {
            event.preventDefault();
            event.stopPropagation();
            this.open(true);
            ConversationMode[] modes = ConversationMode.values();
            int current = this.stateIndex(modes);
            int direction = event.keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            for (int offset = 1; offset <= modes.length; offset++) {
                Element next = this.options.get(modes[Math.floorMod(current + direction * offset, modes.length)]);
                if (!next.isDisabled()) {
                    next.focus();
                    break;
                }
            }
        }
    }

    private int stateIndex(ConversationMode[] modes) {
        Element focused = this.document.getFocusedElement();
        for (int index = 0; index < modes.length; index++) if (this.options.get(modes[index]) == focused) return index;
        return AssistantClient.state().mode().ordinal();
    }
}
