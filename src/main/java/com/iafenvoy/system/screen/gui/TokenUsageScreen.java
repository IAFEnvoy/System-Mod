package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.data.usage.RecognitionUsage;
import com.iafenvoy.system.data.usage.SpeechUsage;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.ToLongFunction;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * Read-only usage dashboard; requests never enter conversation memory or inference.
 */
public final class TokenUsageScreen extends AssistantPageScreen {
    private static final int PAGE_SIZE = TokenUsageSnapshot.PAGE_SIZE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    // Summary and player details share the same service groups and metric order.
    private static final List<MetricGroup> GROUPS = List.of(
            new MetricGroup("ai", "usage_counts", List.of(
                    new Metric("totalTokens", usage -> usage.tokens().totalTokens()),
                    new Metric("inputTokens", usage -> usage.tokens().inputTokens()),
                    new Metric("outputTokens", usage -> usage.tokens().outputTokens()),
                    new Metric("cachedInputTokens", usage -> usage.tokens().cachedInputTokens()))),
            new MetricGroup("tts", "usage_speech_counts", List.of(
                    new Metric("speechCost", usage -> usage.speech().estimatedNanoYuan()),
                    new Metric("speechCharacters", usage -> usage.speech().characters()),
                    new Metric("speechTotalTokens", usage -> usage.speech().tokens().totalTokens()),
                    new Metric("speechInputTokens", usage -> usage.speech().tokens().inputTokens()),
                    new Metric("speechOutputTokens", usage -> usage.speech().tokens().outputTokens()))),
            new MetricGroup("asr", "usage_recognition_counts", List.of(
                    new Metric("recognitionCalls", usage -> usage.recognition().calls()),
                    new Metric("recognitionCost", usage -> usage.recognition().estimatedNanoYuan()))));
    private final Screen parent;
    private TokenUsageSnapshot snapshot;
    private String error = "";
    private String requestId;
    private long requestedAt = System.currentTimeMillis();
    private boolean all;
    private int offset;

    private record Metric(String key, ToLongFunction<TokenUsageSnapshot.Usage> value) {
        private String format(TokenUsageSnapshot.Usage usage) {
            long value = this.value.applyAsLong(usage);
            return this.key.endsWith("Cost") ? "¥" + BigDecimal.valueOf(value, 9).stripTrailingZeros().toPlainString() : number(value);
        }
    }

    private record MetricGroup(String key, String countsId, List<Metric> metrics) {
    }

    public TokenUsageScreen(Screen parent, TokenUsageResponseS2CPayload initial) {
        super("system/assistant-usage.html");
        this.parent = parent;
        this.setPauseGame(false);
        this.setShowDefaultBackground(false);
        this.accept(initial);
    }

    @Override
    protected void initPage(Document document) {
        text(document.getElementById("brand"), tr("system.ui.usage_title"));
        for (String id : List.of("usage_mine", "usage_all", "usage_refresh", "usage_back", "usage_prev", "usage_next", "usage_note", "usage_speech_note", "usage_recognition_note", "close"))
            text(document.getElementById(id), tr("system.ui." + id));
        this.createSummary(document);
        bind(document, "close", this::onClose);
        bind(document, "usage_back", this::onClose);
        bind(document, "usage_mine", () -> this.request(false, 0));
        bind(document, "usage_all", () -> this.request(true, 0));
        bind(document, "usage_refresh", () -> this.request(this.all, this.offset));
        bind(document, "usage_prev", () -> this.request(this.all, Math.max(0, this.offset - PAGE_SIZE)));
        bind(document, "usage_next", () -> this.request(this.all, this.offset + PAGE_SIZE));
        this.renderSnapshot();
    }

    private void createSummary(Document document) {
        Element summary = document.getElementById("usage_summary");
        summary.setInnerHTML("");
        for (MetricGroup group : GROUPS) {
            Element section = element(document, "div", "usage_section usage_section_" + group.key(), "");
            section.appendChild(element(document, "div", "usage_section_title", tr("system.ui.usage_group_" + group.key())));
            Element metrics = element(document, "div", "usage_metrics", "");
            for (Metric metric : group.metrics()) {
                Element cell = element(document, "div", "usage_metric", "");
                cell.appendChild(element(document, "span", "usage_label", tr("system.usage." + metric.key())));
                Element value = element(document, "span", "usage_total", "—");
                value.setAttribute("id", "value_" + metric.key());
                cell.appendChild(value);
                metrics.appendChild(cell);
            }
            section.appendChild(metrics);
            Element counts = element(document, "div", "usage_counts", "");
            counts.setAttribute("id", group.countsId());
            section.appendChild(counts);
            summary.appendChild(section);
        }
    }

