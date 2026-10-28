package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.request.PreferencesRequest;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.iafenvoy.system.speech.asr.RecognitionCapture;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.speech.tts.SpeechState;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * Each control commits its own settings immediately; server preferences still wait for acknowledgement.
 */
public final class AssistantSettingsScreen extends AssistantInputScreen {
    public enum Tab {
        GENERAL, TTS, ASR;

        private String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private final Screen parent;
    private Tab tab;
    // Forms may contain incomplete connection details; providers validate only when actually used.
    private SpeechConfig speechForm = SpeechConfig.INSTANCE.get();
    private RecognitionConfig asrForm = RecognitionConfig.INSTANCE.get();
    private boolean simpleMode = AssistantClient.simpleMode();
    private boolean saveHistory = AssistantClient.state().saveHistory();
    private boolean externalModel = AssistantClient.state().externalModel();
    private boolean savingPreferences;
    private boolean invalidVolume;
    private String generalStatus = "";
    private SpeechState speechStatus;
    private String asrStatus = "idle";
    private String displayedStatus;

    public AssistantSettingsScreen(Screen parent, Tab tab) {
        super("system/assistant-settings.html", "");
        this.parent = parent;
        this.tab = tab;
        this.setPauseGame(false);
        this.setShowDefaultBackground(false);
        if (parent instanceof AssistantInputScreen screen) {
            Document document = screen.getLinkedDocument();
            Element message = document == null ? null : document.getElementById("message");
            if (message != null) screen.draft = message.getValue();
        }
    }

