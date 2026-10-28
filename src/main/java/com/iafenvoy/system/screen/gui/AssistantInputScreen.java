package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.client.AssistantCommandSuggestions;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.speech.tts.AssistantSpeech.Session;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Shared single-line composer, local completions and native input-method lifecycle.
 */
public abstract class AssistantInputScreen extends AssistantPageScreen {
    protected String draft;
    private @Nullable AbstractText textInput;
    private @Nullable IMEPreeditOverlay preeditOverlay;
    private @Nullable InputArea inputArea;
    private @Nullable SuggestionState suggestionState;
    private @Nullable SuggestionArea suggestionArea;
    private List<AssistantCommandSuggestions.Suggestion> suggestions = List.of();
    private int selectedSuggestion;
    private @Nullable String dismissedDraft;
    private long compositionEnded;
    private boolean enterDown;
    private @Nullable Session speech;
    private AssistantRecognition.@Nullable Session recognition;
    private String recognitionStatus = "idle";
    private String recognitionDraft = "";
    private String renderedRecognition = "";

    private record InputArea(int left, int top, int right, int bottom, int guiScale) {
    }

    private record SuggestionState(String value, int cursor, int selectionStart, int selectionEnd, long revision,
                                   boolean eligible) {
    }

    private record SuggestionArea(int left, int bottom, int width, int maxHeight) {
    }

    protected AssistantInputScreen(String templatePath, String draft) {
        super(templatePath);
        this.draft = draft == null ? "" : draft.replace('\r', ' ').replace('\n', ' ');
    }

    public final Session speech() {
        if (this.speech == null) this.speech = new Session();
        return this.speech;
    }

    public final void stopSpeech() {
        if (this.speech != null) this.speech.close();
        this.speech = null;
    }

