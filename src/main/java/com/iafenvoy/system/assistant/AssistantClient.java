package com.iafenvoy.system.assistant;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.assistant.chat.ConversationMode;
import com.iafenvoy.assistant.request.*;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.command.client.AssistantClientCommands;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.data.ClientState;
import com.iafenvoy.system.data.History;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.card.CardReference;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.client.ClientCards;
import com.iafenvoy.system.data.input.SpaceDepositInput;
import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.iafenvoy.system.data.output.RewardChoiceOutput;
import com.iafenvoy.system.data.output.SpaceInventoryOutput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.module.client.ClientItemSearch;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.network.NetworkManager;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.iafenvoy.system.screen.AssistantCardDisplay;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.emotion.EmotionHudState;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-thread navigation and effects; conversation updates replace one immutable state object.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantClient {
    private static AssistantClientState state = AssistantClientState.EMPTY;
    // This view invalidation counter stays monotonic across EMPTY resets.
    private static long revision;

    private AssistantClient() {
    }

    public static AssistantClientState state() {
        return state;
    }

    public static SystemDefinition system() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? SystemDefinition.DEFAULT : state.serverState().system()
                .flatMap(id -> connection.registryAccess().lookupOrThrow(SystemResourceKeys.SYSTEM).getOptional(id)).orElse(SystemDefinition.DEFAULT);
    }

    public static long revision() {
        return revision;
    }

    private record ViewOwner(Object screen, String page) {
    }

    private static ViewOwner viewOwner() {
        Screen screen = Minecraft.getInstance().screen;
        return new ViewOwner(screen, screen instanceof AssistantScreen full ? full.page() : "");
    }

    public static boolean simpleMode() {
        return ClientConfig.INSTANCE.get().simpleMode();
    }

    public static void configureMode(boolean simple) {
        ClientConfig settings = ClientConfig.INSTANCE.get();
        ClientConfig.INSTANCE.set(new ClientConfig(simple, settings.theme(), settings.cardAnimations(), settings.cardsOnOtherScreens()));
    }

    public static void open(boolean simple, String draft) {
        configureMode(simple);
        Minecraft.getInstance().setScreen(simple ? new AssistantSimpleScreen(draft) : new AssistantScreen(draft == null ? "" : draft));
    }

    public static boolean submitText(String text) {
        if (text == null || text.isBlank() || state.busy()) return false;
        if (text.codePointCount(0, text.length()) > 2000) {
            local(SystemResults.error(AgentFailure.INVALID_INPUT));
            return true;
        }
        // Explicit UI commands remain local in free mode; ordinary text still goes directly to the agent.
        if (state.mode() != ConversationMode.FREE || text.stripLeading().startsWith("/")) {
            SpaceTakeInput take = ClientModuleCaches.SPACE.resolve(text);
            if (take != null) {
                send(take, text.strip());
                return true;
            }
            SpaceDepositInput deposit = SpaceDepositInput.parse(text);
            if (deposit != null) {
                send(deposit, text.strip());
                return true;
            }
            if (AssistantClientCommands.execute(text)) return true;
        }
        send(new ChatRequest(text.strip()));
        return true;
    }

    public static void selectMode(ConversationMode mode) {
        if (state.busy() || !state.allowsMode(mode) || mode == state.preferredMode()) return;
        send(new PreferencesRequest(state.saveHistory(), mode));
    }

    public static void local(AgentResult<CardUpdate> result) {
        if (chatError(result)) return;
        result.cards().forEach(card -> ClientCards.apply(card, false));
        state = state.local(result);
        revision++;
        if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) screen.speech().result(result);
    }

    public static void command(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand("system " + command);
    }

    public static void receive(String requestId, AgentResult<CardUpdate> result) {
        receive(requestId, result, false);
    }

    public static void notice(AgentResult<CardUpdate> result) {
        receive("", result, true);
    }

    public static void invalidatePending() {
        state = state.withoutPending();
        revision++;
    }

    public static void complete(String requestId) {
        AssistantClientState completed = state.complete(requestId);
        if (completed == state) return;
        state = completed;
        revision++;
    }

    private static void receive(String requestId, AgentResult<CardUpdate> result, boolean notification) {
        // Command replies have no client pending entry, so unbound errors must precede late-reply filtering.
        if (chatError(result)) {
            complete(requestId);
            return;
        }
        AssistantClientState.Pending pending = state.pending().get(requestId);
        if (!notification && pending == null) {
            // Timed-out replies may finish an existing card, but cannot select a page or create a new message.
            result = result.withPresentation(result.messageKey(), result.text(), result.cards().stream()
                    .filter(card -> card.mode() != CardUpdate.Mode.SHOW).toList(), true);
        }
        boolean select = pending != null && pending.owner().equals(viewOwner());
        boolean removesCurrent = state.current() != null && state.current().result() != null && result.cards().stream()
                .anyMatch(update -> update.mode() == CardUpdate.Mode.REMOVE && state.current().result().cards().stream()
                        .anyMatch(card -> card.ref().id().equals(update.ref().id())));
        if (removesCurrent && Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
            screen.stopSpeech();
            screen.stopRecognition();
        }
        List<CardUpdate> updates = result.cards().stream().filter(card -> ClientCards.apply(card, false)).toList();
        updates.stream().flatMap(card -> card.data().stream()).filter(SpaceOutput.class::isInstance)
                .map(SpaceOutput.class::cast).forEach(ClientModuleCaches.SPACE::receive);
        updates.stream().flatMap(card -> card.data().stream()).filter(SpaceInventoryOutput.class::isInstance)
                .map(SpaceInventoryOutput.class::cast).forEach(ClientModuleCaches.SPACE::receive);
        updates.stream().flatMap(card -> card.data().stream()).filter(RewardChoiceOutput.class::isInstance)
                .map(RewardChoiceOutput.class::cast).forEach(ClientModuleCaches.SPACE::receive);
        updates.forEach(EmotionHudState::receive);
        AgentResult<CardUpdate> accepted = result.withPresentation(result.messageKey(), result.text(), updates, result.silent());
        state = state.receive(requestId, accepted, notification, select);
        revision++;
        // Only this request's reply can replace its source notice; unrelated pushes retain FIFO order.
        if (pending != null && pending.notification().isPresent())
            AssistantMessages.reply(accepted, pending.notification().get());
        else AssistantMessages.refresh(accepted);
        if (accepted.silent() || updates.stream().noneMatch(card -> card.mode() == CardUpdate.Mode.SHOW)) return;
        if (pending != null && pending.notification().isPresent()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (notification || select && !(minecraft.screen instanceof AssistantInputScreen))
            AssistantMessages.result(accepted);
        else if (select && minecraft.screen instanceof AssistantInputScreen screen) screen.speech().result(accepted);
    }

    private static boolean chatError(AgentResult<CardUpdate> result) {
        if (result.status() != AgentResult.Status.FAILED || state.serverState().system().isPresent()
                && !result.messageKey().equals("system.error.not_bound")) return false;
        MutableComponent message = Component.translatable(result.messageKey());
        if (!result.text().isBlank()) message.append("\n").append(result.text());
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(message.withStyle(ChatFormatting.RED));
        return true;
    }

    public static void synchronizedState(String requestId, ClientState value) {
        if (!requestId.isEmpty() && !state.pending().containsKey(requestId)) return;
        state = state.synchronizedState(requestId, value);
        revision++;
    }

    public static void history(String requestId, History value) {
        if (!state.pending().containsKey(requestId)) return;
        state = state.history(requestId, value);
        revision++;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, AssistantClientState.Pending> pending : state.pending().entrySet())
            if (pending.getValue().deadline() <= now) complete(pending.getKey());
    }

    public static void send(AgentAction call, String message) {
        send(new InvokeRequest(call, message));
    }

    public static void send(AgentRequest request) {
        send(request, Optional.empty());
    }

    public static void send(AgentRequest request, Optional<CardReference> notification) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || state.busy()) return;
        String id = UUID.randomUUID().toString();
        if (request != ControlRequest.STATE && Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
            screen.stopSpeech();
            screen.stopRecognition();
        }
        state = state.requested(id, request, viewOwner(), notification);
        revision++;
        Optional<ItemSearchQuery> search = ClientItemSearch.query(request);
        if (search.isEmpty()) connection.send(NetworkManager.ASSISTANT.request(id, request));
        else ClientItemSearch.search(search.get()).whenComplete((batch, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().getConnection() != connection || !state.pending().containsKey(id))
                        return;
                    if (error != null) {
                        SystemMod.LOGGER.warn("Client item search failed for query {}", search.get().query(), error);
                        local(SystemResults.error(AgentFailure.INVALID_INPUT));
                        complete(id);
                    } else connection.send(NetworkManager.ASSISTANT.request(id, request, Optional.of(batch)));
                }));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void clear() {
        AssistantSpeech.stop();
        if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) screen.stopRecognition();
        AssistantMessages.clear();
        AssistantCardDisplay.unmount();
        ClientCards.clear();
        ClientModuleCaches.DISPLAY_CARDS.clear();
        ClientModuleCaches.SPACE.clear();
        ClientModuleCaches.ITEM_SEARCH.clear();
        state = AssistantClientState.EMPTY;
        revision++;
    }
}
