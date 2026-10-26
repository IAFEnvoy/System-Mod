package com.iafenvoy.system.runtime.server;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModules;
import com.iafenvoy.system.agent.builtin.input.AdvancementInput;
import com.iafenvoy.system.agent.builtin.tool.DeliverTool;
import com.iafenvoy.system.agent.builtin.tool.ItemQueryTool;
import com.iafenvoy.system.agent.builtin.tool.PlayerQueryTool;
import com.iafenvoy.system.agent.builtin.tool.ShopPurchaseTool;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.model.OpenAiCompatibleClient;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.FileAgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.AdvancementRule;
import com.iafenvoy.system.config.file.AdvancementConfig;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.config.file.SemanticConfig;
import com.iafenvoy.system.data.CatalogReloadListener;
import com.iafenvoy.system.data.PromptReloadListener;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.event.AssistantRegistrationEvent;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.network.payload.RecognitionUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.SpeechUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import com.iafenvoy.system.runtime.usage.SpeechAccounting;
import com.iafenvoy.system.runtime.usage.TokenUsageData;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Per-server bridge for Agent registration, player lifecycle, game events and main-thread replies.
 */
@EventBusSubscriber
public final class AssistantServer {
    private static final Map<MinecraftServer, AssistantServer> SERVERS = new ConcurrentHashMap<>();
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final AdvancementConfig advancements;
    private final TokenUsageData tokenUsage;
    private final SpeechAccounting speechAccounting;
    private final Queue<UsageRecord> pendingUsage = new ConcurrentLinkedQueue<>();
    private final Map<UUID, String> usageNames = new ConcurrentHashMap<>();
    private CatalogReloadListener catalogListener;
    private PromptReloadListener promptListener;
    private final ShopPurchaseTool shopTools;
    private final GameTaskQueue gameQueue = new GameTaskQueue();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<UUID, Long> lastUsageRequest = new HashMap<>();
    private final Map<UUID, Set<Identifier>> baselineReady = new HashMap<>();
    private final Map<UUID, List<Identifier>> initializingEvents = new HashMap<>();
    private final Map<UUID, UUID> initializations = new HashMap<>();
    private final Set<UUID> bindingChanges = new HashSet<>();
    private final TypeDispatch<RequestContext, CompletionStage<AgentResponse>> requestHandlers = new TypeDispatch<>((_, _) -> {
        throw new AgentException("UNSUPPORTED");
    });
    private final Map<AgentRequest.Control, Function<RequestContext, CompletionStage<AgentResponse>>> controlHandlers = new EnumMap<>(AgentRequest.Control.class);

