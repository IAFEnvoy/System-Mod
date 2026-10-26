package com.iafenvoy.system.runtime.reload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ListenerKey;
import org.jspecify.annotations.NonNull;

/** Retained with server resources; failed reloads cannot replace the active engine's prompt catalog. */
public final class PromptReloadListener extends SimplePreparableReloadListener<SystemPrompts> {
    public static final ListenerKey<PromptReloadListener> KEY = ListenerKey.create(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "prompts"));
    private static final String DIRECTORY = "system/prompts";
    private SystemPrompts prompts;

    @Override
    protected SystemPrompts prepare(ResourceManager manager, @NonNull ProfilerFiller profiler) {
        Map<Identifier, Resource> resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".txt"));
        if (resources.size() > SystemPrompts.MAX_TEMPLATES) throw new IllegalArgumentException("Too many prompt resources");
        Map<String, String> definitions = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":" + path.substring(DIRECTORY.length() + 1, path.length() - 4);
            definitions.put(id, read(entry));
        });
        return SystemPrompts.create(definitions);
    }

    private static String read(Map.Entry<Identifier, Resource> entry) {
        try (InputStream input = entry.getValue().open()) {
            byte[] bytes = input.readNBytes(SystemPrompts.MAX_TEMPLATE_BYTES + 1);
            if (bytes.length > SystemPrompts.MAX_TEMPLATE_BYTES) throw new IllegalArgumentException("Prompt resource exceeds byte limit");
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid prompt resource " + entry.getKey(), error);
        }
    }

    @Override
    protected void apply(SystemPrompts prepared, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        this.prompts = prepared;
    }

    public SystemPrompts prompts() {
        if (this.prompts == null) throw new IllegalStateException("Prompts have not loaded");
        return this.prompts;
    }
}
