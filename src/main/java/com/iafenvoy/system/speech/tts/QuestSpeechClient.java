package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.agent.QuestNarration;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.network.payload.QuestNarrationRequestC2SPayload;
import com.iafenvoy.system.network.payload.QuestNarrationResponseS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One pending narration belongs to the current audio session; stale replies cannot restart playback.
 */
public final class QuestSpeechClient {
    private static String requestId;
    private static CompletableFuture<String> pending;

    private QuestSpeechClient() {
    }

    public static CompletableFuture<String> request(List<String> quests, SpeechConfig settings) {
        cancel();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (quests.isEmpty() || connection == null || !settings.questRequirements() && !settings.questRewards() && !settings.questDescription())
            return CompletableFuture.completedFuture("");
        requestId = UUID.randomUUID().toString();
        pending = new CompletableFuture<>();
        connection.send(new QuestNarrationRequestC2SPayload(requestId,
                new QuestNarration.Request(quests, settings.questRequirements(), settings.questRewards(), settings.questDescription())));
        return pending.completeOnTimeout("", 8, TimeUnit.SECONDS);
    }

    public static void receive(QuestNarrationResponseS2CPayload payload) {
        if (pending == null || !payload.id().equals(requestId)) return;
        pending.complete(String.join("\n", payload.reply().lines().stream().map(Component::getString).toList()));
        pending = null;
        requestId = null;
    }

    public static void cancel() {
        if (pending != null) pending.cancel(false);
        pending = null;
        requestId = null;
    }
}
