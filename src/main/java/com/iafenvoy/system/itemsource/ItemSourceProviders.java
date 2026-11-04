package com.iafenvoy.system.itemsource;

import com.iafenvoy.system.SystemMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of source providers, ordered by ID so the JSON is stable between runs.
 * <p>
 * Registration is expected during mod construction. Lookups happen on the server thread and never hold the registry
 * lock while a provider runs, so a provider cannot block the game thread behind another provider's work.
 */
public final class ItemSourceProviders {
    /**
     * Scope that runs every provider; also the default when a caller does not ask for a narrower one.
     */
    public static final String ALL = "all";

    private static final Map<Identifier, ItemSourceProvider> PROVIDERS = new ConcurrentHashMap<>();

    static {
        register(new RecipeItemSourceProvider());
        register(new LootItemSourceProvider());
        register(new TagItemSourceProvider());
    }

    private ItemSourceProviders() {
    }

    public static void register(ItemSourceProvider provider) {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(provider.id());
        ItemSourceProvider previous = PROVIDERS.putIfAbsent(provider.id(), provider);
        if (previous != null && previous != provider)
            throw new IllegalArgumentException("Duplicate item source provider " + provider.id());
    }

    public static List<Identifier> ids() {
        return PROVIDERS.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    /**
     * Scopes accepted by {@link #find(ServerLevel, Item, String)}: {@link #ALL} plus every provider path and full ID.
     */
    public static List<String> scopes() {
        List<String> scopes = new ArrayList<>();
        scopes.add(ALL);
        for (Identifier id : ids()) {
            if (!scopes.contains(id.getPath())) scopes.add(id.getPath());
        }
        return List.copyOf(scopes);
    }

    /**
     * @param scope {@link #ALL}, a provider path such as {@code recipe}, or a full provider ID
     * @return false when no registered provider matches, so callers can reject the request instead of returning
     * an empty list that looks like "this item has no source"
     */
    public static boolean isKnownScope(String scope) {
        String normalized = normalize(scope);
        if (normalized.isEmpty()) return true;
        for (Identifier id : PROVIDERS.keySet())
            if (matches(id, normalized)) return true;
        return false;
    }

    public static List<ItemSource> find(ServerLevel level, Item item) {
        return find(level, item, ALL);
    }

    /**
     * Runs every matching provider in ID order. A broken optional integration must not disable item sources as a whole.
     */
    public static List<ItemSource> find(ServerLevel level, Item item, String scope) {
        String normalized = normalize(scope);
        List<ItemSource> sources = new ArrayList<>();
        for (ItemSourceProvider provider : sorted()) {
            if (!normalized.isEmpty() && !matches(provider.id(), normalized)) continue;
            try {
                sources.addAll(provider.find(level, item));
            } catch (RuntimeException | LinkageError error) {
                SystemMod.LOGGER.error("Item source provider {} failed", provider.id(), error);
            }
        }
        return sources;
    }

    private static String normalize(String scope) {
        return scope == null ? "" : scope.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(Identifier id, String scope) {
        return scope.isEmpty() || scope.equals(ALL)
                || scope.equals(id.getPath()) || scope.equals(id.toString().toLowerCase(Locale.ROOT));
    }

    private static List<ItemSourceProvider> sorted() {
        return PROVIDERS.values().stream().sorted(Comparator.comparing(provider -> provider.id().toString())).toList();
    }
}