    private void request(boolean all, int offset) {
        long now = System.currentTimeMillis();
        if (this.requestId != null || now - this.requestedAt < 400) return;
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection == null) return;
        this.requestId = UUID.randomUUID().toString();
        this.requestedAt = now;
        this.error = "";
        connection.send(new TokenUsageRequestC2SPayload(this.requestId, all, offset));
        this.renderSnapshot();
    }

    public void receive(TokenUsageResponseS2CPayload response) {
        if (!response.id().equals(this.requestId)) return;
        this.update(response);
    }

    public void update(TokenUsageResponseS2CPayload response) {
        this.requestId = null;
        this.requestedAt = System.currentTimeMillis();
        this.accept(response);
        this.renderSnapshot();
    }

    private void accept(TokenUsageResponseS2CPayload response) {
        if (response.snapshot().isPresent()) {
            this.snapshot = response.snapshot().get();
            this.all = this.snapshot.all();
            this.offset = this.snapshot.offset();
            this.error = "";
        } else {
            this.snapshot = null;
            this.all = false;
            this.offset = 0;
            this.error = response.errorKey();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.requestId != null && System.currentTimeMillis() - this.requestedAt > 10000) {
            this.update(TokenUsageResponseS2CPayload.failure(this.requestId, "EXPIRED"));
        }
    }

    private void renderSnapshot() {
        Document document = this.getLinkedDocument();
        if (document == null) return;
        boolean loading = this.requestId != null;
        boolean canViewAll = this.snapshot != null && this.snapshot.canViewAll();
        text(document.getElementById("status"), tr(loading ? "system.ui.usage_loading" : this.error.isEmpty() ? "system.ui.ready" : this.error));
        text(document.getElementById("usage_scope"), tr(this.all ? "system.ui.usage_scope_all" : "system.ui.usage_scope_mine"));
        document.getElementById("usage_all").setInlineStyleProperty("display", canViewAll ? "block" : "none");
        document.getElementById("usage_all").setClassName(this.all ? "primary" : "");
        document.getElementById("usage_mine").setClassName(this.all ? "" : "primary");
        for (String id : List.of("usage_mine", "usage_all", "usage_refresh"))
            document.getElementById(id).setDisabled(loading);
        document.getElementById("usage_prev").setDisabled(loading || this.offset == 0);
        document.getElementById("usage_next").setDisabled(loading || this.snapshot == null || !this.snapshot.hasMore());
        for (MetricGroup group : GROUPS)
            for (Metric metric : group.metrics())
                text(document.getElementById("value_" + metric.key()), this.snapshot == null ? "—" : metric.format(this.snapshot.totals()));
        Element list = document.getElementById("usage_players");
        list.setInnerHTML("");
        if (this.snapshot == null) {
            text(document.getElementById("usage_counts"), tr(this.error.isEmpty() ? "system.ui.usage_empty" : this.error));
            text(document.getElementById("usage_speech_counts"), "");
            text(document.getElementById("usage_recognition_counts"), "");
            text(document.getElementById("usage_page"), "");
            return;
        }
        text(document.getElementById("usage_counts"), counts(this.snapshot.totals()));
        text(document.getElementById("usage_speech_counts"), speechCounts(this.snapshot.totals().speech()));
        text(document.getElementById("usage_recognition_counts"), recognitionCounts(this.snapshot.totals().recognition()));
        int playerCount = this.snapshot.playerCount();
        text(document.getElementById("usage_page"), Component.translatable("system.ui.usage_page",
                this.offset / PAGE_SIZE + 1, Math.max(1, (playerCount + PAGE_SIZE - 1) / PAGE_SIZE), playerCount).getString());
        for (TokenUsageSnapshot.Player player : this.snapshot.players()) {
            Element row = element(document, "div", "result_card usage_row", "");
            Element identity = element(document, "div", "usage_identity", "");
            identity.appendChild(element(document, "div", "card_title", player.name()));
            identity.appendChild(element(document, "div", "usage_uuid", player.player().toString()));
            long lastCall = player.lastCall();
            identity.appendChild(element(document, "div", "usage_last_call", Component.translatable("system.ui.usage_last_call",
                    lastCall == 0 ? tr("system.ui.usage_never") : TIME.format(Instant.ofEpochMilli(lastCall))).getString()));
            row.appendChild(identity);
            for (MetricGroup group : GROUPS) {
                Element section = element(document, "div", "usage_section usage_section_" + group.key(), "");
                section.appendChild(element(document, "div", "usage_section_title", tr("system.ui.usage_group_" + group.key())));
                Element metrics = element(document, "div", "usage_metrics", "");
                for (Metric field : group.metrics()) {
                    Element metric = element(document, "div", "usage_metric", "");
                    metric.appendChild(element(document, "span", "usage_label", tr("system.usage." + field.key())));
                    metric.appendChild(element(document, "span", "usage_value", field.format(player.usage())));
                    metrics.appendChild(metric);
                }
                section.appendChild(metrics);
                section.appendChild(element(document, "div", "usage_counts", switch (group.key()) {
                    case "tts" -> speechCounts(player.usage().speech());
                    case "asr" -> recognitionCounts(player.usage().recognition());
                    default -> counts(player.usage());
                }));
                row.appendChild(section);
            }
            list.appendChild(row);
        }
        if (playerCount == 0) list.appendChild(element(document, "p", "", tr("system.ui.usage_empty")));
        Element scroll = document.getElementById("usage_scroll");
        scroll.scrollTop = 0;
        scroll.targetScrollTop = 0;
    }

    private static String counts(TokenUsageSnapshot.Usage data) {
        return Component.translatable("system.ui.usage_counts", number(data.calls()), number(data.failedCalls()), number(data.unreportedCalls())).getString();
    }

    private static String speechCounts(SpeechUsage data) {
        return Component.translatable("system.ui.usage_speech_counts", number(data.calls()), number(data.failedCalls()),
                number(data.unreportedCalls()), number(data.unpricedCalls())).getString();
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String recognitionCounts(RecognitionUsage data) {
        return Component.translatable("system.ui.usage_recognition_counts", number(data.calls()), number(data.failedCalls()),
                number(data.unreportedCalls()), number(data.unpricedCalls())).getString();
    }

    @Override
    public void onClose() {
        super.onClose();
        if (this.parent != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