    @Override
    protected void initPage(Document document) {
        this.stopRecognition();
        this.renderedRecognition = "";
        this.clearTextInputFocus();
        this.suggestionState = null;
        this.suggestionArea = null;
        this.suggestions = List.of();
        this.dismissedDraft = null;
        this.enterDown = false;
        for (Element input : document.querySelectorAll("input, textarea")) {
            input.addEventListener("focus", event -> this.syncTextInputFocus());
            input.addEventListener("keydown", event -> {
                if (event instanceof KeyEvent key) this.inputKeyPressed(input, key);
            }, true);
        }
        document.addEventListener("keyup", event -> {
            if (event instanceof KeyEvent key && isEnter(key.keyCode)) this.enterDown = false;
        });
        Element message = document.getElementById("message");
        if (message == null) return;
        message.setValue(this.draft);
        if (message instanceof AbstractText text) text.setSelectionRange(this.draft.length(), this.draft.length());
        message.addEventListener("input", event -> this.updateSuggestions());
        message.focus();
        AssistantView.bind(document, "asr_toggle", this::toggleRecognition);
        AssistantView.bind(document, "asr_cancel", this::stopRecognition);
        AssistantView.text(document.getElementById("asr_cancel"), AssistantView.tr("system.asr.ui.cancel"));
        this.renderRecognition();
        // Blur fires before AUI commits the next focus; polling observes the completed transition.
        this.syncTextInputFocus();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.recognition != null && !this.recognition.finished() && !this.minecraft.isWindowActive())
            this.stopRecognition();
        this.syncTextInputFocus();
        this.updateSuggestions();
        this.renderRecognition();
    }

    protected void sendMessage() {
        Document document = this.getLinkedDocument();
        if (document == null || this.imeOwnsKeys()) return;
        Element message = document.getElementById("message");
        if (message == null) return;
        this.stopRecognition();
        if (!AssistantClient.submitText(message.getValue())) return;
        this.draft = "";
        if (this.minecraft.screen != this) return;
        message.setValue("");
        if (message instanceof AbstractText text) text.setSelectionRange(0, 0);
        message.focus();
        this.updateSuggestions();
    }

    final void toggleRecognition() {
        Document document = this.getLinkedDocument();
        if (document == null || document.getElementById("message") == null) return;
        if (this.recognition != null && !this.recognition.finished()) {
            this.recognition.finish();
            return;
        }
        this.startRecognition();
    }

    public final boolean startRecognition() {
        Document document = this.getLinkedDocument();
        if (document == null || document.getElementById("message") == null || this.imeOwnsKeys()
                || AssistantClient.state().busy() || this.recognition != null && !this.recognition.finished())
            return false;
        if (!RecognitionConfig.INSTANCE.get().enabled()) {
            this.minecraft.setScreen(new AssistantSettingsScreen(this, AssistantSettingsScreen.Tab.ASR));
            return false;
        }
        try {
            AssistantRecognition.validate();
        } catch (SpeechException error) {
            this.recognitionStatus = error.code();
            this.renderRecognition();
            return false;
        }
        this.stopRecognition();
        AssistantSpeech.stop();
        this.recognitionDraft = document.getElementById("message").getValue();
        this.recognition = new AssistantRecognition.Session(() -> this.minecraft.screen == this && this.minecraft.isWindowActive(), this::recognized);
        this.recognition.start();
        this.renderRecognition();
        return true;
    }

    public final AssistantRecognition.Session recognitionSession() {
        return this.recognition;
    }

    public final void recognitionCommand() {
        Document document = this.getLinkedDocument();
        if (document == null || document.getElementById("message") == null) return;
        document.getElementById("message").setValue("");
        this.draft = "";
        this.toggleRecognition();
    }

    private void recognized(String text) {
        Document document = this.getLinkedDocument();
        if (document == null || !(document.getElementById("message") instanceof AbstractText message)) return;
        // A delayed result must never overwrite a draft edited while recognition was in flight.
        if (!message.getValue().equals(this.recognitionDraft)) {
            this.recognitionStatus = "draft_changed";
            Element hint = document.getElementById("asr_status");
            if (hint != null) hint.setAttribute("title", text);
            return;
        }
        String value = (this.recognitionDraft.isBlank() ? "" : this.recognitionDraft + " ") + text.replace('\r', ' ').replace('\n', ' ');
        if (value.length() > 2000) {
            this.recognitionStatus = "length";
            return;
        }
        message.setValue(value);
        message.setSelectionRange(value.length(), value.length());
        message.focus();
        this.draft = value;
        this.recognitionStatus = "done";
        this.updateSuggestions();
        // Reuse normal submission so commands, pending requests and IME guards keep their behavior.
        if (RecognitionConfig.INSTANCE.get().autoSend() && this.minecraft.isWindowActive()) this.sendMessage();
    }

    public final void stopRecognition() {
        if (this.recognition != null) this.recognition.close();
        this.recognition = null;
        this.recognitionStatus = "idle";
        Document document = this.getLinkedDocument();
        if (document != null && document.getElementById("asr_status") != null)
            document.getElementById("asr_status").removeAttribute("title");
    }

    private void renderRecognition() {
        Document document = this.getLinkedDocument();
        if (document == null || document.getElementById("asr_toggle") == null) return;
        boolean active = this.recognition != null && !this.recognition.finished();
        String status = this.recognitionStatus.equals("draft_changed") || this.recognitionStatus.equals("length")
                ? this.recognitionStatus : this.recognition == null ? this.recognitionStatus : this.recognition.status();
        String partial = this.recognition == null ? "" : this.recognition.intermediate();
        String rendered = status + ":" + active + ":" + AssistantClient.state().busy() + ":" + partial;
        if (rendered.equals(this.renderedRecognition)) return;
        this.renderedRecognition = rendered;
        AssistantView.text(document.getElementById("asr_toggle"), AssistantView.tr(active && !status.equals("recognizing")
                ? "system.asr.ui.finish" : "system.asr.ui.record"));
        document.getElementById("asr_toggle").setDisabled(AssistantClient.state().busy() || active && status.equals("recognizing"));
        Element cancel = document.getElementById("asr_cancel");
        if (cancel != null) cancel.setInlineStyleProperty("display", active ? "block" : "none");
        Element hint = document.getElementById("asr_status");
        if (hint != null) {
            hint.setInlineStyleProperty("display", status.equals("idle") ? "none" : "block");
            AssistantView.text(hint, AssistantView.tr("system.asr.status." + status) + (active && !partial.isBlank() ? " · " + partial : ""));
        }
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    private boolean imeOwnsKeys() {
        return this.preeditOverlay != null || this.compositionEnded != 0 && System.nanoTime() - this.compositionEnded < 150_000_000L;
    }

    private void inputKeyPressed(Element input, KeyEvent event) {
        if (this.minecraft.screen != this) return;
        boolean enter = isEnter(event.keyCode);
        boolean completionKey = event.keyCode == GLFW.GLFW_KEY_TAB || event.keyCode == GLFW.GLFW_KEY_UP || event.keyCode == GLFW.GLFW_KEY_DOWN;
        // Native IMEs process these keys first; never let AUI blur or submit while a candidate is confirmed.
        if (this.imeOwnsKeys() && (enter || completionKey || event.keyCode == GLFW.GLFW_KEY_ESCAPE)) {
            event.preventDefault();
            if (enter) this.enterDown = true;
            return;
        }
        if (enter) {
            event.preventDefault();
            if (this.enterDown) return;
            this.enterDown = true;
            Document document = this.getLinkedDocument();
            if (document == null) return;
            if (input == document.getElementById("message")) this.sendMessage();
            else {
                String buttonId = input.getAttribute("data-submit");
                Element button = buttonId == null || buttonId.isEmpty() ? null : document.getElementById(buttonId);
                if (button != null) button.click();
            }
            return;
        }
        Document document = this.getLinkedDocument();
        if (document == null || input != document.getElementById("message")) return;
        this.updateSuggestions();
        if (this.suggestions.isEmpty()) return;
        switch (event.keyCode) {
            case GLFW.GLFW_KEY_TAB -> {
                event.preventDefault();
                this.completeSuggestion(this.selectedSuggestion);
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                event.preventDefault();
                this.selectedSuggestion = Math.floorMod(this.selectedSuggestion + (event.keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), this.suggestions.size());
                this.renderSuggestions();
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                event.preventDefault();
                this.dismissedDraft = input.getValue();
                this.suggestionState = null;
                this.updateSuggestions();
            }
        }
    }

    private void updateSuggestions() {
        Document document = this.getLinkedDocument();
        if (document == null || !(document.getElementById("message") instanceof AbstractText message)) return;
        this.draft = message.getValue();
        boolean eligible = document.getFocusedElement() == message && !this.imeOwnsKeys();
        SuggestionState state = new SuggestionState(this.draft, message.getCursor(), message.getSelectionStart(), message.getSelectionEnd(), AssistantClient.revision(), eligible);
        if (state.equals(this.suggestionState)) return;
        this.suggestionState = state;
        this.suggestions = eligible && message.getCursor() == this.draft.length() && !message.hasSelection() && !this.draft.equals(this.dismissedDraft)
                ? AssistantCommandSuggestions.find(this.draft) : List.of();
        if (!this.draft.equals(this.dismissedDraft)) this.dismissedDraft = null;
        this.selectedSuggestion = 0;
        this.renderSuggestions();
    }

    private void renderSuggestions() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        Element popup = document.getElementById("command_suggestions");
        if (popup == null) return;
        popup.setInnerHTML("");
        // Rebuild off-screen; the render phase anchors the independent overlay before showing it.
        popup.setInlineStyleProperty("display", "none");
        this.suggestionArea = null;
        if (this.suggestions.isEmpty()) return;
        int start = Math.clamp(this.selectedSuggestion - 2, 0, Math.max(0, this.suggestions.size() - 6));
        for (int i = start; i < Math.min(start + 6, this.suggestions.size()); i++) {
            int index = i;
            Element row = AssistantView.element(document, "button", i == this.selectedSuggestion ? "command_option selected" : "command_option", this.suggestions.get(i).label());
            row.addEventListener("mousedown", event -> {
                event.preventDefault();
                // AUI focuses buttons before mousedown; restore the editor before the IME focus poll.
                document.getElementById("message").focus();
            });
            row.addEventListener("click", event -> this.completeSuggestion(index));
            popup.appendChild(row);
        }
        popup.appendChild(AssistantView.element(document, "div", "command_hint", AssistantView.tr("system.ui.command_hint")));
    }

    private void positionSuggestions(Document document) {
        if (this.suggestions.isEmpty()) return;
        Element popup = document.getElementById("command_suggestions");
        Element message = document.getElementById("message");
        if (popup == null || message == null) return;
        Element.DOMRect bounds = message.getBoundingClientRect();
        int viewportWidth = document.getViewport().layoutWidth();
        int viewportHeight = document.getViewport().layoutHeight();
        int maxHeight = Math.max(0, (int) Math.floor(bounds.top - 12));
        if (document.getFocusedElement() != message || this.imeOwnsKeys() || bounds.width <= 0 || bounds.height <= 0 || maxHeight == 0) {
            popup.setInlineStyleProperty("display", "none");
            this.suggestionArea = null;
            return;
        }
        int width = Math.clamp((int) Math.round(bounds.width), 1, Math.max(1, viewportWidth - 12));
        int left = Math.clamp((int) Math.floor(bounds.left), 6, Math.max(6, viewportWidth - width - 6));
        int bottom = Math.max(0, (int) Math.ceil(viewportHeight - bounds.top + 6));
        SuggestionArea area = new SuggestionArea(left, bottom, width, maxHeight);
        if (area.equals(this.suggestionArea)) return;
        this.suggestionArea = area;
        // All values use the owning document's CSS pixels, including after GUI scale or AUI zoom changes.
        popup.setInlineStyleProperty("left", left + "px");
        popup.setInlineStyleProperty("bottom", bottom + "px");
        popup.setInlineStyleProperty("width", width + "px");
        popup.setInlineStyleProperty("max-height", maxHeight + "px");
        popup.setInlineStyleProperty("display", "flex");
    }

    private void completeSuggestion(int index) {
        Document document = this.getLinkedDocument();
        if (document == null || this.imeOwnsKeys() || index < 0 || index >= this.suggestions.size()
                || !(document.getElementById("message") instanceof AbstractText message)) return;
        String value = this.suggestions.get(index).value();
        message.setRangeText(value, 0, message.getValue().length(), "end");
        message.focus();
        this.draft = message.getValue();
        this.dismissedDraft = null;
        this.suggestionState = null;
        this.updateSuggestions();
    }

    private void syncTextInputFocus() {
        Document document = this.getLinkedDocument();
        Element focused = document == null ? null : document.getFocusedElement();
        AbstractText next = focused instanceof AbstractText text && text.canEditText() ? text : null;
        if (next == this.textInput) return;
        boolean wasTyping = this.textInput != null;
        this.textInput = next;
        this.preeditOverlay = null;
        this.compositionEnded = 0;
        this.inputArea = null;
        // Moving between editable controls keeps the native IME session enabled.
        if (wasTyping == (next == null)) this.minecraft.onTextInputFocusChange(this, next != null);
    }

    private void clearTextInputFocus() {
        this.preeditOverlay = null;
        this.compositionEnded = 0;
        this.inputArea = null;
        if (this.textInput == null) return;
        this.textInput = null;
        this.minecraft.onTextInputFocusChange(this, false);
    }

    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        if (event == null && this.preeditOverlay != null) this.compositionEnded = System.nanoTime();
        this.preeditOverlay = event != null && this.textInput != null ? new IMEPreeditOverlay(event, this.font, this.font.lineHeight + 1) : null;
        this.updateSuggestions();
        return this.textInput != null || super.preeditUpdated(event);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.syncTextInputFocus();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        // Geometry queries can populate layout caches; always resolve them in this document's viewport.
        try (Document.ContextScope ignored = Document.withContext(document)) {
            this.positionSuggestions(document);
            AssistantPages.commit(document);
            AssistantItems.tooltip(document, graphics, mouseX, mouseY);
            if (this.textInput == null) return;
            Element.DOMRect bounds = this.textInput.getBoundingClientRect();
            if (bounds.width <= 0 || bounds.height <= 0) return;
            Position start = document.documentToGuiPosition(new Position(bounds.left, bounds.top));
            Position end = document.documentToGuiPosition(new Position(bounds.right, bounds.bottom));
            int left = Math.clamp((int) Math.floor(start.x), 0, Math.max(0, this.width - 1));
            int top = Math.clamp((int) Math.floor(start.y), 0, Math.max(0, this.height - 1));
            int right = Math.clamp((int) Math.ceil(end.x), left + 1, Math.max(left + 1, this.width));
            int bottom = Math.clamp((int) Math.ceil(end.y), top + 1, Math.max(top + 1, this.height));
            if (this.preeditOverlay != null) {
                // The vanilla overlay sets its own hot area; a second rectangle here makes IMEs oscillate.
                this.inputArea = null;
                this.preeditOverlay.updateInputPosition(left, Math.max(top, bottom - this.font.lineHeight - 1));
                graphics.setPreeditOverlay(this.preeditOverlay);
            } else {
                InputArea area = new InputArea(left, top, right, bottom, this.minecraft.getWindow().getGuiScale());
                if (!area.equals(this.inputArea)) {
                    this.minecraft.textInputManager().setTextInputArea(left, top, right, bottom);
                    this.inputArea = area;
                }
            }
        }
    }

    @Override
    public void onClose() {
        this.stopRecognition();
        this.stopSpeech();
        this.clearTextInputFocus();
        super.onClose();
    }

    @Override
    public void removed() {
        this.stopRecognition();
        this.stopSpeech();
        this.clearTextInputFocus();
        super.removed();
    }
}
