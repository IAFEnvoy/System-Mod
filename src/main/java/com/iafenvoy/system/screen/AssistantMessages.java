package com.iafenvoy.system.screen;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.*;
import com.iafenvoy.system.card.client.CardController;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.command.client.NotificationVoiceCommands;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.event.client.AssistantRecognitionKeys;
import com.iafenvoy.system.module.client.ClientMessage;
import com.iafenvoy.system.screen.card.CardPageState;
import com.iafenvoy.system.screen.card.CardPresentation;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
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
    private static final Deque<Message> BACK = new ArrayDeque<>();
    private static CardRef navigationSource;
    private static Message current;
    private static Pending replacement;
    private static AssistantSpeech.Session speech;
    private static boolean speakCurrent;
    private static boolean speechStarted;
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

    // Callbacks are bound to the current card version by the shared controller.
    public record Button(String id, String labelKey, boolean requiresIdle, Runnable action) {
        public String label() {
            return tr(this.labelKey);
        }
    }

    private record Message(String text, String speechText, List<Button> buttons, UUID cardId, AgentResult result,
                           CardPageState pages) {
        private Message {
            buttons = List.copyOf(buttons);
        }
    }

    private static void post(Message message, boolean speak) {
        Objects.requireNonNull(message);
        if (sameContent(message, current) || replacement != null && sameContent(message, replacement.message())
                || QUEUE.stream().anyMatch(queued -> sameContent(message, queued.message())))
            return;
        if (QUEUE.size() >= MAX_PENDING) QUEUE.removeFirst();
        QUEUE.addLast(new Pending(message, speak));
    }

    public static void result(AgentResult result) {
        if (result.silent()) return;
        for (CardUpdate card : result.cards()) {
            if (card.mode() != CardUpdate.Mode.SHOW || card.data().isEmpty()) continue;
            Message message = message(result.withPresentation(result.messageKey(), result.text(), List.of(card), false));
            post(message, !message.speechText().isBlank());
        }
    }

    /**
     * Direct action replies replace the active slot; a dismissed source cannot reopen or displace another notice.
     */
    public static void reply(AgentResult result, CardRef source) {
        // A local back action may already be fading this source out in favor of its retained parent.
        boolean active = current != null && replacement == null && source.id().equals(current.cardId());
        Message parent = active && source.equals(navigationSource) ? current : null;
        navigationSource = null;
        refresh(result);
        if (!active || result.silent()) return;
        List<Pending> replies = result.cards().stream()
                .filter(card -> card.mode() == CardUpdate.Mode.SHOW && card.data().isPresent())
                .map(card -> message(result.withPresentation(result.messageKey(), result.text(), List.of(card), false)))
                .map(message -> new Pending(message, !message.speechText().isBlank())).toList();
        if (replies.isEmpty()) return;
        if (parent != null && ClientCards.active(ClientCards.latest(parent.result()).cards().getFirst().ref()))
            BACK.addLast(parent);
        replacement = replies.getFirst();
        // Extra reply cards follow the replacement without evicting notices that were already waiting.
        int count = Math.min(replies.size() - 1, Math.max(0, MAX_PENDING - QUEUE.size()));
        for (int index = count; index > 0; index--) QUEUE.addFirst(replies.get(index));
        stopRecognition();
        stopSpeech();
        if (document == null || document.isDisposed() || displayedMessage == null) {
            nextMessage();
            phase = Phase.HIDDEN;
        } else animate(Phase.FADING_OUT, Minecraft.getInstance().screen instanceof ChatScreen);
    }

    private static Message message(AgentResult result) {
        return message(result, new CardPageState());
    }

    private static Message message(AgentResult result, CardPageState pages) {
        ClientMessage content = CardPresentation.message(result);
        List<Button> buttons = new ArrayList<>();
        CardUpdate card = result.cards().getFirst();
        for (ClientCards.Choice choice : ClientCards.choices(result)) {
            CardButton button = choice.button();
            if (button.placement() == CardButton.Placement.FOOTER) buttons.add(button(choice));
        }
        return new Message(content.text(), content.speech(), buttons, card.ref().id(), result, pages);
    }

    private static Button button(ClientCards.Choice choice) {
        CardButton button = choice.button();
        return new Button(button.id(), button.labelKey(), button.action() instanceof CardAction.Server,
                () -> CardController.activate(choice.source(), button, true));
    }

    public static void refresh(AgentResult result) {
        for (CardUpdate update : result.cards()) {
            if (update.mode() == CardUpdate.Mode.SHOW) continue;
            if (replacement != null && update.ref().id().equals(replacement.message().cardId()))
                replacement = update.mode() == CardUpdate.Mode.REMOVE ? null
                        : new Pending(message(ClientCards.latest(replacement.message().result()), replacement.message().pages()), false);
            List<Message> parents = new ArrayList<>(BACK);
            BACK.clear();
            for (Message parent : parents) {
                if (!update.ref().id().equals(parent.cardId())) BACK.addLast(parent);
                else if (update.mode() != CardUpdate.Mode.REMOVE)
                    BACK.addLast(message(ClientCards.latest(parent.result()), parent.pages()));
            }
            List<Pending> queued = new ArrayList<>(QUEUE);
            QUEUE.clear();
            for (Pending pending : queued) {
                if (!update.ref().id().equals(pending.message().cardId())) QUEUE.addLast(pending);
                else if (update.mode() != CardUpdate.Mode.REMOVE)
                    QUEUE.addLast(new Pending(message(ClientCards.latest(pending.message().result()), pending.message().pages()), false));
            }
            if (current == null || !update.ref().id().equals(current.cardId())) continue;
            if (update.mode() == CardUpdate.Mode.REMOVE) dismiss(current);
            else {
                stopRecognition();
                stopSpeech();
                current = message(ClientCards.latest(current.result()), current.pages());
                speakCurrent = false;
                speechStarted = true;
                if (document != null && !document.isDisposed()) renderMessage();
            }
        }
        renderNavigation();
    }

    public static void dismissCard(UUID id) {
        BACK.removeIf(parent -> id.equals(parent.cardId()));
        QUEUE.removeIf(pending -> id.equals(pending.message().cardId()));
        if (replacement != null && id.equals(replacement.message().cardId())) replacement = null;
        if (current != null && id.equals(current.cardId())) dismiss(current);
        renderNavigation();
    }

    public static boolean actionStarted(CardRef source, CardButton.Behavior behavior) {
        if (!hasVoiceTarget() || !source.id().equals(current.cardId())) return false;
        navigationSource = behavior == CardButton.Behavior.PUSH ? source : null;
        if (behavior == CardButton.Behavior.CLOSE) dismiss(current);
        else {
            stopRecognition();
            stopSpeech();
        }
        return true;
    }

    public static String summary(AgentResult result) {
        return CardPresentation.message(result).text();
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
                // AUI draws HUD documents only while no screen is open; this keeps the notice over the chat screen.
                document.setReloadPersistent(true);
            } catch (RuntimeException error) {
                // A broken resource pack must not crash gameplay or retry the same notice every tick.
                SystemMod.LOGGER.warn("Unable to create assistant HUD notification", error);
                minecraft.player.sendSystemMessage(Component.literal(tr("system.ui.assistant") + "\n" + current.text()));
                current = null;
                unmount();
                return;
            }
        }
        if (generation != document.getRefreshGeneration()) {
            generation = document.getRefreshGeneration();
            if (current == null || replacement != null) nextMessage();
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
    }

    private static void nextMessage() {
        stopRecognition();
        stopSpeech();
        // Only a new queued notice starts a new navigation session; replacements remain in the same window.
        if (replacement == null) BACK.clear();
        navigationSource = null;
        Pending next = replacement == null ? QUEUE.pollFirst() : replacement;
        replacement = null;
        current = next == null ? null : next.message();
        speakCurrent = next != null && next.speak();
        speechStarted = false;
    }

    private static void renderMessage() {
        displayedMessage = current;
        // All message details share one scroll viewport; a new message always starts at its top.
        Element scroll = document.getElementById("notification_scroll");
        scroll.scrollTop = 0;
        scroll.targetScrollTop = 0;
        text(document.getElementById("notification_title"), tr("system.ui.assistant"));
        text(document.getElementById("notification_text"), "");
        Element cards = document.getElementById("notification_cards");
        cards.setInnerHTML("");
        Element navigation = element(document, "div", "card_navigation", "");
        navigation.setAttribute("id", "notification_navigation");
        cards.appendChild(navigation);
        renderNavigation();
        for (CardUpdate card : current.result().cards())
            cards.appendChild(CardPresentation.of(card, current.result().messageKey(), CardContext.Mode.NOTIFICATION, 0, current.pages()).render(document));
        cards.setInlineStyleProperty("display", cards.children.isEmpty() ? "none" : "block");
        document.getElementById("notification_choices").setInlineStyleProperty("display", "none");
        document.getElementById("notification_items").setInlineStyleProperty("display", "none");
        renderButtons();
        interactive = null;
        displayedBusy = null;
        displayedPending = -1;
    }

    private static void renderNavigation() {
        if (document == null || document.isDisposed()) return;
        Element navigation = document.getElementById("notification_navigation");
        if (navigation == null) return;
        navigation.setInnerHTML("");
        navigation.setInlineStyleProperty("display", current == null || BACK.isEmpty() ? "none" : "flex");
        if (current == null || BACK.isEmpty()) return;
        Message shown = current;
        Button back = backButton(shown);
        Element button = element(document, "button", "card_action", back.label());
        button.addEventListener("click", event -> {
            if (canInteract(shown)) activate(shown, back);
        });
        navigation.appendChild(button);
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
                speech.notification(speakCurrent ? current.speechText() : "");
            else speech.say(current.speechText());
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
                // Narration completion and elapsed time never advance the notification queue.
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
                if (replacement != null || !QUEUE.isEmpty()) {
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
        // Dismiss is always available, independently of the card's business actions and busy state.
        buttons.add(new Button("confirm", "system.ui.message_confirm", false, () -> dismiss(shown)));
        return buttons;
    }

    private static List<Button> voiceButtons(Message shown) {
        List<Button> buttons = new ArrayList<>();
        if (!BACK.isEmpty()) buttons.add(backButton(shown));
        for (ClientCards.Choice choice : ClientCards.choices(shown.result()))
            if (choice.button().placement() == CardButton.Placement.BODY) buttons.add(button(choice));
        buttons.addAll(buttons(shown));
        return buttons;
    }

    private static Button backButton(Message shown) {
        return new Button("back", "system.ui.notification_back", false, () -> {
            if (current != shown || BACK.isEmpty()) return;
            navigationSource = null;
            stopRecognition();
            stopSpeech();
            Message parent = BACK.removeLast();
            replacement = new Pending(message(ClientCards.latest(parent.result()), parent.pages()), false);
            animate(Phase.FADING_OUT, Minecraft.getInstance().screen instanceof ChatScreen);
        });
    }

    private static void activate(Message shown, Button option) {
        if (current != shown || option.requiresIdle() && AssistantClient.state().busy()) return;
        // CardController owns business-button lifecycle; closing here would lose the reply's source.
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

    public static boolean canActivate(CardRef source) {
        return canInteract(current) && current.result().cards().stream().anyMatch(card -> card.ref().equals(source));
    }

    private static void dismiss(Message shown) {
        if (current != shown) return;
        stopRecognition();
        stopSpeech();
        BACK.clear();
        navigationSource = null;
        current = null;
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
            for (Element button : document.querySelectorAll("#notification_cards button[data-requires-idle]"))
                button.setDisabled(!enabled || busy);
            Element actions = document.getElementById("notification_actions");
            if (actions != null)
                for (int index = 0; index < actions.children.size(); index++) {
                    Element button = actions.children.get(index);
                    boolean disabled = !enabled || current != null && index < current.buttons().size() && current.buttons().get(index).requiresIdle() && busy;
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
        BACK.clear();
        navigationSource = null;
        current = null;
        replacement = null;
        unmount();
    }

    private static void unmount() {
        stopRecognition();
        stopSpeech();
        // Finish an in-flight swap before hiding the document, so reopening cannot restore the old content.
        if (replacement != null) nextMessage();
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
        return second != null && first.cardId().equals(second.cardId());
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
