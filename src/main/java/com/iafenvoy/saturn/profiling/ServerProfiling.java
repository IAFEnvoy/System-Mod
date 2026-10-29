package com.iafenvoy.saturn.profiling;

import com.iafenvoy.saturn.Saturn;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Only the owning server thread is sampled; worker threads need their own verified tick boundaries.
@EventBusSubscriber
public final class ServerProfiling {
    private static final ProfilerScope NOOP = () -> {
    };
    private static volatile Session current;

    private ServerProfiling() {
    }

    public static boolean enabled(MinecraftServer server) {
        Session session = current;
        return session != null && session.server == server && session.profiler.enabled();
    }

    public static boolean setEnabled(MinecraftServer server, boolean enabled) {
        Session session = current;
        if (session == null || session.server != server || !server.isSameThread()) return false;
        if (session.profiler.enabled() != enabled) {
            session.profiler.setEnabled(enabled);
            session.active = 0L;
            session.epoch++;
        }
        return true;
    }

    public static CompletableFuture<ProfilerWindowSnapshot> snapshot(MinecraftServer server) {
        Session session = current;
        return session != null && session.server == server ? session.profiler.snapshot()
                : CompletableFuture.completedFuture(ProfilerWindowSnapshot.EMPTY);
    }

    public static ProfilerScope scope(Source source) {
        Session session = current;
        if (session == null || !session.profiler.enabled() || !session.server.isSameThread()) return NOOP;
        return session.scope(source);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerAboutToStartEvent event) {
        Session previous = current;
        current = new Session(event.getServer());
        setEnabled(event.getServer(), Saturn.isDevMode());
        if (previous != null) previous.profiler.close();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        Session session = current;
        if (session == null || session.server != event.getServer()) return;
        session.profiler.flushTick();
        session.active = 0L;
        session.epoch++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopped(ServerStoppedEvent event) {
        Session session = current;
        if (session == null || session.server != event.getServer()) return;
        current = null;
        session.profiler.close();
    }

    /**
     * One bucket a scope can be opened under, named by an id: Saturn registers its own at class initialisation and a
     * dependent mod registers its own from its mod setup ({@link #register(String)} or {@link #register(String, Source)}
     * for a nested bucket).
     *
     * <p>The registration list is read once when a server starts, so a source registered after that is simply not
     * sampled for that session; register during mod construction. Two sources with one id are one bucket, first
     * registration wins.
     */
    public static final class Source {
        private static final Map<String, Source> REGISTERED = new LinkedHashMap<>();

        public static final Source RESOURCE = register("resource");
        public static final Source ABILITY = register("ability");
        public static final Source TRIGGER = register("trigger");
        public static final Source ITEM = register("item");
        public static final Source ENTITY = register("entity");
        public static final Source COST = register("cost");
        public static final Source ABILITY_RECONCILE = register("ability/reconcile", ABILITY);
        public static final Source ABILITY_CAST = register("ability/cast", ABILITY);
        public static final Source ABILITY_CHANNEL = register("ability/channel", ABILITY);

        private final String path;
        private final String id;
        private final Source parent;
        private final int index;

        private Source(String path, Source parent, int index) {
            this.path = path;
            this.id = "server/" + path;
            this.parent = parent;
            this.index = index;
        }

        // The id is what a command names and what a translation key is built from; a null or blank one is refused.
        public static Source register(String path) {
            return register(path, null);
        }

        public static Source register(String path, Source parent) {
            if (path == null || path.isBlank())
                throw new IllegalArgumentException("A profiling source needs an id");
            String id = "server/" + path;
            Source existing = REGISTERED.get(id);
            if (existing != null) return existing;
            Source created = new Source(path, parent, REGISTERED.size());
            REGISTERED.put(id, created);
            return created;
        }

        public static Source byPath(String path) {
            return REGISTERED.get(path.startsWith("server/") ? path : "server/" + path);
        }

        public static List<Source> all() {
            return List.copyOf(REGISTERED.values());
        }

        public static int count() {
            return REGISTERED.size();
        }

        public String path() {
            return this.path;
        }

        public String id() {
            return this.id;
        }

        public int index() {
            return this.index;
        }

        public String translationKey() {
            return "saturn.profiling.saturn." + this.path.replace('/', '.');
        }

        // What this bucket counts, one line, shown while the pointer rests on a row's source label.
        public String tooltipKey() {
            return this.translationKey() + ".tooltip";
        }
    }

    private static final class Session {
        private final MinecraftServer server;
        private final Profiler profiler = new Profiler();
        private final ProfilerSource[] sources;
        private long active;
        private long epoch;

        private Session(MinecraftServer server) {
            this.server = server;
            List<Source> all = Source.all();
            this.sources = new ProfilerSource[all.size()];
            for (Source source : all) this.sources[source.index()] = this.profiler.sources().register(source.id());
        }

        // Reentrant calls share their outermost source; named children also open their parent when entered alone.
        private ProfilerScope scope(Source source) {
            long bit = 1L << source.index();
            if ((this.active & bit) != 0L) return NOOP;
            ProfilerScope parent = source.parent == null ? NOOP : this.scope(source.parent);
            this.active |= bit;
            ProfilerScope sample = this.profiler.scope(this.sources[source.index()]);
            long opened = this.epoch;
            return new ProfilerScope() {
                private boolean closed;

                @Override
                public void close() {
                    if (this.closed || !Session.this.server.isSameThread()) return;
                    this.closed = true;
                    try {
                        sample.close();
                    } finally {
                        if (Session.this.epoch == opened) Session.this.active &= ~bit;
                        parent.close();
                    }
                }
            };
        }
    }
}
