package com.iafenvoy.system.runtime.server;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.TypedPayload;
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
import com.iafenvoy.system.config.AgentSettings;
import com.iafenvoy.system.config.AliyunSpeechPricing;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.event.AssistantRegistrationEvent;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.network.payload.RecognitionUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.SpeechUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import com.iafenvoy.system.runtime.reload.CatalogReloadListener;
import com.iafenvoy.system.runtime.reload.PromptReloadListener;
import com.iafenvoy.system.runtime.usage.SpeechAccounting;
import com.iafenvoy.system.runtime.usage.TokenUsageData;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.SystemConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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

/**
 * Per-server bridge for Agent registration, player lifecycle, game events and main-thread replies.
 */
@EventBusSubscriber
public final class AssistantServer {
    private static final Map<MinecraftServer, AssistantServer> SERVERS = new ConcurrentHashMap<>();
    private final MinecraftServer server;
    private final AgentEngine engine;
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
    private final Map<UUID, Set<String>> baselineReady = new HashMap<>();
    private final Map<UUID, List<String>> initializingEvents = new HashMap<>();
    private final Map<UUID, UUID> initializations = new HashMap<>();
    private final Set<UUID> bindingChanges = new HashSet<>();

    private AssistantServer(MinecraftServer server) throws Exception {
        this.server = server;
        this.tokenUsage = server.getDataStorage().computeIfAbsent(TokenUsageData.TYPE);
        try {
            AliyunSpeechPricing.load(SystemConfig.file(AliyunSpeechPricing.FILE_NAME));
        } catch (IOException error) {
            SystemMod.LOGGER.warn("Unable to load speech pricing; new speech calls will remain unpriced", error);
        }
        this.speechAccounting = new SpeechAccounting(this.tokenUsage);
        Path configuration = SystemConfig.file("system-agent.json");
        AgentSettings settings;
        if (Files.exists(configuration)) {
            settings = CodecIO.read(AgentSettings.CODEC, configuration, 131072);
        } else {
            settings = new AgentSettings();
            CodecIO.save(AgentSettings.CODEC, configuration, settings);
        }
        settings.validate();
        this.engine = new AgentEngine(settings, new FileAgentStore(server.getWorldPath(LevelResource.ROOT).resolve("system-agent")), new OpenAiCompatibleClient(settings));
        this.engine.onModelUsage((player, usage, successful, time) -> this.pendingUsage.add(new UsageRecord(
                player, this.usageNames.getOrDefault(player, player.toString()), usage, successful, time)));
        try {
            this.refreshCatalog();
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
            for (AgentSettings.EventRule rule : settings.advancementRules) {
                if (rule.call().value() instanceof AdvancementInput input && (input.advancementId().isPresent() || input.ruleId().isPresent()))
                    throw new IllegalArgumentException("Event metadata is supplied by the server");
                if (Identifier.tryParse(rule.advancementId()) == null)
                    throw new IllegalArgumentException("Invalid advancement");
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
        if (this.notificationsEnabled(player)) ServerNetworkHandler.reply(player, "event", result);
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
        if (offset < 0 || offset > Integer.MAX_VALUE - TokenUsageData.PAGE_SIZE) throw new AgentException("INVALID_INPUT");
        this.drainUsage();
        return this.tokenUsage.snapshot(player.getUUID(), player.getGameProfile().name(), all, canViewAll, offset);
    }

    public void openUsage(ServerPlayer player, boolean all) {
        this.requestUsage(player, new TokenUsageRequestC2SPayload("open", all, 0));
    }

    public void reportSpeech(ServerPlayer player, SpeechUsageReportC2SPayload report) {
        this.speechAccounting.report(player, report);
    }

    public void reportRecognition(ServerPlayer player, RecognitionUsageReportC2SPayload report) {
        this.speechAccounting.report(player, report);
    }

    public void requestUsage(ServerPlayer player, TokenUsageRequestC2SPayload request) {
        long now = System.currentTimeMillis();
        if (now - this.lastUsageRequest.getOrDefault(player.getUUID(), 0L) < 350) {
            ServerNetworkHandler.replyUsageError(player, request.id(), "RATE_LIMITED");
            return;
        }
        this.lastUsageRequest.put(player.getUUID(), now);
        this.rememberUsageName(player);
        ServerNetworkHandler.replyUsage(player, request.id(), this.usageSnapshot(player, request.all(), request.offset()));
    }

    public void open(ServerPlayer player, String view) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        AgentStore store = this.engine.playerStore(player.getUUID());
        this.engine.maintenance(() -> store.read(player.getUUID(), data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            return AgentResult.ok("system.result.ready");
        })).whenComplete((result, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(player.getUUID()) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            ServerNetworkHandler.reply(player, error == null ? view : "notice", error == null ? result : AgentResult.error(AgentEngine.errorCode(error)));
        }));
    }

    public void grant(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (!this.beginBindingChange(player, feedback)) return;
        UUID uuid = player.getUUID(), connection = ServerNetworkHandler.connectionId(player);
        Map<String, Set<String>> baselines = new LinkedHashMap<>();
        for (AgentSettings.EventRule rule : this.engine.settings().advancementRules) {
            AdvancementHolder advancement = this.server.getAdvancements().get(Identifier.parse(rule.advancementId()));
            boolean completed = advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
            baselines.put(rule.id() + "|" + rule.advancementId(), completed ? Set.of(rule.advancementId()) : Set.of());
        }
        this.engine.grant(uuid, baselines).whenComplete((result, error) -> this.server.execute(() -> {
            this.bindingChanges.remove(uuid);
            AgentResult answer = error == null ? result : AgentResult.error(AgentEngine.errorCode(error));
            feedback.accept(answer);
            if (this.server.getPlayerList().getPlayer(uuid) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            boolean fresh = answer.messageKey().equals("system.result.bound");
            if (fresh) this.login(player);
            ServerNetworkHandler.reply(player, answer.status().equals("SUCCEEDED") && fresh ? "bound" : "notice", answer);
        }));
    }

    public void revoke(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (!this.beginBindingChange(player, feedback)) return;
        UUID uuid = player.getUUID();
        long previous = this.engine.incarnation(uuid);
        CompletionStage<AgentResult> work = this.engine.revoke(uuid);
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
            ServerNetworkHandler.reply(player, answer.status().equals("SUCCEEDED") ? "revoked" : this.engine.incarnation(uuid) != previous ? "revoke_failed" : "notice", answer);
        }));
    }

    private boolean beginBindingChange(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (this.bindingChanges.add(player.getUUID())) return true;
        AgentResult busy = AgentResult.error("BUSY");
        feedback.accept(busy);
        ServerNetworkHandler.reply(player, "notice", busy);
        return false;
    }

    private void refreshCatalog() {
        CatalogReloadListener listener = this.server.getServerResources().managers().getListener(CatalogReloadListener.KEY);
        if (this.catalogListener == listener) return;
        this.engine.catalog(listener.catalog(this.engine.settings()));
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
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        if (now - this.lastRequest.getOrDefault(uuid, 0L) < 350) {
            ServerNetworkHandler.reply(player, requestId, AgentResult.error("RATE_LIMITED"));
            return;
        }
        this.lastRequest.put(uuid, now);
        UUID connection = ServerNetworkHandler.connectionId(player);
        AgentStore store = this.engine.playerStore(uuid);
        CompletionStage<AgentResult> work = switch (request) {
            case AgentRequest.Control.STATE -> this.engine.maintenance(() -> store.read(uuid, data -> {
                if (!data.bound) throw new AgentException("NOT_BOUND");
                return AgentResult.data("system.result.state",
                        ResultTypes.CLIENT_STATE.of(new ResultTypes.ClientState(data.saveHistory, data.externalModel,
                                this.engine.modules().get(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_query")) != null,
                                this.engine.modules().commands().suggestions())));
            }));
            case AgentRequest.Control.HISTORY -> this.engine.history(uuid);
            case AgentRequest.Control.FORGET -> this.engine.forget(uuid);
            case AgentRequest.Preferences preferences -> this.engine.preferences(uuid, preferences.history(), preferences.external());
            case AgentRequest.Chat chat -> this.engine.submit(uuid, requestId, null, chat.text());
            case AgentRequest.Invoke invoke -> this.engine.submit(uuid, requestId, invoke.call(), invoke.text());
            case AgentRequest.Control.OPEN -> throw new AgentException("INVALID_INPUT");
        };
        work.whenComplete((result, error) -> this.server.execute(() -> {
            ServerPlayer online = this.server.getPlayerList().getPlayer(uuid);
            if (online != null && connection.equals(ServerNetworkHandler.connectionId(online)))
                ServerNetworkHandler.reply(online, requestId, error == null ? result : AgentResult.error(AgentEngine.errorCode(error)));
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
        List<AgentSettings.EventRule> rules = List.copyOf(this.engine.settings().advancementRules);
        Map<String, Boolean> completed = new LinkedHashMap<>();
        for (AgentSettings.EventRule rule : rules) {
            AdvancementHolder advancement = this.server.getAdvancements().get(Identifier.parse(rule.advancementId()));
            completed.put(rule.id(), advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone());
        }
        this.initializingEvents.put(uuid, new ArrayList<>());
        this.engine.maintenance(() -> {
            if (!store.read(uuid, data -> data.bound)) return null;
            return store.update(uuid, data -> {
                Map<String, Boolean> initial = new LinkedHashMap<>();
                for (AgentSettings.EventRule rule : rules) {
                    String baselineId = rule.id() + "|" + rule.advancementId();
                    boolean fresh = !data.baselines.containsKey(baselineId);
                    initial.put(rule.id(), fresh);
                    if (fresh)
                        data.baselines.put(baselineId, completed.get(rule.id()) ? Set.of(rule.advancementId()) : Set.of());
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
            List<String> delayed = this.initializingEvents.remove(uuid);
            if (error != null) {
                SystemMod.LOGGER.warn("Assistant baseline failed for {}", uuid);
                return;
            }
            if (initial == null) {
                this.baselineReady.remove(uuid);
                this.engine.disableRules(uuid);
                return;
            }
            this.baselineReady.put(uuid, rules.stream().map(AgentSettings.EventRule::id).collect(Collectors.toSet()));
            this.engine.enableRules(uuid, this.baselineReady.get(uuid));
            for (AgentSettings.EventRule rule : rules) {
                if ((!initial.get(rule.id()) && completed.get(rule.id())) || (delayed != null && delayed.contains(rule.advancementId())))
                    this.dispatch(uuid, rule);
            }
            this.engine.resume(uuid);
        }));
    }

    private void earned(ServerPlayer player, String advancement) {
        this.rememberUsageName(player);
        this.engine.language(player.getUUID(), player.clientInformation().language());
        UUID uuid = player.getUUID();
        if (this.initializingEvents.containsKey(uuid)) {
            List<String> delayed = this.initializingEvents.get(uuid);
            if (delayed.size() < 128) delayed.add(advancement);
            return;
        }
        for (AgentSettings.EventRule rule : this.engine.settings().advancementRules)
            if (rule.advancementId().equals(advancement) && this.baselineReady.getOrDefault(uuid, Set.of()).contains(rule.id()))
                this.dispatch(uuid, rule);
    }

    private void dispatch(UUID player, AgentSettings.EventRule rule) {
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player);
        this.engine.maintenance(() -> store.read(player, data ->
                        data.baselines.getOrDefault(rule.id() + "|" + rule.advancementId(), Set.of()).contains(rule.advancementId())))
                .thenAccept(baseline -> {
                    if (baseline || !this.engine.current(player, incarnation)) return;
                    TypedPayload<?> input = rule.call();
                    if (input.value() instanceof AdvancementInput value)
                        input = ResultTypes.ADVANCEMENT_ACTION.of(new AdvancementInput(value.featureId(), value.rewardId(),
                                Optional.of(rule.advancementId()), Optional.of(rule.id())));
                    this.engine.trigger(player, rule.id() + "|" + rule.advancementId(), rule.id(), input).thenAccept(result -> {
                        if (result != null && "SUCCEEDED".equals(result.status())) this.server.execute(() -> {
                            ServerPlayer online = this.server.getPlayerList().getPlayer(player);
                            if (online != null && this.engine.current(player, incarnation))
                                ServerNetworkHandler.reply(online, "event", result);
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
            if (runtime != null) runtime.earned(player, event.getAdvancement().id().toString());
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
