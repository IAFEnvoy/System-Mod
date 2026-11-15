package com.iafenvoy.system.screen.emotion;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.util.CodecIO;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

/**
 * Client-only emotion/<path>.json definitions; malformed overrides fall back to a lower pack.
 */
@EventBusSubscriber(Dist.CLIENT)
public enum EmotionProfiles implements ResourceManagerReloadListener {
    INSTANCE;
    public static final Identifier DEFAULT = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "panda");
    private static final String DIRECTORY = "emotion";
    private static final EmotionFrame MISSING = new EmotionFrame(MissingTextureAtlasSprite.getLocation());
    private static Map<Identifier, EmotionProfile> profiles = Map.of();
    private static Set<Identifier> textures = Set.of();
    private static final Set<String> WARNED = new HashSet<>();
    private static long revision;

    @SubscribeEvent
    public static void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "emotion"), INSTANCE);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<Identifier, EmotionProfile> loaded = new LinkedHashMap<>();
        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            String path = resourceId.getPath();
            Identifier id = Identifier.fromNamespaceAndPath(resourceId.getNamespace(), path.substring(DIRECTORY.length() + 1, path.length() - 5));
            for (Resource layer : manager.getResourceStack(resourceId).reversed()) {
                try (InputStream stream = layer.open()) {
                    EmotionProfile profile = CodecIO.parse(EmotionProfile.CODEC, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    validate(profile);
                    loaded.put(id, profile);
                    break;
                } catch (Exception error) {
                    SystemMod.LOGGER.warn("Unable to load emotion profile {}", resourceId, error);
                }
            }
        });
        profiles = Map.copyOf(loaded);
        Set<Identifier> available = new HashSet<>();
        profiles.values().stream().flatMap(profile -> profile.states().values().stream())
                .flatMap(state -> Stream.concat(Stream.of(state.texture()), state.speaking().stream()))
                .map(EmotionFrame::texture).distinct()
                .filter(texture -> texture.getPath().startsWith("textures/") && manager.getResource(texture).isPresent())
                .forEach(available::add);
        textures = Set.copyOf(available);
        WARNED.clear();
        revision++;
    }

    private static void validate(EmotionProfile profile) {
        profile.states().keySet().forEach(state -> {
            if (Identifier.tryParse(state) == null || !state.contains(":"))
                throw new IllegalArgumentException("Emotion states require namespaced IDs: " + state);
        });
        if (!profile.states().containsKey(profile.defaultState()))
            throw new IllegalArgumentException("Unknown default emotion " + profile.defaultState());
        Stream.concat(profile.transitions().values().stream(), profile.states().values().stream().flatMap(state ->
                Stream.concat(state.transitions().values().stream(), Stream.of(state.next()).filter(next -> !next.isBlank()))))
                .forEach(target -> {
                    if (!profile.states().containsKey(target)) throw new IllegalArgumentException("Unknown emotion target " + target);
                });
    }

    public static EmotionProfile get(Identifier id) {
        if (revision > 0 && !profiles.containsKey(id) && WARNED.add(id + "/unknown-profile"))
            SystemMod.LOGGER.warn("Missing emotion profile {}; using {}", id, DEFAULT);
        return profiles.getOrDefault(id, profiles.get(DEFAULT));
    }

    public static Map<Identifier, EmotionProfile> all() {
        return profiles;
    }

    public static long revision() {
        return revision;
    }

    /** Texture presence is cached at resource reload; sampling an animation never reads files. */
    public static EmotionFrame frame(Identifier id, String state, EmotionFrame requested) {
        if (textures.contains(requested.texture())) return requested;
        EmotionProfile profile = get(id);
        EmotionFrame fallback = profile == null ? null : profile.states().get(profile.defaultState()).texture();
        if (WARNED.add(id + "/" + state + "/" + requested.texture()))
            SystemMod.LOGGER.warn("Missing emotion texture: profile={}, state={}, texture={}; using default {}",
                    id, state, requested.texture(), fallback == null ? null : fallback.texture());
        if (fallback != null && textures.contains(fallback.texture())) return fallback;
        return missing(id, fallback == null ? null : fallback.texture());
    }

    public static EmotionFrame missing(Identifier id, Identifier fallback) {
        if (WARNED.add(id + "/missing-default"))
            SystemMod.LOGGER.warn("Missing default emotion texture: profile={}, texture={}; rendering purple/black missing texture", id, fallback);
        return MISSING;
    }

    public static void unknown(Identifier id, String state) {
        if (!state.isBlank() && WARNED.add(id + "/unknown/" + state))
            SystemMod.LOGGER.warn("Unknown emotion state: profile={}, state={}; using default", id, state);
    }
}
