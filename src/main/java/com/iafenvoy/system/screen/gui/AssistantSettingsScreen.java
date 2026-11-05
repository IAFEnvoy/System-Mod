package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.request.PreferencesRequest;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.screen.AssistantThemes;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.iafenvoy.system.speech.asr.RecognitionCapture;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.speech.tts.SpeechState;
import com.sighs.apricityui.element.ThemeSwitcher;
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
        for (Tab value : Tab.values())
            bind(document, "settings_tab_" + value.key(), () -> this.selectTab(value));
        document.getElementById("settings_theme").addEventListener(ThemeSwitcher.THEME_CHANGE_EVENT, event -> {
            if (event.detail instanceof String theme) {
                this.generalStatus = tr(AssistantThemes.select(theme) ? "system.ui.settings_saved" : "system.ui.settings_config_error");
                this.renderControls();
            }
        });
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
        document.getElementById("asr_access_key_id").setValue(this.asrForm.accessKeyId());
        document.getElementById("asr_access_key_secret").setValue(this.asrForm.accessKeySecret());
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
                document.getElementById("asr_access_key_id").getValue().strip(), document.getElementById("asr_access_key_secret").getValue().strip(), this.asrForm.region(), this.asrForm.autoSend());
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
        this.asrForm = new RecognitionConfig(enabled, this.asrForm.provider(), this.asrForm.appKey(), this.asrForm.accessKeyId(), this.asrForm.accessKeySecret(), region, autoSend);
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
            boolean saved = this.saveHistory == AssistantClient.state().saveHistory()
                    && this.externalModel == AssistantClient.state().externalModel();
            this.generalStatus = tr(saved ? "system.ui.settings_saved" : "system.ui.settings_config_error");
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