    private AssistantServer(MinecraftServer server) throws Exception {
        this.server = server;
        this.tokenUsage = server.getDataStorage().computeIfAbsent(TokenUsageData.TYPE);
        this.speechAccounting = new SpeechAccounting(this.tokenUsage);
        AgentConfig settings = AgentConfig.INSTANCE.get();
        ModelConfig model = ModelConfig.INSTANCE.get();
        SemanticConfig semantic = SemanticConfig.INSTANCE.get();
        this.advancements = AdvancementConfig.INSTANCE.get();
        this.catalogListener = server.getServerResources().managers().getListener(CatalogReloadListener.KEY);
        this.engine = new AgentEngine(settings, semantic, this.catalogListener.catalog(),
                new FileAgentStore(server.getWorldPath(LevelResource.ROOT).resolve("system-agent")), new OpenAiCompatibleClient(model));
        this.engine.onModelUsage((player, usage, successful, time) -> this.pendingUsage.add(new UsageRecord(
                player, this.usageNames.getOrDefault(player, player.toString()), usage, successful, time)));
        try {
            this.registerRequests();
            this.refreshPrompts();
            MinecraftTools game = new MinecraftTools(server, this.gameQueue);
            this.shopTools = new ShopPurchaseTool(this.engine, game);
            BuiltinModules.install(this.engine, List.of(new PlayerQueryTool(game).tool(), new ItemQueryTool(game).tool(),
                    new DeliverTool(this.engine, game).tool(), this.shopTools.tool()));
            // Resolve optional adapters only after the mod check to keep their classes safe to omit.
            if (ModList.get().isLoaded("ftbquests")) {
                try {
                    Class.forName("com.iafenvoy.system.compat.ftb.FtbQuestAdapter")
                            .getMethod("install", AgentEngine.class, MinecraftTools.class).invoke(null, this.engine, game);
                } catch (ReflectiveOperationException | LinkageError e) {
                    SystemMod.LOGGER.error("FTB Quests assistant adapter is unavailable", e);
                }
            }
            NeoForge.EVENT_BUS.post(new AssistantRegistrationEvent(this.engine));
            for (AdvancementRule rule : this.advancements.rules()) {
                if (!this.engine.modules().call(rule.call()).module().descriptor().event())
                    throw new IllegalArgumentException("Unknown event module " + rule.moduleId());
                if (rule.call().value() instanceof AdvancementInput input && (input.advancementId().isPresent() || input.ruleId().isPresent()))
                    throw new IllegalArgumentException("Event metadata is supplied by the server");
            }
            this.engine.start();
        } catch (Exception e) {
            this.engine.close();
            throw e;
        }
    }

    public static AssistantServer get(MinecraftServer server) {
        return SERVERS.get(server);
    }

    public AgentEngine engine() {
        return this.engine;
    }

    /**
     * The completed login baseline is the main-thread binding cache; notification paths never read files on ticks.
     */
    public boolean notificationsEnabled(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return this.server.getPlayerList().getPlayer(uuid) == player && this.baselineReady.containsKey(uuid)
                && !this.initializations.containsKey(uuid) && !this.bindingChanges.contains(uuid)
                && this.engine.current(uuid, this.engine.incarnation(uuid));
    }

    public void notifyEvent(ServerPlayer player, AgentResult result) {
        if (this.notificationsEnabled(player)) ServerNetworkHandler.notice(player, result);
    }

    private record UsageRecord(UUID player, String name, ModelClient.Usage usage, boolean successful, long time) {
    }

    private void rememberUsageName(ServerPlayer player) {
        String name = player.getGameProfile().name();
        this.usageNames.put(player.getUUID(), name);
        this.tokenUsage.rename(player.getUUID(), name);
    }

    private void drainUsage() {
        // Model workers enqueue immutable records; only the server thread mutates and dirties SavedData.
        UsageRecord record;
        while ((record = this.pendingUsage.poll()) != null)
            this.tokenUsage.record(record.player(), this.usageNames.getOrDefault(record.player(), record.name()), record.usage(), record.successful(), record.time());
    }

    private TokenUsageSnapshot usageSnapshot(ServerPlayer player, boolean all, int offset) {
        boolean canViewAll = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (all && !canViewAll) throw new AgentException("NOT_AUTHORIZED");
        if (offset < 0 || offset > Integer.MAX_VALUE - TokenUsageData.PAGE_SIZE)
            throw new AgentException("INVALID_INPUT");
        this.drainUsage();
        return this.tokenUsage.snapshot(player.getUUID(), player.getGameProfile().name(), all, canViewAll, offset);
    }

    public void openUsage(ServerPlayer player, boolean all) {
        this.requestUsage(player, new TokenUsageRequestC2SPayload(UUID.randomUUID().toString(), all, 0), true);
    }

    public void reportSpeech(ServerPlayer player, SpeechUsageReportC2SPayload report) {
        this.speechAccounting.report(player, report);
    }

    public void reportRecognition(ServerPlayer player, RecognitionUsageReportC2SPayload report) {
        this.speechAccounting.report(player, report);
    }

