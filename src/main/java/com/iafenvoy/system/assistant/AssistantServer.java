package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentLifecycle;
import com.iafenvoy.system.agent.chat.AiAccess;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.model.OpenAiCompatibleClient;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.assistant.usage.AssistantUsage;
import com.iafenvoy.system.command.SystemCommand;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.database.AgentDatabases;
import com.iafenvoy.system.data.prompt.PromptReloadListener;
import com.iafenvoy.system.data.store.AiAccessData;
import com.iafenvoy.system.data.store.FileAgentStore;
import com.iafenvoy.system.event.AssistantRegistrationEvent;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.ModuleManager;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.iafenvoy.system.search.DeepSeekSearchClient;
import com.iafenvoy.system.search.SearchGateway;
import com.iafenvoy.system.speech.quest.QuestNarration;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

/**
 * One active server runtime; player sessions, requests and usage have separate owners.
 */
@EventBusSubscriber
public final class AssistantServer {
    private static volatile AssistantServer INSTANCE;
    private final MinecraftServer server;
    private final UUID localOwner;
    private final AgentEngine engine;
    private final AgentDatabases databases;
    private final QuestNarration narration;
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private final AssistantPlayers players;
    private final AssistantRequests requests;
    private final AssistantUsage usage;
    private final ModuleManager moduleManager;
    private final AssistantCardDisplays displays;
    private final GameTaskQueue gameQueue = new GameTaskQueue();
    private PromptReloadListener promptListener;
    private final AiAccessData aiAccessData;
    private AiAccess aiAccess;
    // Model-only reloads must refresh players even when administrator rules stay unchanged.
    private boolean modelConfigured;

    private AssistantServer(MinecraftServer server) throws Exception {
        this.server = server;
        GameProfile profile = server.getSingleplayerProfile();
        this.localOwner = profile == null ? null : profile.getId();
        this.aiAccessData = server.overworld().getDataStorage().computeIfAbsent(AiAccessData.TYPE, AiAccessData.ID);
        this.aiAccess = this.aiAccessData.get();
        try {
            Path storageRoot = server.getWorldPath(LevelResource.ROOT).resolve(SystemMod.MOD_ID);
            this.databases = this.own(new AgentDatabases(storageRoot.resolve("database")));
            FileAgentStore store = this.own(new FileAgentStore(storageRoot,
                    () -> this.aiAccessData.get().defaultMode()));
            OpenAiCompatibleClient model = this.own(new OpenAiCompatibleClient());
            this.engine = this.own(new AgentEngine(store, model, data -> ModuleData.get(data, CoreSystemModule.JOURNAL)));
            this.engine.permissions(player -> this.aiAccessData.get().permissions(player, player.equals(this.localOwner)));
            this.engine.chat().personality(this::personalityPrompt);
            this.modelConfigured = this.engine.model().configured();
            // Transfer ownership only after construction succeeds, so a failed initialization still releases the file lock.
            this.resources.remove(store);
            this.resources.remove(model);
            this.engine.lifecycle(new AgentLifecycle() {
                @Override
                public void revoked(UUID player) {
                    AssistantServer.this.databases.deletePlayer(player);
                }
            });
            SearchGateway search = this.own(new SearchGateway(new DeepSeekSearchClient()));
            this.narration = this.own(new QuestNarration(this.engine, this.databases.shared()));
            this.usage = new AssistantUsage(server, this.engine, search);
            this.refreshPrompts();
            MinecraftTools game = new MinecraftTools(server, this.gameQueue);
            CommerceCatalogs catalogs = new CommerceCatalogs(CommerceCatalog.create(server.registryAccess()), this.engine::moduleEnabled);
            CommerceTransactions transactions = new CommerceTransactions(this.engine, game, catalogs);
            this.displays = this.own(new AssistantCardDisplays(server, this.engine));
            ModuleContext context = new ModuleContext(this.engine, server.registryAccess().registryOrThrow(SystemResourceKeys.SYSTEM_MODULE));
            context.put(ContextTypes.SEARCH, search);
            context.put(ContextTypes.GAME, game);
            context.put(ContextTypes.COMMERCE, transactions);
            context.put(ContextTypes.CATALOGS, catalogs);
            context.put(ContextTypes.DATABASES, this.databases);
            context.put(ContextTypes.NARRATION, this.narration);
            context.put(ContextTypes.DISPLAYS, this.displays);
            this.moduleManager = this.own(new ModuleManager(server, context));
            this.engine.moduleAccess(this.moduleManager::hasModule);
            NeoForge.EVENT_BUS.post(new AssistantRegistrationEvent(this.engine));
            this.players = new AssistantPlayers(server, this.engine, this.moduleManager, this.usage);
            this.players.validateModules();
            this.requests = new AssistantRequests(server, this.engine, this.players, this.moduleManager);
            this.moduleManager.onChange(player -> {
                this.requests.syncState(player);
                this.server.getCommands().sendCommands(player);
            });
            this.engine.start();
        } catch (Exception | LinkageError e) {
            this.closeResources();
            throw e;
        }
    }

    private <T extends AutoCloseable> T own(T resource) {
        this.resources.addFirst(resource);
        return resource;
    }

