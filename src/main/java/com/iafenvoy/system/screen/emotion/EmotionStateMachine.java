package com.iafenvoy.system.screen.emotion;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Per-card state, independent of DOM lifetime. Speech is an overlay and preserves the chosen emotion.
 * All mutation runs on the client thread; a sample advances at most one timeout transition.
 */
public final class EmotionStateMachine {
    private Identifier profileId;
    private final String initial;
    private EmotionProfile profile;
    private String state;
    private long entered;
    private boolean speaking;
    private long speechStarted;
    private @Nullable Boolean speakingOverride;

    public EmotionStateMachine(Identifier profileId, String initial) {
        this.profileId = profileId;
        this.initial = initial;
        this.state = initial;
    }

    private void refresh() {
        EmotionProfile loaded = EmotionProfiles.get(this.profileId);
        if (loaded == this.profile) return;
        this.profile = loaded;
        if (loaded != null && !loaded.states().containsKey(this.state)) {
            EmotionProfiles.unknown(this.profileId, this.state);
            this.state = loaded.defaultState();
        }
        this.entered = System.nanoTime();
    }

    public boolean setState(String state) {
        this.refresh();
        if (this.profile != null && state.isBlank()) state = this.profile.defaultState();
        if (this.profile == null || !this.profile.states().containsKey(state)) return false;
        this.state = state;
        this.entered = System.nanoTime();
        return true;
    }

    public Identifier profileId() {
        return this.profileId;
    }

    public boolean setProfile(Identifier profileId) {
        if (!EmotionProfiles.all().containsKey(profileId)) return false;
        this.profileId = profileId;
        this.profile = null;
        this.refresh();
        return true;
    }

    public String state() {
        this.refresh();
        return this.state;
    }

    public boolean signal(String event) {
        this.refresh();
        if (this.profile == null) return false;
        String target = this.profile.states().get(this.state).transitions().get(event);
        if (target == null) target = this.profile.transitions().get(event);
        return this.setState(target == null ? event : target);
    }

    /**
     * A null override resumes automatic speech tracking.
     */
    public void setSpeaking(@Nullable Boolean speaking) {
        this.speakingOverride = speaking;
    }

    public void reset() {
        this.speakingOverride = null;
        this.speaking = false;
        this.speechStarted = System.nanoTime();
        this.refresh();
        if (!this.setState(this.initial) && this.profile != null) this.setState(this.profile.defaultState());
    }

    public EmotionFrame frame(boolean automaticSpeech) {
        this.refresh();
        if (this.profile == null) return EmotionProfiles.missing(this.profileId, null);
        long now = System.nanoTime();
        boolean speaking = this.speakingOverride == null ? automaticSpeech : this.speakingOverride;
        if (speaking != this.speaking) {
            this.speaking = speaking;
            this.speechStarted = now;
            this.signal(speaking ? "speech_started" : "speech_finished");
        }
        EmotionProfile.State definition = this.profile.states().get(this.state);
        if (definition.duration() > 0 && now - this.entered >= definition.duration() * 1_000_000L)
            this.setState(definition.next().isBlank() ? this.profile.defaultState() : definition.next());
        definition = this.profile.states().get(this.state);
        if (!speaking || definition.speaking().isEmpty())
            return EmotionProfiles.frame(this.profileId, this.state, definition.texture());
        int frame = (int) ((now - this.speechStarted) / (Math.max(1, definition.frameTime()) * 1_000_000L)
                % definition.speaking().size());
        return EmotionProfiles.frame(this.profileId, this.state, definition.speaking().get(frame));
    }

    public @Nullable EmotionProfile.State definition() {
        this.refresh();
        return this.profile == null ? null : this.profile.states().get(this.state);
    }
}
