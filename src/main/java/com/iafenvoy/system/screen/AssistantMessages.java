package com.iafenvoy.system.screen;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.speech.tts.SpeechText;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Transition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
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

    private AssistantMessages() {
    }

    private enum Phase {
        HIDDEN, EXPANDING, FADING_IN, VISIBLE, FADING_OUT, COLLAPSING
    }

    private record Pending(Message message, boolean speak) {
    }

    // Callbacks come from trusted client code, never from model-generated strings or commands.
    public record Button(String id, String label, boolean requiresIdle, Runnable action) {
        public Button {
            Objects.requireNonNull(id);
            label = limit(Objects.requireNonNull(label), 80, 1);
            Objects.requireNonNull(action);
        }
    }

    // Visual details and spoken text are separate; direct notifications default to reading their body.
    public record Message(String title, String text, String speechText, List<Button> buttons, int durationTicks) {
        public Message(String title, String text, List<Button> buttons, int durationTicks) {
            this(title, text, text, buttons, durationTicks);
        }

        public Message {
            title = limit(Objects.requireNonNull(title), 96, 1);
            text = limit(Objects.requireNonNull(text), 360, 6);
            speechText = limit(Objects.requireNonNull(speechText), 500, 10);
            buttons = List.copyOf(buttons).stream().limit(3).toList();
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

    public static void binding(String playerName) {
        post(new Message(tr("system.ui.binding_title"),
                Component.translatable("system.ui.binding_text", playerName).getString(),
                Component.translatable("system.tts.message.binding", playerName).getString(), List.of(
                new Button("open", tr("system.ui.message_open_button"), false,
                        () -> AssistantClient.open(AssistantClient.simpleMode(), ""))), 240));
    }

    public static void result(AgentResult result, boolean review) {
        String summary = summary(result);
        List<Button> buttons = new ArrayList<>();
        for (AgentResult.Card card : result.cards()) {
            for (AgentResult.Action action : card.actions()) {
                if (buttons.size() >= 2) break;
                buttons.add(new Button(action.moduleId() + ":" + action.call().value(), actionLabel(card, action), true,
                        () -> AssistantClient.send(action, "")));
            }
            if (buttons.size() >= 2) break;
        }
        if (review) buttons.add(new Button("details", tr("system.ui.message_details"), false, () -> {
            AssistantClientState.ChatEntry entry = AssistantClient.state().current();
            AssistantClient.open(entry != null && entry.result() == result && AssistantClient.simpleMode(), "");
        }));
        String speechText = SpeechText.result(result);
        post(new Message(tr("system.ui.assistant"), summary, speechText, buttons, 160), !speechText.isBlank());
    }

    public static String summary(AgentResult result) {
        StringBuilder summary = new StringBuilder(tr(result.messageKey()));
        if (!result.text().isBlank()) summary.append("\n").append(result.text());
        if (!result.cards().isEmpty() && !result.messageKey().equals("system.result.history")) {
            AgentResult.Card card = result.cards().getFirst();
            if (!card.titleKey().equals(result.messageKey())) summary.append("\n").append(tr(card.titleKey()));
            String data = formatCard(card);
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
        }
        advanceAnimation(chatting);
        if (document == null) return;
        updateInteraction(chatting);
        if (displayedPending != QUEUE.size()) {
            displayedPending = QUEUE.size();
            text(document.getElementById("notification_pending"), displayedPending == 0 ? ""
                    : Component.translatable("system.ui.message_pending", displayedPending).getString());
        }
        if (phase == Phase.VISIBLE && !chatting) remainingTicks--;
    }

    private static void nextMessage() {
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
        text(document.getElementById("notification_text"), current.text());
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
            if (playCue) speech.notification(speakCurrent ? current.speechText() : "");
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

    private static void renderButtons() {
        Message shown = current;
        Element actions = document.getElementById("notification_actions");
        if (actions == null) return;
        actions.setInnerHTML("");
        for (Button option : shown.buttons()) {
            Element button = element(document, "button", "notification_action", option.label());
            button.addEventListener("click", event -> {
                if (!canInteract(shown) || option.requiresIdle() && AssistantClient.state().busy()) return;
                dismiss(shown);
                option.action().run();
            });
            actions.appendChild(button);
        }
        Element confirm = element(document, "button", "primary", tr("system.ui.message_confirm"));
        confirm.addEventListener("click", event -> {
            if (canInteract(shown)) dismiss(shown);
        });
        actions.appendChild(confirm);
    }

    private static void bindMouseEvents() {
        // Consume only clicks on this panel; vanilla chat typing and clicks outside it keep their defaults.
        for (String type : List.of("mousedown", "mouseup", "wheel"))
            document.addEventListener(type, event -> {
                if (Minecraft.getInstance().screen instanceof ChatScreen && event instanceof MouseEvent mouse) {
                    mouse.consumeNative();
                    if (document != null) document.clearFocus();
                }
            });
    }

    private static boolean canInteract(Message shown) {
        Minecraft minecraft = Minecraft.getInstance();
        return phase == Phase.VISIBLE && current == shown && displayedMessage == shown
                && minecraft.screen instanceof ChatScreen && !minecraft.options.hideGui;
    }

    private static void dismiss(Message shown) {
        if (current != shown) return;
        stopSpeech();
        current = null;
        remainingTicks = 0;
        animate(Phase.FADING_OUT, Minecraft.getInstance().screen instanceof ChatScreen);
    }

    private static void updateInteraction(boolean chatting) {
        if (document == null || document.isDisposed()) return;
        boolean enabled = chatting && phase == Phase.VISIBLE && current != null;
        boolean expanded = phase != Phase.HIDDEN && phase != Phase.COLLAPSING;
        boolean contentVisible = phase == Phase.FADING_IN || phase == Phase.VISIBLE;
        boolean busy = AssistantClient.state().busy();
        if (interactive == null || interactive != enabled || displayedBusy == null || displayedBusy != busy) {
            interactive = enabled;
            displayedBusy = busy;
            document.body.setClassName("notification" + (expanded ? " expanded" : "")
                    + (contentVisible ? " content_visible" : "") + (enabled ? " interactive" : ""));
            document.clearFocus();
            if (!enabled) document.setPressedElement(null);
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
                || first.durationTicks() != second.durationTicks() || first.buttons().size() != second.buttons().size())
            return false;
        for (int index = 0; index < first.buttons().size(); index++) {
            Button left = first.buttons().get(index), right = second.buttons().get(index);
            if (!left.id().equals(right.id()) || !left.label().equals(right.label()) || left.requiresIdle() != right.requiresIdle())
                return false;
        }
        return true;
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