    private void closeResources() {
        // Reverse creation order also handles partially installed servers without leaking workers or file locks.
        while (!this.resources.isEmpty()) {
            AutoCloseable resource = this.resources.removeFirst();
            try {
                resource.close();
            } catch (Exception | LinkageError error) {
                SystemMod.LOGGER.warn("Unable to close assistant resource {}", resource.getClass().getSimpleName(), error);
            }
        }
    }

    public AgentDatabases databases() {
        return this.databases;
    }

    public QuestNarration narration() {
        return this.narration;
    }

    public static AssistantServer get(MinecraftServer server) {
        AssistantServer runtime = INSTANCE;
        return runtime != null && runtime.server == server ? runtime : null;
    }

    public ModuleManager moduleManager() {
        return this.moduleManager;
    }

    public AgentEngine engine() {
        return this.engine;
    }

    public AiAccessData aiAccess() {
        return this.aiAccessData;
    }

    public AssistantCardDisplays displays() {
        return this.displays;
    }

    public AssistantPlayers players() {
        return this.players;
    }

    public AssistantRequests requests() {
        return this.requests;
    }

    public AssistantUsage usage() {
        return this.usage;
    }

    private void refreshPrompts() {
        PromptReloadListener listener = PromptReloadListener.latest();
        if (this.promptListener == listener) return;
        // Publish only the listener retained by successfully installed server resources.
        this.engine.prompts(listener.prompts());
        this.promptListener = listener;
    }

    private String personalityPrompt(ExecutionContext context) {
        Optional<ResourceLocation> prompt = context.store().read(context.player(), data -> data.systems.current())
                .flatMap(id -> this.server.registryAccess().registryOrThrow(SystemResourceKeys.SYSTEM).getOptional(id))
                .flatMap(SystemDefinition::personalityPrompt);
        if (prompt.isEmpty()) return "";
        return context.findPrompt(prompt.get()).orElseGet(() -> {
            SystemMod.LOGGER.warn("Missing personality prompt {} for player {}", prompt.get(), context.player());
            return "";
        });
    }

    public void refreshAiAccess() {
        AiAccess next = this.aiAccessData.get();
        boolean configured = this.engine.model().configured();
        if (this.aiAccess == next && this.modelConfigured == configured) return;
        AiAccess previous = this.aiAccess;
        boolean availabilityChanged = this.modelConfigured != configured;
        this.aiAccess = next;
        this.modelConfigured = configured;
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            boolean owner = player.getUUID().equals(this.localOwner);
            AiAccess.Permissions permissions = next.permissions(player.getUUID(), owner);
            boolean policyChanged = !previous.permissions(player.getUUID(), owner).equals(permissions);
            if (policyChanged || availabilityChanged) {
                this.engine.cancel(player.getUUID());
                this.requests.syncState(player);
                if (policyChanged) player.sendSystemMessage(Component.translatable("system.ai.access_changed",
                        Component.translatable(permissions.allowAi() ? "system.ai.allowed" : "system.ai.denied"),
                        Component.translatable(permissions.allowFreeChat() ? "system.ai.allowed" : "system.ai.denied"),
                        permissions.modelCallsPerDay(), Component.translatable(permissions.maxMode().labelKey()))
                        .withStyle(permissions.allowAi() ? ChatFormatting.YELLOW : ChatFormatting.RED));
                if (availabilityChanged) player.sendSystemMessage(Component.translatable(configured
                                ? "system.ai.model_ready_hint" : "system.ai.model_missing_hint")
                        .withStyle(configured ? ChatFormatting.YELLOW : ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (INSTANCE != null) return;
        try {
            // Publish only fully constructed services; stale callbacks still check their server identity.
            INSTANCE = new AssistantServer(event.getServer());
            SystemCommand.install(event.getServer());
        } catch (Exception | LinkageError e) {
            stopping(event.getServer());
            SystemMod.LOGGER.error("Assistant failed to initialize; reward writes are disabled", e);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        stopping(event.getServer());
    }

    private static void stopping(MinecraftServer server) {
        AssistantServer runtime = get(server);
        if (runtime != null) {
            INSTANCE = null;
            try {
                runtime.players.close();
            } finally {
                runtime.closeResources();
                runtime.usage.drain();
            }
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerNetworkHandler.connected(player);
            AssistantServer runtime = get(player.level().getServer());
            if (runtime != null) runtime.players.login(player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerNetworkHandler.disconnected(player);
            AssistantServer runtime = get(player.level().getServer());
            if (runtime != null) runtime.players.logout(player);
        }
    }

    @SubscribeEvent
    public static void advancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AssistantServer runtime = get(player.level().getServer());
            if (runtime != null) runtime.players.earned(player, event.getAdvancement().id());
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        AssistantServer runtime = get(event.getServer());
        if (runtime != null) {
            runtime.usage.drain();
            runtime.refreshPrompts();
            runtime.refreshAiAccess();
            runtime.moduleManager.tick();
            runtime.gameQueue.tick();
        }
    }

    @SubscribeEvent
    public static void reloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PromptReloadListener());
    }
}
