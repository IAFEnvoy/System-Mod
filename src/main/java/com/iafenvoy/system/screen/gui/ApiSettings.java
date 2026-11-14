package com.iafenvoy.system.screen.gui;

import com.google.gson.JsonObject;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.config.credential.Credential;
import com.iafenvoy.system.config.credential.CredentialEntry;
import com.iafenvoy.system.config.credential.CredentialManager;
import com.iafenvoy.system.config.file.ApiConfig;
import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.config.file.SearchConfig;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.data.ClientState;
import com.iafenvoy.system.registry.SystemRegistries;
import com.iafenvoy.system.registry.SystemServices;
import com.iafenvoy.system.search.SearchProvider;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/** Credential-owned editors and typed service selection; secrets stay in this process. */
final class ApiSettings {
    private final Document document;
    private final boolean serverEditable;
    private final Runnable changed;
    private final Consumer<String> status;
    private final List<Selector<?>> selectors = new ArrayList<>();
    private boolean rendering;
    private ApiConfig displayed;

    ApiSettings(Document document, boolean serverEditable, Runnable changed, Consumer<String> status) {
        this.document = document;
        this.serverEditable = serverEditable;
        this.changed = changed;
        this.status = status;
        // Remote clients have no server configuration controls or service instances.
        if (this.serverEditable) {
            this.selectors.add(new Selector<>("model", "api_model", SystemServices.MODEL, ModelClient::configured, service -> {
                ModelConfig.INSTANCE.set(service.defaults(ModelConfig.INSTANCE.get()));
                return ModelConfig.INSTANCE.save();
            }));
            this.selectors.add(new Selector<>("search", "api_search", SystemServices.SEARCH, SearchProvider::configured, service -> {
                SearchConfig.INSTANCE.set(new SearchConfig(service.defaultBaseUrl(), SearchConfig.INSTANCE.get().maxResponseBytes()));
                return SearchConfig.INSTANCE.save();
            }));
        }
        this.selectors.add(new Selector<>("tts", "speech_api", SystemServices.TTS, TtsProvider::configured, service -> {
            SpeechConfig.INSTANCE.set(service.defaults(SpeechConfig.INSTANCE.get()));
            return SpeechConfig.INSTANCE.save();
        }));
        this.selectors.add(new Selector<>("asr", "asr_api", SystemServices.ASR, AsrProvider::configured, _ -> true));
    }

    void init() {
        bind(this.document, "api_add", () -> {
            try {
                Identifier type = Identifier.parse(this.document.getElementById("api_new_type").getValue());
                MapCodec<? extends Credential> codec = SystemRegistries.CREDENTIAL_TYPE.getValue(type);
                if (codec == null) return;
                Credential credential = codec.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
                this.save(ApiConfig.INSTANCE.get().put(new CredentialEntry(UUID.randomUUID().toString(), typeLabel(type), credential)));
                this.render();
            } catch (RuntimeException invalid) {
                // Codec exceptions may contain credential values; log only the exception type.
                SystemMod.LOGGER.warn("Failed to create API credential ({})", invalid.getClass().getName());
                this.status.accept(tr("system.api.create_error"));
            }
        });
        Element types = this.document.getElementById("api_new_type");
        types.setAttribute("aria-label", tr("system.api.new_type"));
        for (Identifier id : SystemRegistries.CREDENTIAL_TYPE.keySet()) option(types, id.toString(), typeLabel(id));
        types.setValue("system:deepseek");
        for (Selector<?> selector : this.selectors)
            this.document.getElementById(selector.prefix() + "_credential").addEventListener("change", _ -> {
                if (this.rendering) return;
                this.save(ApiConfig.INSTANCE.get().bind(selector.usage(), this.document.getElementById(selector.prefix() + "_credential").getValue()));
                this.renderSelectors();
            });
        display(this.document.getElementById("api_server_notice"), this.serverEditable ? "none" : "block");
        text(this.document.getElementById("api_hint"), tr(this.serverEditable ? "system.api.hint" : "system.api.client_hint"));
        for (String id : List.of("api_model_settings", "api_search_settings"))
            display(this.document.getElementById(id), this.serverEditable ? "block" : "none");
        for (String id : List.of("api_model_url", "api_model_name", "api_search_url")) {
            Element input = this.document.getElementById(id);
            input.setDisabled(!this.serverEditable);
            if (this.serverEditable) input.addEventListener("input", _ -> this.saveConnection(id.equals("api_search_url")));
        }
        this.render();
    }

