package com.iafenvoy.system.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Server-thread extension point for authoritative stages and progression signals.
 * Bounds from multiple listeners intersect; contradictory stages fail instead of bypassing a ceiling.
 */
public final class PlayerProgressionEvent extends Event {
    private final ServerPlayer player;
    private final double observed;
    private final int samples;
    private double scale = 1;
    private double minimum;
    private double maximum = Double.MAX_VALUE;
    private boolean calibrated;

    public PlayerProgressionEvent(ServerPlayer player, double observed, int samples) {
        this.player = player;
        this.observed = observed;
        this.samples = samples;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public double observed() {
        return this.observed;
    }

    public int samples() {
        return this.samples;
    }

    public void calibrate(double scale, double minimum, double maximum) {
        if (!Double.isFinite(scale) || scale <= 0 || !Double.isFinite(minimum) || minimum < 0
                || !Double.isFinite(maximum) || maximum < minimum || !Double.isFinite(this.scale * scale) || this.scale * scale <= 0
                || Math.max(this.minimum, minimum) > Math.min(this.maximum, maximum))
            throw new IllegalArgumentException("Invalid or conflicting player progression calibration");
        this.scale *= scale;
        this.minimum = Math.max(this.minimum, minimum);
        this.maximum = Math.min(this.maximum, maximum);
        this.calibrated = true;
    }

    public double clamp(double value) {
        return Math.clamp(value, this.minimum, this.maximum);
    }

    public double value() {
        return this.clamp(this.observed * this.scale);
    }

    public double minimum() {
        return this.minimum;
    }

    public boolean calibrated() {
        return this.calibrated;
    }
}
