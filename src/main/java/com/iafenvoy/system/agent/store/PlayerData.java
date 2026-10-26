package com.iafenvoy.system.agent.store;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.util.AgentCodecs;
import com.iafenvoy.system.util.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/**
 * Private server records. Never send this object as a client snapshot.
 */
public final class PlayerData {
    public static final Codec<PlayerData> CODEC = ObjectCodec.of(PlayerData::new)
            .field("version", Codec.INT, v -> v.version, (v, x) -> v.version = x)
            .field("bound", Codec.BOOL, v -> v.bound, (v, x) -> v.bound = x)
            .field("memoryEpoch", Codec.LONG, v -> v.memoryEpoch, (v, x) -> v.memoryEpoch = x)
            .field("saveHistory", Codec.BOOL, v -> v.saveHistory, (v, x) -> v.saveHistory = x)
            .field("externalModel", Codec.BOOL, v -> v.externalModel, (v, x) -> v.externalModel = x)
            .field("history", Message.CODEC.listOf().xmap(ArrayList::new, List::copyOf), v -> v.history, (v, x) -> v.history = x)
            .field("requests", Codec.unboundedMap(Codec.STRING, Codec.lazyInitialized(() -> AgentResult.CODEC)).xmap(LinkedHashMap::new, value -> value), v -> v.requests, (v, x) -> v.requests = x)
            .field("requestInputs", Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(LinkedHashMap::new, value -> value), v -> v.requestInputs, (v, x) -> v.requestInputs = x)
            .field("signIns", Codec.unboundedMap(Codec.STRING, Reward.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.signIns, (v, x) -> v.signIns = x)
            .field("inbox", Codec.unboundedMap(Codec.STRING, Delivery.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.inbox, (v, x) -> v.inbox = x)
            .field("tasks", Codec.unboundedMap(Codec.STRING, EventTask.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.tasks, (v, x) -> v.tasks = x)
            .field("operations", Codec.unboundedMap(Codec.STRING, Step.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.operations, (v, x) -> v.operations = x)
            .field("purchases", Codec.unboundedMap(Codec.STRING, Purchase.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.purchases, (v, x) -> v.purchases = x)
            .field("baselines", Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf().<Set<String>>xmap(HashSet::new, List::copyOf)).xmap(LinkedHashMap::new, value -> value), v -> v.baselines, (v, x) -> v.baselines = x)
            .field("unlocked", Codec.STRING.listOf().<Set<String>>xmap(HashSet::new, List::copyOf), v -> v.unlocked, (v, x) -> v.unlocked = x)
            .field("usageDate", Codec.STRING, v -> v.usageDate, (v, x) -> v.usageDate = x)
            .field("usedModelCalls", Codec.INT, v -> v.usedModelCalls, (v, x) -> v.usedModelCalls = x)
            .codec();

    public int version = 1;
    public boolean bound;
    public long memoryEpoch;
    public boolean saveHistory = true;
    public boolean externalModel = true;
    // Disposable conversation data and request replay caches follow memory preferences and retention.
    public List<Message> history = new ArrayList<>();
    public Map<String, AgentResult> requests = new LinkedHashMap<>();
    public Map<String, String> requestInputs = new LinkedHashMap<>();
    // Business entitlements and recovery journals survive forget; revoke deletes this entire player file.
    public Map<String, Reward> signIns = new LinkedHashMap<>();
    public Map<String, Delivery> inbox = new LinkedHashMap<>();
    public Map<String, EventTask> tasks = new LinkedHashMap<>();
    public Map<String, Step> operations = new LinkedHashMap<>();
    public Map<String, Purchase> purchases = new LinkedHashMap<>();
    public Map<String, Set<String>> baselines = new LinkedHashMap<>();
    public Set<String> unlocked = new HashSet<>();
    // Daily admission quota only; cumulative billed tokens live separately in world SavedData.
    public String usageDate = "";
    public int usedModelCalls;

    public record Message(String requestId, String role, long time, String text) {
        public static final Codec<Message> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("requestId").forGetter(Message::requestId),
                Codec.STRING.fieldOf("role").forGetter(Message::role),
                Codec.LONG.fieldOf("time").forGetter(Message::time),
                Codec.STRING.fieldOf("text").forGetter(Message::text)
        ).apply(i, Message::new));
    }

    public record Item(String id, int count) {
        public static final Codec<Item> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Item::id),
                AgentCodecs.intRange(1, 64).optionalFieldOf("count").xmap(value -> value.orElse(1), Optional::of).forGetter(Item::count)
        ).apply(i, Item::new));
    }

    public record Reward(String id, String label, List<Item> items) {
        public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Reward::id),
                Codec.STRING.fieldOf("label").forGetter(Reward::label),
                Item.CODEC.listOf().fieldOf("items").forGetter(Reward::items)
        ).apply(i, Reward::new));
        public Reward {
            items = List.copyOf(items);
        }
    }

    public static final class Delivery {
        public static final Codec<Delivery> CODEC = ObjectCodec.of(Delivery::new)
                .field("id", Codec.STRING, v -> v.id, (v, x) -> v.id = x)
                .field("reward", Reward.CODEC, v -> v.reward, (v, x) -> v.reward = x)
                .field("state", Codec.STRING, v -> v.state, (v, x) -> v.state = x)
                .field("created", Codec.LONG, v -> v.created, (v, x) -> v.created = x)
                .codec();

        public String id;
        public Reward reward;
        public String state = "PENDING";
        public long created;

        public Delivery() {
        }

        public Delivery(String id, Reward reward) {
            this.id = id;
            this.reward = reward;
            this.created = System.currentTimeMillis();
        }
    }

    public boolean hasCompletedOperation(String taskId) {
        return this.operations.entrySet().stream().anyMatch(entry ->
                entry.getKey().startsWith(taskId + ":") && "DONE".equals(entry.getValue().state));
    }

    public static final class EventTask {
        public static final Codec<EventTask> CODEC = ObjectCodec.of(EventTask::new)
                .field("id", Codec.STRING, v -> v.id, (v, x) -> v.id = x)
                .field("key", Codec.STRING, v -> v.key, (v, x) -> v.key = x)
                .field("moduleVersion", Codec.INT, v -> v.moduleVersion, (v, x) -> v.moduleVersion = x)
                .inline(ResultTypes.ACTIONS.mapCodec(), v -> v.call, (v, x) -> v.call = x)
                .field("ruleId", Codec.STRING, v -> v.ruleId, (v, x) -> v.ruleId = x)
                .field("policy", ExecutionPolicy.CODEC, v -> v.policy, (v, x) -> v.policy = x)
                .field("created", Codec.LONG, v -> v.created, (v, x) -> v.created = x)
                .field("expires", Codec.LONG, v -> v.expires, (v, x) -> v.expires = x)
                .field("state", Codec.STRING, v -> v.state, (v, x) -> v.state = x)
                .field("modelCalls", Codec.INT, v -> v.modelCalls, (v, x) -> v.modelCalls = x)
                .field("toolCalls", Codec.INT, v -> v.toolCalls, (v, x) -> v.toolCalls = x)
                .field("workflowSteps", Codec.INT, v -> v.workflowSteps, (v, x) -> v.workflowSteps = x)
                .field("writeSteps", Codec.INT, v -> v.writeSteps, (v, x) -> v.writeSteps = x)
                .field("tokens", Codec.INT, v -> v.tokens, (v, x) -> v.tokens = x)
                .field("result", Codec.lazyInitialized(() -> AgentResult.CODEC), v -> v.result, (v, x) -> v.result = x)
                .codec();

        public String id;
        public String key;
        public String ruleId = "";
        public int moduleVersion;
        public TypedPayload<?> call;
        public ExecutionPolicy policy;
        public long created;
        public long expires;
        public String state = "QUEUED";
        public int modelCalls;
        public int toolCalls;
        public int workflowSteps;
        public int writeSteps;
        public int tokens;
        public AgentResult result;

        public boolean pending() {
            return Set.of("QUEUED", "RUNNING", "WAITING").contains(this.state);
        }

        public void complete(AgentResult result) {
            this.state = result.messageKey().equals("system.error.player_offline") ? "WAITING" : result.status();
            this.result = result;
        }
    }

    public static final class Step {
        public static final Codec<Step> CODEC = ObjectCodec.of(Step::new)
                .field("state", Codec.STRING, v -> v.state, (v, x) -> v.state = x)
                .field("fingerprint", Codec.STRING, v -> v.fingerprint, (v, x) -> v.fingerprint = x)
                .field("result", Codec.PASSTHROUGH, v -> v.result, (v, x) -> v.result = x)
                .codec();

        public String state = "RUNNING";
        public String fingerprint;
        public Dynamic<?> result;
    }

    public static final class Purchase {
        public static final Codec<Purchase> CODEC = ObjectCodec.of(Purchase::new)
                .field("id", Codec.STRING, v -> v.id, (v, x) -> v.id = x)
                .field("offer", CommerceCatalog.Offer.CODEC, v -> v.offer, (v, x) -> v.offer = x)
                .field("state", Codec.STRING, v -> v.state, (v, x) -> v.state = x)
                .field("error", Codec.STRING, v -> v.error, (v, x) -> v.error = x)
                .field("created", Codec.LONG, v -> v.created, (v, x) -> v.created = x)
                .codec();

        public String id;
        public CommerceCatalog.Offer offer;
        public String state = "PREPARED";
        public String error = "";
        public long created;
    }
}