    @Override
    protected void initPage(Document document) {
        super.initPage(document);
        for (String id : List.of("settings_title", "settings_back", "settings_view_heading", "settings_mode_full", "settings_mode_simple"))
            text(document.getElementById(id), tr("system.ui." + id));
        for (String id : List.of("privacy_heading", "privacy_text"))
            text(document.getElementById(id), tr("system.ui." + id));
        text(document.getElementById("settings_history_label"), tr("system.ui.history_toggle"));
        text(document.getElementById("settings_model_label"), tr("system.ui.model_toggle"));
        text(document.getElementById("settings_speech_enabled_label"), tr("system.tts.ui.enabled"));
        text(document.getElementById("settings_speech_notification_sound_label"), tr("system.tts.ui.notification_sound"));
        text(document.getElementById("speech_notification_sound_hint"), tr("system.tts.ui.notification_sound_hint"));
        text(document.getElementById("settings_speech_cache_label"), tr("system.tts.ui.cache_enabled"));
        text(document.getElementById("speech_cache_hint"), tr("system.tts.ui.cache_hint"));
        for (String part : List.of("requirements", "rewards", "description"))
            text(document.getElementById("speech_quest_" + part + "_label"), tr("system.tts.ui.quest_" + part));
        text(document.getElementById("speech_quest_hint"), tr("system.tts.ui.quest_hint"));
        text(document.getElementById("speech_language_label"), tr("system.tts.ui.language"));
        text(document.getElementById("settings_asr_enabled_label"), tr("system.asr.ui.enabled"));
        text(document.getElementById("settings_asr_auto_send_label"), tr("system.asr.ui.auto_send"));
        for (Tab value : Tab.values()) {
            String id = "settings_tab_" + value.key();
            text(document.getElementById(id), tr("system.ui." + id));
            bind(document, id, () -> this.selectTab(value));
        }
        for (String id : List.of("speech_note", "speech_key_step", "speech_voice_step", "speech_key_label", "speech_workspace_label",
                "speech_model_label", "speech_voice_label", "speech_volume_label", "speech_hint", "speech_preview", "speech_stop"))
            text(document.getElementById(id), tr("system.tts.ui." + id));
        for (String id : List.of("asr_note", "asr_key_step", "asr_appkey_label", "asr_token_label", "asr_hint"))
            text(document.getElementById(id), tr("system.asr.ui." + id));
        document.getElementById("speech_key").setAttribute("placeholder", tr("system.tts.ui.key_placeholder"));
        Element languageSelect = document.getElementById("speech_language");
        for (String language : SpeechConfig.LANGUAGES) {
            Element option = document.createElement("option");
            option.setAttribute("value", language);
            option.setTextContent(tr("system.tts.language." + language));
            languageSelect.appendChild(option);
        }
        this.fillSpeechForm(document);
        document.getElementById("asr_appkey").setValue(this.asrForm.appKey());
        document.getElementById("asr_token").setValue(this.asrForm.token());
        text(document.getElementById("asr_backend"), tr(RecognitionCapture.voicechatInstalled()
                ? "system.asr.ui.backend_voicechat" : "system.asr.ui.backend_java"));
        for (Element input : document.querySelectorAll("#settings_page_tts input:not([type=checkbox])"))
            input.addEventListener("input", event -> {
                this.readSpeechForm(document);
                this.saveSpeech();
            });
        for (Element input : document.querySelectorAll("#settings_page_asr input:not([type=checkbox])"))
            input.addEventListener("input", event -> {
                this.readAsrForm(document);
                this.saveAsr();
            });
        for (String mode : List.of("full", "simple"))
            bind(document, "settings_mode_" + mode, () -> {
                this.simpleMode = mode.equals("simple");
                try {
                    AssistantClient.configureMode(this.simpleMode);
                    this.generalStatus = tr(ClientConfig.INSTANCE.save() ? "system.ui.settings_saved" : "system.ui.settings_config_error");
                } catch (Exception error) {
                    this.generalStatus = tr("system.ui.settings_config_error");
                }
                this.renderControls();
            });
        bindSwitch(document, "history_toggle", checked -> {
            this.saveHistory = checked;
            this.savePreferences();
        });
        bindSwitch(document, "model_toggle", checked -> {
            this.externalModel = checked;
            this.savePreferences();
        });
        bindSwitch(document, "speech_enabled", checked ->
                this.changeSpeech(checked, this.speechForm.region(), this.speechForm.language()));
        for (String id : List.of("speech_notification_sound", "speech_cache", "speech_quest_requirements", "speech_quest_rewards", "speech_quest_description"))
            bindSwitch(document, id, checked -> {
                this.readSpeechForm(document);
                this.saveSpeech();
            });
        bind(document, "speech_region", () -> this.changeSpeech(this.speechForm.enabled(),
                this.speechForm.region().equals("beijing") ? "singapore" : "beijing", this.speechForm.language()));
        languageSelect.addEventListener("change", event -> {
            this.readSpeechForm(document);
            this.saveSpeech();
        });
        bind(document, "speech_preview", () -> {
            this.readSpeechForm(document);
            try {
                if (this.invalidVolume) throw new SpeechException("settings");
                this.speech().preview(this.speechForm);
                this.speechStatus = null;
            } catch (SpeechException error) {
                this.speechStatus = SpeechState.fromCode(error.code());
            }
        });
        bind(document, "speech_stop", () -> {
            this.stopSpeech();
            this.speechStatus = null;
        });
        bindSwitch(document, "asr_enabled", checked ->
                this.changeAsr(checked, this.asrForm.region(), this.asrForm.autoSend()));
        bindSwitch(document, "asr_auto_send", checked ->
                this.changeAsr(this.asrForm.enabled(), this.asrForm.region(), checked));
        bind(document, "asr_region", () -> {
            List<String> regions = RecognitionConfig.REGIONS.stream().sorted().toList();
            int index = regions.indexOf(this.asrForm.region());
            this.changeAsr(this.asrForm.enabled(), regions.get((index + 1) % regions.size()), this.asrForm.autoSend());
        });
        bind(document, "settings_back", this::onClose);
        this.selectTab(this.tab);
    }

    private void fillSpeechForm(Document document) {
        document.getElementById("speech_key").setValue(this.speechForm.apiKey());
        document.getElementById("speech_workspace").setValue(this.speechForm.workspace());
        document.getElementById("speech_model").setValue(this.speechForm.model());
        document.getElementById("speech_voice").setValue(this.speechForm.voice());
        document.getElementById("speech_language").setValue(this.speechForm.language());
        document.getElementById("speech_volume").setValue(Integer.toString(this.speechForm.volume()));
        switchChecked(document.getElementById("speech_notification_sound"), this.speechForm.notificationSoundEnabled());
        switchChecked(document.getElementById("speech_cache"), this.speechForm.cacheEnabled());
        switchChecked(document.getElementById("speech_quest_requirements"), this.speechForm.questRequirements());
        switchChecked(document.getElementById("speech_quest_rewards"), this.speechForm.questRewards());
        switchChecked(document.getElementById("speech_quest_description"), this.speechForm.questDescription());
    }