    public void requestUsage(ServerPlayer player, TokenUsageRequestC2SPayload request) {
        this.requestUsage(player, request, false);
    }

    private void requestUsage(ServerPlayer player, TokenUsageRequestC2SPayload request, boolean open) {
        long now = System.currentTimeMillis();
        if (now - this.lastUsageRequest.getOrDefault(player.getUUID(), 0L) < 350) {
            ServerNetworkHandler.replyUsageError(player, request.id(), "RATE_LIMITED", open);
            return;
        }
        this.lastUsageRequest.put(player.getUUID(), now);
        this.rememberUsageName(player);
        ServerNetworkHandler.replyUsage(player, request.id(), this.usageSnapshot(player, request.all(), request.offset()), open);
    }

    private CompletionStage<AgentResult> requireBound(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            return AgentResult.ok("system.result.ready");
        }));
    }

    public void open(ServerPlayer player, AgentResponse.View view) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        this.requireBound(player.getUUID()).whenComplete((result, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(player.getUUID()) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            if (error == null) ServerNetworkHandler.send(player, new AgentResponse.Open(view));
            else ServerNetworkHandler.notice(player, AgentResult.error(AgentEngine.errorCode(error)));
        }));
    }

    public void grant(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (!this.beginBindingChange(player, feedback)) return;
        UUID uuid = player.getUUID(), connection = ServerNetworkHandler.connectionId(player);
        Map<String, Set<String>> baselines = new LinkedHashMap<>();
        for (AdvancementRule rule : this.advancements.rules()) {
            AdvancementHolder advancement = this.server.getAdvancements().get(rule.advancementId());
            boolean completed = advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
            baselines.put(rule.id() + "|" + rule.advancementId(), completed ? Set.of(rule.advancementId().toString()) : Set.of());
        }
        this.engine.grant(uuid, baselines).whenComplete((result, error) -> this.server.execute(() -> {
            this.bindingChanges.remove(uuid);
            AgentResult answer = error == null ? result : AgentResult.error(AgentEngine.errorCode(error));
            feedback.accept(answer);
            if (this.server.getPlayerList().getPlayer(uuid) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            boolean fresh = answer.messageKey().equals("system.result.bound");
            if (fresh) this.login(player);
            if (answer.status().equals("SUCCEEDED") && fresh)
                ServerNetworkHandler.binding(player, AgentResponse.Change.BOUND, answer);
            else ServerNetworkHandler.notice(player, answer);
        }));
    }

    public void revoke(ServerPlayer player, Consumer<AgentResult> feedback) {
        this.clearPlayerData(player, feedback, false);
    }

    public void reset(ServerPlayer player, Consumer<AgentResult> feedback) {
        this.clearPlayerData(player, feedback, true);
    }

    private void clearPlayerData(ServerPlayer player, Consumer<AgentResult> feedback, boolean reset) {
        if (!this.beginBindingChange(player, feedback)) return;
        UUID uuid = player.getUUID();
        long previous = this.engine.incarnation(uuid);
        CompletionStage<AgentResult> work = reset ? this.engine.reset(uuid) : this.engine.revoke(uuid);
        if (this.engine.incarnation(uuid) != previous) ServerNetworkHandler.connected(player);
        UUID connection = ServerNetworkHandler.connectionId(player);
        work.whenComplete((result, error) -> this.server.execute(() -> {
            this.bindingChanges.remove(uuid);
            AgentResult answer = error == null ? result : AgentResult.error(AgentEngine.errorCode(error));
            if (answer.status().equals("SUCCEEDED")) {
                this.lastRequest.remove(uuid);
                this.baselineReady.remove(uuid);
                this.initializingEvents.remove(uuid);
                this.initializations.remove(uuid);
            }
            feedback.accept(answer);
            if (this.server.getPlayerList().getPlayer(uuid) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            if (!answer.status().equals("SUCCEEDED") && this.engine.incarnation(uuid) != previous) this.login(player);
            if (answer.status().equals("SUCCEEDED"))
                ServerNetworkHandler.binding(player, AgentResponse.Change.REVOKED, answer);
            else if (this.engine.incarnation(uuid) != previous)
                ServerNetworkHandler.binding(player, AgentResponse.Change.INVALIDATED, answer);
            else ServerNetworkHandler.notice(player, answer);
        }));
    }

    private boolean beginBindingChange(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (this.bindingChanges.add(player.getUUID())) return true;
        AgentResult busy = AgentResult.error("BUSY");
        feedback.accept(busy);
        ServerNetworkHandler.notice(player, busy);
        return false;
    }

    private void refreshCatalog() {
        CatalogReloadListener listener = this.server.getServerResources().managers().getListener(CatalogReloadListener.KEY);
        if (this.catalogListener == listener) return;
        this.engine.catalog(listener.catalog());
        this.catalogListener = listener;
    }

    private void refreshPrompts() {
        PromptReloadListener listener = this.server.getServerResources().managers().getListener(PromptReloadListener.KEY);
        if (this.promptListener == listener) return;
        // Publish only the listener retained by successfully installed server resources.
        this.engine.prompts(listener.prompts());
        this.promptListener = listener;
    }

    public void request(ServerPlayer player, String requestId, AgentRequest request) {
        this.rememberUsageName(player);
        this.engine.language(player.getUUID(), player.clientInformation().language());
        UUID uuid = player.getUUID();
        UUID connection = ServerNetworkHandler.connectionId(player);
        CompletionStage<AgentResponse> work = this.requestHandlers.apply(new RequestContext(player, requestId), request);
        work.whenComplete((result, error) -> this.server.execute(() -> {
            ServerPlayer online = this.server.getPlayerList().getPlayer(uuid);
            if (online != null && connection.equals(ServerNetworkHandler.connectionId(online))) {
                if (error == null) ServerNetworkHandler.send(online, result);
                else ServerNetworkHandler.reply(online, requestId, AgentResult.error(AgentEngine.errorCode(error)));
            }
        }));
    }

    private record RequestContext(ServerPlayer player, String requestId) {
        UUID uuid() {
            return this.player.getUUID();
        }
    }

    private static CompletionStage<AgentResponse> answer(RequestContext context, CompletionStage<AgentResult> work) {
        return work.thenApply(result -> new AgentResponse.Reply(context.requestId(), result));
    }

    private CompletionStage<AgentResponse> limited(RequestContext context, Supplier<CompletionStage<AgentResponse>> work) {
        long now = System.currentTimeMillis();
        if (now - this.lastRequest.getOrDefault(context.uuid(), 0L) < 350)
            return CompletableFuture.completedFuture(new AgentResponse.Reply(context.requestId(), AgentResult.error("RATE_LIMITED")));
        this.lastRequest.put(context.uuid(), now);
        return work.get();
    }

    private <T extends AgentRequest> void registerRequest(Class<T> type, BiFunction<RequestContext, T, CompletionStage<AgentResponse>> handler) {
        this.requestHandlers.register(type, (context, request) -> this.limited(context, () -> handler.apply(context, request)));
    }

    private void registerControl(AgentRequest.Control control, Function<RequestContext, CompletionStage<AgentResponse>> handler) {
        this.controlHandlers.put(control, context -> this.limited(context, () -> handler.apply(context)));
    }

    private void registerRequests() {
        this.registerRequest(AgentRequest.Chat.class, (context, chat) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), null, chat.text())));
        this.registerRequest(AgentRequest.Invoke.class, (context, invoke) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), invoke.call(), invoke.text())));
        this.registerRequest(AgentRequest.Preferences.class, (context, preferences) -> answer(context, this.engine.preferences(context.uuid(), preferences.history(), preferences.external())));
        this.requestHandlers.register(AgentRequest.Control.class, (context, control) -> this.controlHandlers.get(control).apply(context));
        this.registerControl(AgentRequest.Control.STATE, context -> answer(context, this.state(context.uuid())));
        this.registerControl(AgentRequest.Control.HISTORY, context -> answer(context, this.engine.history(context.uuid())));
        this.registerControl(AgentRequest.Control.FORGET, context -> answer(context, this.engine.forget(context.uuid())));
        // Navigation must not consume the interval needed by the client's immediate state refresh.
        this.controlHandlers.put(AgentRequest.Control.OPEN, context -> this.requireBound(context.uuid())
                .thenApply(_ -> new AgentResponse.Open(AgentResponse.View.CURRENT, Optional.of(context.requestId()))));
    }

    private CompletionStage<AgentResult> state(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            return AgentResult.data("system.result.state",
                    ResultTypes.CLIENT_STATE.of(new ResultTypes.ClientState(data.saveHistory, data.externalModel,
                            this.engine.modules().get(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_query")) != null,
                            this.engine.modules().commands().suggestions())));
        }));
    }

    private void login(ServerPlayer player) {
        // Snapshot advancement baselines before enabling rules; buffer new events during asynchronous setup.
        this.rememberUsageName(player);
        this.engine.language(player.getUUID(), player.clientInformation().language());
        UUID uuid = player.getUUID();
        long incarnation = this.engine.incarnation(uuid);
        UUID initialization = UUID.randomUUID();
        this.initializations.put(uuid, initialization);
        AgentStore store = this.engine.playerStore(uuid);
        List<AdvancementRule> rules = List.copyOf(this.advancements.rules());
        Map<Identifier, Boolean> completed = new LinkedHashMap<>();
        for (AdvancementRule rule : rules) {
            AdvancementHolder advancement = this.server.getAdvancements().get(rule.advancementId());
            completed.put(rule.id(), advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone());
        }
        this.initializingEvents.put(uuid, new ArrayList<>());
        this.engine.maintenance(() -> {
            if (!store.read(uuid, data -> data.bound)) return null;
            return store.update(uuid, data -> {
                Map<Identifier, Boolean> initial = new LinkedHashMap<>();
                for (AdvancementRule rule : rules) {
                    String baselineId = rule.id() + "|" + rule.advancementId();
                    boolean fresh = !data.baselines.containsKey(baselineId);
                    initial.put(rule.id(), fresh);
                    if (fresh)
                        data.baselines.put(baselineId, completed.get(rule.id()) ? Set.of(rule.advancementId().toString()) : Set.of());
                }
                for (PlayerData.Delivery delivery : data.inbox.values())
                    if ("DELIVERING".equals(delivery.state)) delivery.state = "DELIVERY_UNKNOWN";
                for (PlayerData.Purchase purchase : data.purchases.values())
                    if ("PREPARED".equals(purchase.state) && !this.shopTools.active(uuid, purchase.id)) {
                        purchase.state = "PAYMENT_UNKNOWN";
                        purchase.error = "EFFECT_UNKNOWN";
                        PlayerData.Delivery delivery = data.inbox.get("shop:" + purchase.id);
                        if (delivery != null) delivery.state = "PAYMENT_UNKNOWN";
                    }
                return initial;
            });
        }).whenComplete((initial, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(uuid) != player || !this.engine.current(uuid, incarnation) || !initialization.equals(this.initializations.get(uuid)))
                return;
            this.initializations.remove(uuid);
            List<Identifier> delayed = this.initializingEvents.remove(uuid);
            if (error != null) {
                SystemMod.LOGGER.warn("Assistant baseline failed for {}", uuid);
                return;
            }
            if (initial == null) {
                this.baselineReady.remove(uuid);
                this.engine.disableRules(uuid);
                return;
            }
            this.baselineReady.put(uuid, rules.stream().map(AdvancementRule::id).collect(Collectors.toSet()));
            this.engine.enableRules(uuid, this.baselineReady.get(uuid).stream().map(Identifier::toString).collect(Collectors.toSet()));
            for (AdvancementRule rule : rules) {
                if ((!initial.get(rule.id()) && completed.get(rule.id())) || (delayed != null && delayed.contains(rule.advancementId())))
                    this.dispatch(uuid, rule);
            }
            this.engine.resume(uuid);
        }));
    }

    private void earned(ServerPlayer player, Identifier advancement) {
        this.rememberUsageName(player);
        this.engine.language(player.getUUID(), player.clientInformation().language());
        UUID uuid = player.getUUID();
        if (this.initializingEvents.containsKey(uuid)) {
            List<Identifier> delayed = this.initializingEvents.get(uuid);
            if (delayed.size() < 128) delayed.add(advancement);
            return;
        }
        for (AdvancementRule rule : this.advancements.rules())
            if (rule.advancementId().equals(advancement) && this.baselineReady.getOrDefault(uuid, Set.of()).contains(rule.id()))
                this.dispatch(uuid, rule);
    }

    private void dispatch(UUID player, AdvancementRule rule) {
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player);
        this.engine.maintenance(() -> store.read(player, data ->
                        data.baselines.getOrDefault(rule.id() + "|" + rule.advancementId(), Set.of()).contains(rule.advancementId().toString())))
                .thenAccept(baseline -> {
                    if (baseline || !this.engine.current(player, incarnation)) return;
                    TypedPayload<?> input = rule.call();
                    if (input.value() instanceof AdvancementInput value)
                        input = ResultTypes.ADVANCEMENT_ACTION.of(new AdvancementInput(value.featureId(), value.rewardId(),
                                Optional.of(rule.advancementId().toString()), Optional.of(rule.id().toString())));
                    this.engine.trigger(player, rule.id() + "|" + rule.advancementId(), rule.id().toString(), input).thenAccept(result -> {
                        if (result != null && "SUCCEEDED".equals(result.status())) this.server.execute(() -> {
                            ServerPlayer online = this.server.getPlayerList().getPlayer(player);
                            if (online != null && this.engine.current(player, incarnation))
                                ServerNetworkHandler.notice(online, result);
                        });
                    });
                });
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        try {
            SERVERS.put(event.getServer(), new AssistantServer(event.getServer()));
        } catch (Exception e) {
            SystemMod.LOGGER.error("Assistant failed to initialize; reward writes are disabled", e);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        AssistantServer runtime = SERVERS.remove(event.getServer());
        if (runtime != null) {
            runtime.engine.close();
            runtime.drainUsage();
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerNetworkHandler.connected(player);
            AssistantServer runtime = get(player.level().getServer());
            if (runtime != null) runtime.login(player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerNetworkHandler.disconnected(player);
            AssistantServer runtime = get(player.level().getServer());
            if (runtime != null) {
                runtime.engine.cancel(player.getUUID());
                runtime.engine.disconnected(player.getUUID());
                runtime.lastRequest.remove(player.getUUID());
                runtime.baselineReady.remove(player.getUUID());
                runtime.initializingEvents.remove(player.getUUID());
                runtime.initializations.remove(player.getUUID());
                runtime.lastUsageRequest.remove(player.getUUID());
                runtime.speechAccounting.disconnected(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void advancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AssistantServer runtime = get(player.level().getServer());
            if (runtime != null) runtime.earned(player, event.getAdvancement().id());
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        AssistantServer runtime = get(event.getServer());
        if (runtime != null) {
            runtime.drainUsage();
            runtime.refreshCatalog();
            runtime.refreshPrompts();
            runtime.gameQueue.tick();
        }
    }

    @SubscribeEvent
    public static void reloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(CatalogReloadListener.KEY, new CatalogReloadListener());
        event.addRetainedListener(PromptReloadListener.KEY, new PromptReloadListener());
    }
}