    private static String typeLabel(Identifier id) {
        String key = id.getNamespace().equals("system") ? "system.api.type." + id.getPath() : id.toLanguageKey("credential");
        String translated = tr(key);
        return translated.equals(key) ? id.toString() : translated;
    }

    private void saveConnection(boolean search) {
        if (this.rendering || !this.serverEditable) return;
        try {
            boolean saved;
            if (!search) {
                synchronized (ModelConfig.INSTANCE) {
                    ModelConfig old = ModelConfig.INSTANCE.get();
                    ModelConfig model = new ModelConfig(this.document.getElementById("api_model_name").getValue().strip(),
                            this.document.getElementById("api_model_url").getValue().strip(), old.providerType(), old.thinkingEnabled(),
                            old.reasoningEffort(), old.apiKeyRequired(), old.jsonMode(), old.outputTokenParameter());
                    model.chatCompletionsEndpoint();
                    if (model.model().isBlank()) throw new IllegalArgumentException("Empty model");
                    ModelConfig.INSTANCE.set(model);
                    saved = ModelConfig.INSTANCE.save();
                }
            } else {
                synchronized (SearchConfig.INSTANCE) {
                    SearchConfig config = new SearchConfig(this.document.getElementById("api_search_url").getValue().strip(), SearchConfig.INSTANCE.get().maxResponseBytes());
                    config.searchEndpoint();
                    SearchConfig.INSTANCE.set(config);
                    saved = SearchConfig.INSTANCE.save();
                }
            }
            this.status.accept(tr(saved ? "system.ui.settings_saved" : "system.ui.settings_config_error"));
        } catch (IllegalArgumentException invalid) {
            this.status.accept(tr("system.ui.settings_config_error"));
        }
    }

    void refresh() {
        if (this.displayed != ApiConfig.INSTANCE.get()) {
            this.render();
            this.changed.run();
        }
        this.renderServerStatus();
    }

    private void render() {
        this.renderSelectors();
        this.renderProfiles();
        this.renderConnections();
        this.displayed = ApiConfig.INSTANCE.get();
    }

    private void renderConnections() {
        if (!this.serverEditable) return;
        this.rendering = true;
        try {
            this.connectionValue("api_model_url", ModelConfig.INSTANCE.get().baseUrl());
            this.connectionValue("api_model_name", ModelConfig.INSTANCE.get().model());
            this.connectionValue("api_search_url", SearchConfig.INSTANCE.get().baseUrl());
        } finally {
            this.rendering = false;
        }
    }

    private void connectionValue(String id, String value) {
        Element input = this.document.getElementById(id);
        if (!input.getValue().equals(value)) input.setValue(value);
    }

    private void renderSelectors() {
        this.rendering = true;
        try {
            ApiConfig config = ApiConfig.INSTANCE.get();
            for (Selector<?> selector : this.selectors) {
                Element credential = this.document.getElementById(selector.prefix() + "_credential");
                credential.setInnerHTML("");
                option(credential, "", tr("system.api.none"));
                for (CredentialEntry entry : config.credentials())
                    if (selector.manager().supports(entry.credential())) option(credential, entry.id(), entry.label());
                credential.setValue(config.binding(selector.usage()));
            }
            this.renderStatuses();
        } finally {
            this.rendering = false;
        }
    }

    private void renderStatuses() {
        ApiConfig config = ApiConfig.INSTANCE.get();
        for (Selector<?> selector : this.selectors)
            text(this.document.getElementById(selector.prefix() + "_status"), tr(selector.configured(config) ? "system.api.ready" : "system.api.incomplete"));
        this.renderServerStatus();
    }

    private void renderServerStatus() {
        if (this.serverEditable) return;
        ClientState state = AssistantClient.state().serverState();
        Element status = this.document.getElementById("api_server_model_status");
        // Unbound clients have not received a server readiness snapshot.
        display(status, state.system().isPresent() ? "block" : "none");
        if (state.system().isPresent())
            text(status, tr(state.ai().modelConfigured() ? "system.api.server_model_ready" : "system.api.server_model_missing"));
    }

    private void renderProfiles() {
        this.rendering = true;
        try {
            Element parent = this.document.getElementById("api_credentials");
            parent.setInnerHTML("");
            try (Document.ContextScope ignored = Document.withContext(this.document)) {
                for (CredentialEntry profile : ApiConfig.INSTANCE.get().credentials())
                    parent.appendChild(this.profileCard(profile));
            }
        } finally {
            this.rendering = false;
        }
    }