    private void readSpeechForm(Document document) {
        int volume;
        try {
            volume = Integer.parseInt(document.getElementById("speech_volume").getValue().strip());
        } catch (NumberFormatException error) {
            volume = -1;
        }
        // Clearing the numeric field while typing must not block saving unrelated controls.
        this.invalidVolume = volume < 0 || volume > 100;
        if (this.invalidVolume) volume = SpeechConfig.INSTANCE.get().volume();
        this.speechForm = new SpeechConfig(this.speechForm.enabled(), this.speechForm.provider(), document.getElementById("speech_key").getValue().strip(),
                this.speechForm.region(), document.getElementById("speech_workspace").getValue().strip(), document.getElementById("speech_model").getValue().strip(),
                document.getElementById("speech_voice").getValue().strip(), document.getElementById("speech_language").getValue(), volume,
                switchChecked(document.getElementById("speech_cache")), switchChecked(document.getElementById("speech_quest_requirements")),
                switchChecked(document.getElementById("speech_quest_rewards")), switchChecked(document.getElementById("speech_quest_description")),
                switchChecked(document.getElementById("speech_notification_sound")));
    }

    private void readAsrForm(Document document) {
        this.asrForm = new RecognitionConfig(this.asrForm.enabled(), this.asrForm.provider(), document.getElementById("asr_appkey").getValue().strip(),
                document.getElementById("asr_token").getValue().strip(), this.asrForm.region(), this.asrForm.autoSend());
    }

    private void changeSpeech(boolean enabled, String region, String language) {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.readSpeechForm(document);
        this.speechForm = new SpeechConfig(enabled, this.speechForm.provider(), this.speechForm.apiKey(), region, this.speechForm.workspace(),
                this.speechForm.model(), this.speechForm.voice(), language, this.speechForm.volume(), this.speechForm.cacheEnabled(),
                this.speechForm.questRequirements(), this.speechForm.questRewards(), this.speechForm.questDescription(), this.speechForm.notificationSoundEnabled());
        this.saveSpeech();
    }

    private void changeAsr(boolean enabled, String region, boolean autoSend) {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        this.readAsrForm(document);
        this.asrForm = new RecognitionConfig(enabled, this.asrForm.provider(), this.asrForm.appKey(), this.asrForm.token(), region, autoSend);
        this.saveAsr();
    }

    private void selectTab(Tab next) {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        if (this.tab != next) this.stopSpeech();
        this.tab = next;
        for (Tab value : Tab.values()) {
            boolean selected = value == next;
            document.getElementById("settings_page_" + value.key()).setInlineStyleProperty("display", selected ? "flex" : "none");
            Element button = document.getElementById("settings_tab_" + value.key());
            button.setClassName(selected ? "settings_tab primary" : "settings_tab");
            button.setAttribute("aria-selected", Boolean.toString(selected));
        }
        document.getElementById("settings_speech_actions").setInlineStyleProperty("display", next == Tab.TTS ? "flex" : "none");
        Element content = document.getElementById("settings_content");
        content.scrollTop = 0;
        content.targetScrollTop = 0;
        this.renderControls();
        // Tab buttons stay visible while AUI applies queued styles; hidden inputs must release IME focus.
        document.getElementById("settings_tab_" + next.key()).focus();
    }

    private void savePreferences() {
        if (AssistantClient.state().busy() || this.minecraft.getConnection() == null) {
            this.saveHistory = AssistantClient.state().saveHistory();
            this.externalModel = AssistantClient.state().externalModel();
        } else if (this.saveHistory != AssistantClient.state().saveHistory() || this.externalModel != AssistantClient.state().externalModel()) {
            this.savingPreferences = true;
            AssistantClient.send(new PreferencesRequest(this.saveHistory, this.externalModel));
        }
        this.renderControls();
    }

    private void saveSpeech() {
        try {
            if (!this.speechForm.equals(SpeechConfig.INSTANCE.get()))
                AssistantSpeech.configure(this.speechForm);
            this.speechStatus = this.invalidVolume ? SpeechState.SETTINGS : null;
        } catch (Exception error) {
            this.speechStatus = error instanceof SpeechException speech ? SpeechState.fromCode(speech.code()) : SpeechState.CONFIG;
        }
        this.renderControls();
    }

