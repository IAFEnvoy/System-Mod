package com.iafenvoy.system.screen;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.command.client.NotificationVoiceCommands;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.event.client.AssistantRecognitionKeys;
import com.iafenvoy.system.module.client.ClientCardRegistry;
import com.iafenvoy.system.module.client.ClientModuleRegistry;
import com.iafenvoy.system.module.client.ClientModules;
import com.iafenvoy.system.screen.card.CardContext;
import com.iafenvoy.system.screen.card.CardPresentation;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.speech.tts.SpeechText;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Transition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.*;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * Client-thread notification queue; the AUI HUD draws its document without owning a Screen or input focus.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantMessages {
    private static final int MAX_PENDING = 32;
    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static Message current;
    private static AssistantSpeech.Session speech;
    private static boolean speakCurrent;
    private static boolean speechStarted;
    private static int remainingTicks;
    private static Document document;
    private static Message displayedMessage;
    private static Phase phase = Phase.HIDDEN;
    private static long generation = -1;
    private static int displayedPending = -1;
    private static Boolean interactive;
    private static Boolean displayedBusy;
    private static AssistantRecognition.Session recognition;
    private static String recognitionStatus = "idle";

    private AssistantMessages() {
    }

    private enum Phase {
        HIDDEN, EXPANDING, FADING_IN, VISIBLE, FADING_OUT, COLLAPSING
    }

    private record Pending(Message message, boolean speak) {
    }

    // Callbacks come from trusted client code, never from model-generated strings or commands.
    public record Button(String id, String labelKey, boolean requiresIdle, boolean keepOpen, boolean details,
                         Runnable action) {
        public Button(String id, String labelKey, boolean requiresIdle, Runnable action) {
            this(id, labelKey, requiresIdle, false, false, action);
        }

        public Button {
            Objects.requireNonNull(id);
            Objects.requireNonNull(labelKey);
            Objects.requireNonNull(action);
        }

        public String label() {
            return limit(tr(this.labelKey), 80, 1);
        }
    }

    public record Row(String label, Button button, String css) {
    }

    public record CardBody(String text, List<CardPresentation> cards) {
        public CardBody {
            cards = List.copyOf(cards);
        }
    }

    // Visual details and spoken text are separate; direct notifications default to reading their body.
    public record Message(String title, String text, String speechText, List<Button> buttons, int durationTicks,
                          List<AssistantItems.Group> items, List<String> quests, List<Row> rows, Class<?> refreshType,
                          CardBody body) {
        public Message(String title, String text, List<Button> buttons, int durationTicks) {
            this(title, text, text, buttons, durationTicks);
        }

        public Message(String title, String text, String speechText, List<Button> buttons, int durationTicks) {
            this(title, text, speechText, buttons, durationTicks, List.of(), List.of(), List.of(), null, null);
        }

        public Message(String title, String text, String speechText, List<Button> buttons, int durationTicks, List<AssistantItems.Group> items) {
            this(title, text, speechText, buttons, durationTicks, items, List.of(), List.of(), null, null);
        }

        public Message(String title, String text, String speechText, List<Button> buttons, int durationTicks,
                       List<AssistantItems.Group> items, List<String> quests) {
            this(title, text, speechText, buttons, durationTicks, items, quests, List.of(), null, null);
        }

        public Message {
            title = tr("system.ui.assistant");
            text = Objects.requireNonNull(text);
            speechText = limit(Objects.requireNonNull(speechText), 500, 10);
            buttons = List.copyOf(buttons).stream().limit(3).toList();
            items = List.copyOf(items);
            quests = List.copyOf(quests);
            rows = List.copyOf(rows);
            durationTicks = Math.clamp(durationTicks, 20, 1200);
        }
    }

    public static void post(Message message) {
        post(message, true);
    }

    private static void post(Message message, boolean speak) {
        Objects.requireNonNull(message);
        if (sameContent(message, current) || QUEUE.stream().anyMatch(queued -> sameContent(message, queued.message())))
            return;
        if (QUEUE.size() >= MAX_PENDING) QUEUE.removeFirst();
        QUEUE.addLast(new Pending(message, speak));
    }

    public static void result(AgentResult result, boolean review) {
        if (result.silent()) return;
        post(message(result, review), !SpeechText.result(result).isBlank());
    }

    private static Message message(AgentResult result, boolean review) {
        String summary = summary(result);
        List<Button> buttons = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        for (AgentResult.Card card : result.cards()) {
            for (AgentResult.Action action : card.actions()) {
                ClientModuleRegistry.NotificationAction policy = ClientModules.registry().notificationAction(action);
                Button button = new Button(action.moduleId() + ":" + action.call().value(), action.labelKey(), true, policy.keepOpen(), policy.details(),
                        () -> {
                            if (policy.openWorkspace())
                                AssistantClient.open(false, "");
                            AssistantClient.send(action, "");
                        });
                // Feature list rows do not consume the limited generic action footer.
                String rowCss = ClientModules.registry().cards().notificationRows(card);
                if (rowCss != null)
                    rows.add(new Row(actionLabel(card, action), button, rowCss));
                else if (buttons.size() < 2) buttons.add(button);
            }
        }
        if (review && buttons.stream().noneMatch(Button::details))
            buttons.add(new Button("details", "system.ui.message_details", false, () -> {
                AssistantClientState.ChatEntry entry = AssistantClient.state().current();
                AssistantClient.open(entry != null && entry.result() == result && AssistantClient.simpleMode(), "");
            }));
        String speechText = SpeechText.result(result);
        Class<?> refreshType = ClientModules.registry().cards().refreshes(result).stream()
                .map(ClientCardRegistry.Refresh::type).findFirst().orElse(null);
        CardBody body = new CardBody(preamble(result), result.cards().stream().map(card ->
                ClientModules.registry().cards().presentation(card, result.messageKey(), CardContext.Mode.NOTIFICATION, 0)).toList());
        return new Message(tr("system.ui.assistant"), summary, speechText, buttons, 160, List.of(), SpeechText.quests(result), rows, refreshType, body);
    }

    public static void refresh(AgentResult result) {
        for (ClientCardRegistry.Refresh refresh : ClientModules.registry().cards().refreshes(result)) {
            QUEUE.removeIf(pending -> pending.message().refreshType() == refresh.type());
            if (current != null && current.refreshType() == refresh.type()) {
                if (!refresh.visible()) dismiss(current);
                else {
                    stopSpeech();
                    current = message(refresh.result(), false);
                    speakCurrent = false;
                    speechStarted = true;
                    remainingTicks = current.durationTicks();
                    if (document != null && !document.isDisposed()) renderMessage();
                }
            } else if (refresh.visible()) post(message(refresh.result(), false), false);
        }
    }

    private static String preamble(AgentResult result) {
        String custom = ClientModules.registry().cards().summary(result);
        if (custom != null) return custom;
        return tr(result.messageKey()) + (result.text().isBlank() ? "" : "\n" + result.text());
    }

    public static String summary(AgentResult result) {
        StringBuilder summary = new StringBuilder(preamble(result));
        if (!result.cards().isEmpty() && !result.messageKey().equals("system.result.history")) {
            AgentResult.Card card = result.cards().getFirst();
            if (!card.titleKey().equals(result.messageKey())) summary.append("\n").append(tr(card.titleKey()));
            String data = formatCard(card, false);
            if (!data.isBlank()) summary.append("\n").append(data);
        }
        return summary.toString();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        boolean chatting = minecraft.screen instanceof ChatScreen;
        // Chat releases the vanilla cursor; all other screens hide this HUD to avoid overlapping controls.
        if (minecraft.screen != null && !chatting || minecraft.options.hideGui) {
            unmount();
            return;
        }
        if (document == null || document.isDisposed()) {
            unmount();
            if (current == null) nextMessage();
            if (current == null) return;
            try {
                AssistantPages.prepare("system/assistant-message.html");
                document = Document.create("system/assistant-message.html");
                if (document == null) throw new IllegalStateException("Missing assistant message template");
            } catch (RuntimeException error) {
                // A broken resource pack must not crash gameplay or retry the same notice every tick.
                SystemMod.LOGGER.warn("Unable to create assistant HUD notification", error);
                minecraft.player.sendSystemMessage(Component.literal(current.title() + "\n" + current.text()));
                current = null;
                unmount();
                return;
            }
        }
        if (generation != document.getRefreshGeneration()) {
            generation = document.getRefreshGeneration();
            if (current == null) nextMessage();
            if (current == null) {
                unmount();
                return;
            }
            phase = Phase.HIDDEN;
            displayedMessage = null;
            bindMouseEvents();
            interactive = null;
            displayedBusy = null;
            displayedPending = -1;
            updateInteraction(chatting);
            // Establish hidden content and a collapsed panel before starting either transition.
            document.commitPendingStyleRecalcForRender();
            renderMessage();
            AssistantPages.commit(document);
        }
        advanceAnimation(chatting);
        if (document == null) return;
        updateRecognition();
        updateInteraction(chatting);
        if (displayedPending != QUEUE.size()) {
            displayedPending = QUEUE.size();
            text(document.getElementById("notification_pending"), displayedPending == 0 ? ""
                    : Component.translatable("system.ui.message_pending", displayedPending).getString());
        }
        if (phase == Phase.VISIBLE && !chatting && (recognition == null || recognition.finished())
                && !(current.buttons().stream().anyMatch(Button::keepOpen) && AssistantClient.state().busy()))
            remainingTicks--;
    }

    private static void nextMessage() {
        stopRecognition();
        stopSpeech();
        Pending next = QUEUE.pollFirst();
        current = next == null ? null : next.message();
        speakCurrent = next != null && next.speak();
        speechStarted = false;
        remainingTicks = current == null ? 0 : current.durationTicks();
    }

    private static void renderMessage() {
        displayedMessage = current;
        text(document.getElementById("notification_title"), current.title());
        text(document.getElementById("notification_text"), current.body() == null ? current.text() : current.body().text());
        Element cards = document.getElementById("notification_cards");
        cards.setInnerHTML("");
        if (current.body() != null)
            for (CardPresentation card : current.body().cards()) cards.appendChild(card.render(document));
        cards.setInlineStyleProperty("display", cards.children.isEmpty() ? "none" : "block");
        renderRows();
        AssistantItems.render(document, document.getElementById("notification_items"), current.items(), true);
        renderButtons();
        interactive = null;
        displayedBusy = null;
        displayedPending = -1;
    }

    private static void animate(Phase next, boolean chatting) {
        Phase previous = phase;
        phase = next;
        interactive = null;
        updateInteraction(chatting);
        document.commitPendingStyleRecalcForRender();
        if (next == Phase.EXPANDING || next == Phase.FADING_IN)
            startSpeech(previous == Phase.HIDDEN && next == Phase.EXPANDING);
    }

    private static void startSpeech(boolean playCue) {
        if (speechStarted || current == null) return;
        speechStarted = true;
        // Only the initial expansion plays a cue; swaps and interrupted collapses keep narration direct.
        if (playCue || speakCurrent) {
            speech = new AssistantSpeech.Session();
            if (playCue)
                speech.notification(speakCurrent ? current.speechText() : "", speakCurrent ? current.quests() : List.of());
            else speech.say(current.speechText(), current.quests());
        }
    }

    private static void advanceAnimation(boolean chatting) {
        switch (phase) {
            case HIDDEN -> animate(Phase.EXPANDING, chatting);
            case EXPANDING -> {
                if (!Transition.isActive(document.getElementById("workspace"))) animate(Phase.FADING_IN, chatting);
            }
            case FADING_IN -> {
                if (!Transition.isActive(document.getElementById("notification_content")))
                    animate(Phase.VISIBLE, chatting);
            }
            case VISIBLE -> {
                if (remainingTicks <= 0) dismiss(current);
            }
            case FADING_OUT -> {
                if (Transition.isActive(document.getElementById("notification_content"))) return;
                // Replace content only at zero opacity; consecutive notices keep the panel expanded.
                nextMessage();
                if (current == null) animate(Phase.COLLAPSING, chatting);
                else {
                    renderMessage();
                    animate(Phase.FADING_IN, chatting);
                }
            }
            case COLLAPSING -> {
                if (!QUEUE.isEmpty()) {
                    nextMessage();
                    renderMessage();
                    animate(Phase.EXPANDING, chatting);
                } else if (!Transition.isActive(document.getElementById("workspace"))) unmount();
            }
        }
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && !(event.getNewScreen() instanceof ChatScreen)) unmount();
        else updateInteraction(event.getNewScreen() instanceof ChatScreen);
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof ChatScreen) updateInteraction(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tooltip(ScreenEvent.Render.Foreground event) {
        if (document == null || !canInteract(current) || !(event.getScreen() instanceof ChatScreen))
            return;
        if (AssistantItems.tooltip(document, event.getGuiGraphics(), event.getMouseX(), event.getMouseY()))
            // AUI deduplicates this submission; vanilla then flushes tooltips/IME once above the HUD.
            ApricityGuiLayers.submitUi(event.getGuiGraphics());
    }

    private static void renderRows() {
        Message shown = current;
        Element choices = document.getElementById("notification_choices");
        if (choices == null) return;
        choices.setInnerHTML("");
        choices.scrollTop = 0;
        choices.targetScrollTop = 0;
        choices.setInlineStyleProperty("display", shown.rows().isEmpty() ? "none" : "flex");
        for (Row row : shown.rows()) {
            Element button = element(document, "button", row.css(), row.label());
            button.addEventListener("click", event -> {
                if (canInteract(shown)) activate(shown, row.button());
            });
            choices.appendChild(button);
        }
    }

    private static void renderButtons() {
        Message shown = current;
        Element actions = document.getElementById("notification_actions");
        if (actions == null) return;
        actions.setInnerHTML("");
        for (Button option : buttons(shown)) {
            Element button = element(document, "button", option.id().equals("confirm") ? "primary" : "notification_action", option.label());
            button.addEventListener("click", event -> {
                if (canInteract(shown)) activate(shown, option);
            });
            actions.appendChild(button);
        }
    }

    private static List<Button> buttons(Message shown) {
        List<Button> buttons = new ArrayList<>(shown.buttons());
        buttons.add(new Button("confirm", "system.ui.message_confirm", false, () -> {
        }));
        return buttons;
    }

    private static List<Button> voiceButtons(Message shown) {
        List<Button> buttons = new ArrayList<>(shown.rows().stream().map(Row::button).toList());
        buttons.addAll(buttons(shown));
        return buttons;
    }

    private static void activate(Message shown, Button option) {
        if (current != shown || option.requiresIdle() && AssistantClient.state().busy()) return;
        if (option.keepOpen()) {
            stopRecognition();
            stopSpeech();
            remainingTicks = shown.durationTicks();
        } else dismiss(shown);
        option.action().run();
    }

    public static boolean hasVoiceTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        return phase == Phase.VISIBLE && current != null && displayedMessage == current
                && document != null && !document.isDisposed() && !minecraft.options.hideGui
                && (minecraft.screen == null || minecraft.screen instanceof ChatScreen);
    }

    public static AssistantRecognition.Session startRecognition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!hasVoiceTarget() || recognition != null && !recognition.finished()) return null;
        if (!RecognitionConfig.INSTANCE.get().enabled()) {
            minecraft.setScreen(new AssistantSettingsScreen(minecraft.screen, AssistantSettingsScreen.Tab.ASR));
            return null;
        }
        stopRecognition();
        try {
            AssistantRecognition.validate();
        } catch (SpeechException error) {
            recognitionStatus = error.code();
            return null;
        }
        stopSpeech();
        Message shown = current;
        Screen owner = minecraft.screen;
        NotificationVoiceCommands.prepare(voiceButtons(shown), minecraft.options.languageCode.split("_", 2)[0]);
        recognition = new AssistantRecognition.Session(() -> current == shown && hasVoiceTarget()
                && minecraft.screen == owner && minecraft.isWindowActive(), text -> recognized(shown, text));
        recognition.start();
        return recognition;
    }

    private static void recognized(Message shown, String text) {
        if (current != shown || !hasVoiceTarget()) return;
        stopRecognition();
        String language = Minecraft.getInstance().options.languageCode.split("_", 2)[0];
        List<Button> matches = NotificationVoiceCommands.match(text, voiceButtons(shown), language);
        if (matches.size() == 1) {
            Button button = matches.getFirst();
            if (button.requiresIdle() && AssistantClient.state().busy()) recognitionStatus = "busy";
            else activate(shown, button);
            return;
        }
        // Missing or ambiguous buttons become ordinary input, never a guessed notification action.
        boolean busy = AssistantClient.state().busy();
        AssistantClient.open(AssistantClient.simpleMode(), busy ? text : "");
        if (!busy) AssistantClient.submitText(text);
    }

    private static void updateRecognition() {
        if (recognition != null && !recognition.active()) stopRecognition();
        Element label = document.getElementById("notification_voice");
        if (label == null) return;
        String status = recognition == null ? recognitionStatus : recognition.status();
        boolean active = recognition != null && !recognition.finished();
        String partial = active ? recognition.intermediate() : "";
        String value = status.equals("idle") ? "" : tr("system.asr.status." + status)
                + (partial.isBlank() ? "" : " · " + limit(partial, 160, 1));
        label.setInlineStyleProperty("display", value.isBlank() ? "none" : "block");
        if (!value.equals(label.getTextContent())) text(label, value);
    }

    private static void stopRecognition() {
        if (recognition != null) recognition.close();
        recognition = null;
        recognitionStatus = "idle";
    }

    private static void bindMouseEvents() {
        Document mounted = document;
        Element panel = mounted.getElementById("workspace");
        if (panel == null) return;
        // Only the visible panel owns mouse input; document events also include clicks outside the HUD.
        for (String type : List.of("mousedown", "mouseup", "wheel"))
            panel.addEventListener(type, event -> {
                if (document == mounted && canInteract(displayedMessage) && event instanceof MouseEvent mouse) {
                    mouse.consumeNative();
                    mounted.clearFocus();
                }
            });
    }

    private static boolean canInteract(Message shown) {
        Minecraft minecraft = Minecraft.getInstance();
        return shown != null && document != null && !document.isDisposed()
                && phase == Phase.VISIBLE && current == shown && displayedMessage == shown
                && minecraft.screen instanceof ChatScreen && !minecraft.options.hideGui;
    }

    private static void dismiss(Message shown) {
        if (current != shown) return;
        stopRecognition();
        stopSpeech();
        current = null;
        remainingTicks = 0;
        // A claim can finish while another screen has temporarily unmounted the HUD.
        if (document == null || document.isDisposed()) {
            displayedMessage = null;
            phase = Phase.HIDDEN;
            return;
        }
        animate(Phase.FADING_OUT, Minecraft.getInstance().screen instanceof ChatScreen);
    }

    private static void updateInteraction(boolean chatting) {
        if (document == null || document.isDisposed()) return;
        boolean enabled = chatting && phase == Phase.VISIBLE && current != null && displayedMessage == current;
        // HUD buttons never own keyboard focus, including after native mouse defaults run.
        if (document.getFocusedElement() != null) document.clearFocus();
        if (!enabled) document.setPressedElement(null);
        boolean expanded = phase != Phase.HIDDEN && phase != Phase.COLLAPSING;
        boolean contentVisible = phase == Phase.FADING_IN || phase == Phase.VISIBLE;
        boolean busy = AssistantClient.state().busy();
        if (interactive == null || interactive != enabled || displayedBusy == null || displayedBusy != busy) {
            interactive = enabled;
            displayedBusy = busy;
            document.body.setClassName("notification" + (expanded ? " expanded" : "")
                    + (contentVisible ? " content_visible" : "") + (enabled ? " interactive" : ""));
            Element choices = document.getElementById("notification_choices");
            if (choices != null)
                for (Element button : choices.children) {
                    if (!enabled || busy) button.setAttribute("disabled", "");
                    else button.removeAttribute("disabled");
                }
            Element actions = document.getElementById("notification_actions");
            if (actions != null)
                for (int index = 0; index < actions.children.size(); index++) {
                    Element button = actions.children.get(index);
                    boolean disabled = !enabled || index < current.buttons().size() && current.buttons().get(index).requiresIdle() && busy;
                    if (disabled) button.setAttribute("disabled", "");
                    else button.removeAttribute("disabled");
                }
        }
        String hint = chatting ? tr("system.ui.message_interacting")
                : Component.translatable("system.ui.message_interact", Minecraft.getInstance().options.keyChat.getTranslatedKeyMessage()).getString();
        if (RecognitionConfig.INSTANCE.get().enabled())
            hint += " · " + Component.translatable("system.ui.message_voice_hint",
                    AssistantRecognitionKeys.translatedKey()).getString();
        Element label = document.getElementById("notification_hint");
        if (label != null && !hint.equals(label.getTextContent())) text(label, hint);
    }

    public static void clear() {
        stopSpeech();
        QUEUE.clear();
        current = null;
        remainingTicks = 0;
        unmount();
    }

    private static void unmount() {
        stopRecognition();
        stopSpeech();
        if (document != null) document.remove();
        document = null;
        displayedMessage = null;
        phase = Phase.HIDDEN;
        generation = -1;
        displayedPending = -1;
        interactive = null;
        displayedBusy = null;
    }

    private static void stopSpeech() {
        if (speech != null) speech.close();
        speech = null;
    }

    private static boolean sameContent(Message first, Message second) {
        if (second == null || !first.title().equals(second.title()) || !first.text().equals(second.text()) || !first.speechText().equals(second.speechText())
                || !first.items().equals(second.items()) || !first.quests().equals(second.quests())
                || first.durationTicks() != second.durationTicks() || first.buttons().size() != second.buttons().size()
                || first.rows().size() != second.rows().size() || first.refreshType() != second.refreshType()
                || !Objects.equals(first.body(), second.body()))
            return false;
        for (int index = 0; index < first.rows().size(); index++) {
            Row left = first.rows().get(index), right = second.rows().get(index);
            if (!left.label().equals(right.label()) || !left.css().equals(right.css()) || !sameButton(left.button(), right.button()))
                return false;
        }
        for (int index = 0; index < first.buttons().size(); index++) {
            Button left = first.buttons().get(index), right = second.buttons().get(index);
            if (!sameButton(left, right)) return false;
        }
        return true;
    }

    private static boolean sameButton(Button left, Button right) {
        return left.id().equals(right.id()) && left.label().equals(right.label()) && left.requiresIdle() == right.requiresIdle()
                && left.keepOpen() == right.keepOpen() && left.details() == right.details();
    }

    private static String limit(String value, int codePoints, int lines) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        int end = normalized.offsetByCodePoints(0, Math.min(codePoints, normalized.codePointCount(0, normalized.length())));
        for (int index = 0, count = 1; index < end; index++)
            if (normalized.charAt(index) == '\n' && ++count > lines) {
                end = index;
                break;
            }
        return end < normalized.length() ? normalized.substring(0, end).stripTrailing() + "…" : normalized;
    }
}
