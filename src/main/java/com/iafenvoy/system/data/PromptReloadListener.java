package com.iafenvoy.system.data;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retained with server resources; failed reloads cannot replace the active engine's prompt catalog.
 */
public final class PromptReloadListener extends SimplePreparableReloadListener<SystemPrompts> {
    // 1.21.1 has no keyed retained-listener registry, so the last successfully applied instance publishes itself.
    private static volatile PromptReloadListener latest;
    private static final String DIRECTORY = "system/prompts";
    private SystemPrompts prompts;

    @Override
    protected SystemPrompts prepare(ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".txt"));
        Map<String, String> definitions = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":" + path.substring(DIRECTORY.length() + 1, path.length() - 4);
            definitions.put(id, read(entry));
        });
        return SystemPrompts.create(definitions);
    }

    private static String read(Map.Entry<ResourceLocation, Resource> entry) {
        try (InputStream input = entry.getValue().open()) {
            byte[] bytes = input.readAllBytes();
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid prompt resource " + entry.getKey(), error);
        }
    }

    @Override
    protected void apply(SystemPrompts prepared, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        this.prompts = prepared;
        latest = this;
    }

    /**
     * Returns the prompt set installed by the most recent successful reload, or {@code null} before the first one.
     */
    public static PromptReloadListener latest() {
        return latest;
    }

    public SystemPrompts prompts() {
        if (this.prompts == null) throw new IllegalStateException("Prompts have not loaded");
        return this.prompts;
    }
}