    private void saveAsr() {
        try {
            if (!this.asrForm.equals(RecognitionConfig.INSTANCE.get()))
                AssistantRecognition.configure(this.asrForm);
            this.asrStatus = "saved";
        } catch (Exception error) {
            this.asrStatus = error instanceof SpeechException speech ? speech.code() : "config";
        }
        this.renderControls();
    }

    private void renderControls() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        document.getElementById("settings_mode_full").setClassName(this.simpleMode ? "" : "primary");
        document.getElementById("settings_mode_simple").setClassName(this.simpleMode ? "primary" : "");
        switchChecked(document.getElementById("history_toggle"), this.saveHistory);
        switchChecked(document.getElementById("model_toggle"), this.externalModel);
        switchChecked(document.getElementById("speech_enabled"), this.speechForm.enabled());
        switchChecked(document.getElementById("speech_notification_sound"), this.speechForm.notificationSoundEnabled());
        switchChecked(document.getElementById("speech_cache"), this.speechForm.cacheEnabled());
        text(document.getElementById("speech_region"), tr("system.tts.ui.region") + ": " + tr("system.tts.region." + this.speechForm.region()));
        Element languageSelect = document.getElementById("speech_language");
        if (!languageSelect.getValue().equals(this.speechForm.language()))
            languageSelect.setValue(this.speechForm.language());
        text(document.getElementById("speech_language_hint"), tr("system.tts.ui.language_hint"));
        for (String id : List.of("speech_workspace", "speech_model", "speech_voice", "speech_language", "speech_volume", "speech_preview"))
            document.getElementById(id).setDisabled(!this.speechForm.hasKey());
        switchChecked(document.getElementById("asr_enabled"), this.asrForm.enabled());
        text(document.getElementById("asr_region"), tr("system.asr.ui.region") + ": " + tr("system.asr.region." + this.asrForm.region()));
        switchChecked(document.getElementById("asr_auto_send"), this.asrForm.autoSend());
        this.displayedStatus = null;
    }

    @Override
    public void tick() {
        super.tick();
        Document document = this.getLinkedDocument();
        if (document == null) return;
        if (this.savingPreferences && !AssistantClient.state().busy()) {
            this.savingPreferences = false;
            AgentResult result = AssistantClient.state().current() == null ? null : AssistantClient.state().current().result();
            this.generalStatus = result == null ? tr("system.ui.settings_config_error")
                    : tr(result.messageKey().equals("system.result.preferences") ? "system.ui.settings_saved" : result.messageKey());
            this.saveHistory = AssistantClient.state().saveHistory();
            this.externalModel = AssistantClient.state().externalModel();
            this.renderControls();
        }
        // A late server state may arrive after settings opens; pending edits wait for acknowledgement.
        if (!this.savingPreferences
                && (this.saveHistory != AssistantClient.state().saveHistory() || this.externalModel != AssistantClient.state().externalModel())) {
            this.saveHistory = AssistantClient.state().saveHistory();
            this.externalModel = AssistantClient.state().externalModel();
            this.renderControls();
        }
        boolean busy = AssistantClient.state().busy() || this.minecraft.getConnection() == null;
        for (String id : List.of("history_toggle", "model_toggle")) {
            Element control = document.getElementById(id);
            if (control.isDisabled() != busy) control.setDisabled(busy);
        }
        String status = switch (this.tab) {
            case GENERAL -> this.savingPreferences ? tr("system.ui.working")
                    : this.generalStatus.isBlank() ? tr("system.ui.settings_auto_save") : this.generalStatus;
            case TTS -> tr((this.speechStatus == null ? AssistantSpeech.status() : this.speechStatus).translationKey());
            case ASR -> tr("system.asr.status." + this.asrStatus);
        };
        if (!status.equals(this.displayedStatus)) {
            this.displayedStatus = status;
            text(document.getElementById("settings_status"), status);
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        if (this.parent instanceof AssistantScreen && AssistantClient.simpleMode()
                || this.parent instanceof AssistantSimpleScreen && !AssistantClient.simpleMode())
            AssistantClient.open(AssistantClient.simpleMode(), ((AssistantInputScreen) this.parent).draft);
        else this.minecraft.setScreen(this.parent);
    }
}
