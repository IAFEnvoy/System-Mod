package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentLifecycle;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.model.OpenAiCompatibleClient;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.assistant.usage.AssistantUsage;
import com.iafenvoy.system.command.SystemCommand;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.database.AgentDatabases;
import com.iafenvoy.system.data.prompt.PromptReloadListener;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * One active server runtime; player sessions, requests and usage have separate owners.
 */
@EventBusSubscriber
public final class AssistantServer {
    private static volatile AssistantServer INSTANCE;
    private final MinecraftServer server;
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

    private AssistantServer(MinecraftServer server) throws Exception {
        this.server = server;
        try {
            this.databases = this.own(new AgentDatabases(server.getWorldPath(LevelResource.ROOT).resolve("system-database")));
            FileAgentStore store = this.own(new FileAgentStore(server.getWorldPath(LevelResource.ROOT).resolve("system-agent")));
            OpenAiCompatibleClient model = this.own(new OpenAiCompatibleClient());
            this.engine = this.own(new AgentEngine(store, model, data -> ModuleData.get(data, CoreSystemModule.JOURNAL)));
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
            ModuleContext context = new ModuleContext(this.engine, server.registryAccess().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE));
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
        PromptReloadListener listener = this.server.getServerResources().managers().getListener(PromptReloadListener.KEY);
        if (this.promptListener == listener) return;
        // Publish only the listener retained by successfully installed server resources.
        this.engine.prompts(listener.prompts());
        this.promptListener = listener;
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
            runtime.moduleManager.tick();
            runtime.gameQueue.tick();
        }
    }

    @SubscribeEvent
    public static void reloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(PromptReloadListener.KEY, new PromptReloadListener());
    }
}
