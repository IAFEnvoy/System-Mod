package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.builtin.BuiltinModules;
import com.iafenvoy.system.agent.builtin.tool.*;
import com.iafenvoy.system.agent.commerce.CommerceTransactions;
import com.iafenvoy.system.agent.model.OpenAiCompatibleClient;
import com.iafenvoy.system.agent.store.FileAgentStore;
import com.iafenvoy.system.assistant.usage.AssistantUsage;
import com.iafenvoy.system.data.CatalogReloadListener;
import com.iafenvoy.system.data.PromptReloadListener;
import com.iafenvoy.system.event.AssistantRegistrationEvent;
import com.iafenvoy.system.network.ServerNetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server composition and event entry points; player sessions, requests and usage have separate owners.
 */
@EventBusSubscriber
public final class AssistantServer {
    private static final Map<MinecraftServer, AssistantServer> SERVERS = new ConcurrentHashMap<>();
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final AssistantPlayers players;
    private final AssistantRequests requests;
    private final AssistantUsage usage;
    private final GameTaskQueue gameQueue = new GameTaskQueue();
    private CatalogReloadListener catalogListener;
    private PromptReloadListener promptListener;

    private AssistantServer(MinecraftServer server) throws Exception {
        this.server = server;
        this.catalogListener = server.getServerResources().managers().getListener(CatalogReloadListener.KEY);
        this.engine = new AgentEngine(this.catalogListener.catalog(),
                new FileAgentStore(server.getWorldPath(LevelResource.ROOT).resolve("system-agent")), new OpenAiCompatibleClient());
        try {
            this.engine.questNarration().cacheFile(server.getWorldPath(LevelResource.ROOT).resolve("system-quest-backgrounds.json"));
            this.usage = new AssistantUsage(server, this.engine);
            this.refreshPrompts();
            MinecraftTools game = new MinecraftTools(server, this.gameQueue);
            CommerceTransactions transactions = new CommerceTransactions(this.engine, game);
            this.installModules(game, transactions);
            this.players = new AssistantPlayers(server, this.engine, transactions, this.usage);
            this.players.validateRules();
            this.requests = new AssistantRequests(server, this.engine, this.players);
            this.engine.start();
        } catch (Exception e) {
            this.engine.close();
            throw e;
        }
    }

    private void installModules(MinecraftTools game, CommerceTransactions transactions) {
        BuiltinModules.install(this.engine, List.of(new PlayerQueryTool(game).tool(), new ItemQueryTool(game).tool(),
                new DeliverTool(this.engine, game).tool(), new ShopPurchaseTool(transactions).tool(), new LotteryDrawTool(transactions).tool(),
                new CommercePreviewTool(transactions).tool()));
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
    }

    public static AssistantServer get(MinecraftServer server) {
        return SERVERS.get(server);
    }

    public AgentEngine engine() {
        return this.engine;
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
            runtime.usage.drain();
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