    private Element profileCard(CredentialEntry profile) {
        Element card = element(this.document, "div", "result_card speech_step api_credential", "");
        Element header = element(this.document, "div", "api_credential_header", "");
        Identifier type = SystemRegistries.CREDENTIAL_TYPE.getKey(profile.credential().codec());
        header.appendChild(element(this.document, "h2", "", type == null ? tr("system.api.unknown_type") : typeLabel(type)));
        Element delete = Element.init(element(this.document, "button", "", tr("system.api.delete")));
        delete.setAttribute("type", "button");
        delete.addEventListener("click", _ -> {
            this.save(ApiConfig.INSTANCE.get().remove(profile.id()));
            this.render();
        });
        header.appendChild(delete);
        card.appendChild(header);

        Element label = element(this.document, "label", "api_credential_name", "");
        label.appendChild(element(this.document, "span", "", tr("system.api.name")));
        Element name = Element.init(this.document.createElement("input"));
        name.setAttribute("type", "text");
        name.setAttribute("autocomplete", "off");
        name.setValue(profile.name());
        name.addEventListener("input", _ -> {
            if (this.rendering) return;
            ApiConfig config = ApiConfig.INSTANCE.get();
            config.entry(profile.id()).ifPresent(current -> {
                if (name.getValue().equals(current.name())) return;
                this.save(config.put(new CredentialEntry(current.id(), name.getValue(), current.credential())));
                this.renderSelectors();
            });
        });
        label.appendChild(name);
        card.appendChild(label);

        // Each card edits its own detached snapshot; typing never rebuilds these controls.
        Credential editing = copy(profile.credential());
        Element widget = editing.createWidget();
        card.appendChild(widget);
        Runnable save = () -> {
            if (this.rendering) return;
            try {
                ApiConfig config = ApiConfig.INSTANCE.get();
                config.entry(profile.id()).ifPresent(current -> {
                    String value = CodecIO.write(Credential.CODEC, editing);
                    // Some controls emit input followed by change for the same value.
                    if (value.equals(CodecIO.write(Credential.CODEC, current.credential()))) return;
                    this.save(config.put(new CredentialEntry(current.id(), current.name(), CodecIO.parse(Credential.CODEC, value))));
                    this.renderStatuses();
                });
            } catch (RuntimeException invalid) {
                this.status.accept(tr("system.ui.settings_config_error"));
            }
        };
        // Bubble after each field's own listener has updated the detached credential.
        widget.addEventListener("input", _ -> save.run());
        widget.addEventListener("change", _ -> save.run());
        return card;
    }

    private static Credential copy(Credential credential) {
        return CodecIO.parse(Credential.CODEC, CodecIO.write(Credential.CODEC, credential));
    }

    private void save(ApiConfig config) {
        boolean saved;
        // The same lock order is used when capturing endpoint + credential for a request.
        if (this.serverEditable) {
            synchronized (ModelConfig.INSTANCE) {
                synchronized (SearchConfig.INSTANCE) {
                    saved = this.saveCredentials(config);
                }
            }
        } else saved = this.saveCredentials(config);
        this.renderConnections();
        this.status.accept(tr(saved ? "system.ui.settings_saved" : "system.ui.settings_config_error"));
        this.changed.run();
    }

    private boolean saveCredentials(ApiConfig config) {
        synchronized (SpeechConfig.INSTANCE) {
            synchronized (ApiConfig.INSTANCE) {
                ApiConfig previous = ApiConfig.INSTANCE.get();
                boolean saved = true;
                for (Selector<?> selector : this.selectors) {
                    Class<?> oldType = previous.selected(selector.usage()).map(Object::getClass).orElse(null);
                    Class<?> newType = config.selected(selector.usage()).map(Object::getClass).orElse(null);
                    if (newType != null && newType != oldType) saved &= selector.applyDefaults(config);
                }
                ApiConfig.INSTANCE.set(config);
                this.displayed = config;
                return ApiConfig.INSTANCE.save() && saved;
            }
        }
    }

    private static void option(Element select, String value, String label) {
        Element option = element(select.document, "option", "", label);
        option.setAttribute("value", value);
        select.appendChild(option);
    }

    private record Selector<S extends AutoCloseable>(String usage, String prefix,
                                                    CredentialManager<S> manager, Predicate<S> ready, Predicate<S> defaults) {
        boolean configured(ApiConfig config) {
            try (S service = config.create(this.usage, this.manager).orElse(null)) {
                return service != null && this.ready.test(service);
            } catch (Exception unavailable) {
                return false;
            }
        }

        boolean applyDefaults(ApiConfig config) {
            try (S service = config.create(this.usage, this.manager).orElse(null)) {
                return service == null || this.defaults.test(service);
            } catch (Exception unavailable) {
                return false;
            }
        }
    }
}
