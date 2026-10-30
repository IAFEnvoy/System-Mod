package com.iafenvoy.system.agent.store;

import com.iafenvoy.saturn.data.storage.DataStorageHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.commerce.CommerceOrder;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;

/**
 * Private server records. Never send this object as a client snapshot.
 */
public final class PlayerData {
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.lenientOptionalFieldOf("version", 1).forGetter(v -> v.version),
            SystemBindings.CODEC.optionalFieldOf("systems", SystemBindings.EMPTY).forGetter(v -> v.systems),
            Codec.BOOL.lenientOptionalFieldOf("saveHistory", true).forGetter(v -> v.saveHistory),
            Codec.BOOL.lenientOptionalFieldOf("externalModel", true).forGetter(v -> v.externalModel),
            Conversation.CODEC.forGetter(v -> v.conversation),
            DataStorageHolder.codec(Identifier.CODEC).fieldOf("moduleData").forGetter(v -> v.moduleData),
            ModelQuota.CODEC.forGetter(v -> v.modelQuota)
    ).apply(i, PlayerData::new));
    public int version;
    public boolean saveHistory, externalModel;
    public SystemBindings systems;
    public final Conversation conversation;
    public final DataStorageHolder<Identifier> moduleData;
    public final ModelQuota modelQuota;

    public PlayerData() {
        this(1, SystemBindings.EMPTY, true, true, new Conversation(), new DataStorageHolder<>(), new ModelQuota());
    }

    private PlayerData(int version, SystemBindings systems, boolean saveHistory, boolean externalModel, Conversation conversation,
                       DataStorageHolder<Identifier> moduleData, ModelQuota modelQuota) {
        this.version = version;
        this.systems = systems;
        this.saveHistory = saveHistory;
        this.externalModel = externalModel;
        this.conversation = conversation;
        this.moduleData = moduleData;
        this.modelQuota = modelQuota;
    }

    public PlayerData copy() {
        return new PlayerData(this.version, this.systems, this.saveHistory, this.externalModel,
                new Conversation(this.conversation.memoryEpoch, this.conversation.history, this.conversation.requests, this.conversation.requestInputs),
                this.moduleData.copy(), new ModelQuota(this.modelQuota.usageDate, this.modelQuota.usedModelCalls));
    }

    public boolean bound() {
        return this.systems.bound();
    }

    /**
     * Disposable memory and replay caches share one lifetime; inline codecs keep their stored field names.
     */
    public static final class Conversation {
        public static final MapCodec<Conversation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.LONG.optionalFieldOf("memoryEpoch", 0L).forGetter(v -> v.memoryEpoch),
                Message.CODEC.listOf().lenientOptionalFieldOf("history", List.of()).forGetter(v -> v.history),
                // Recover entries independently; operation journals and business data remain strict.
                CodecIO.recoveringMap(Codec.STRING, Codec.lazyInitialized(() -> AgentResult.CODEC),
                                count -> SystemMod.LOGGER.warn("Discarded {} invalid cached agent results", count))
                        .lenientOptionalFieldOf("requests", Map.of()).forGetter(v -> v.requests),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("requestInputs", Map.of()).forGetter(v -> v.requestInputs)
        ).apply(i, Conversation::new));
        public long memoryEpoch;
        public final List<Message> history;
        public final Map<String, AgentResult> requests;
        public final Map<String, String> requestInputs;

        public Conversation() {
            this(0, List.of(), Map.of(), Map.of());
        }

        private Conversation(long memoryEpoch, List<Message> history, Map<String, AgentResult> requests, Map<String, String> requestInputs) {
            this.memoryEpoch = memoryEpoch;
            // Codec defaults may be immutable; each player owns fresh mutable collections.
            this.history = new ArrayList<>(history);
            this.requests = new LinkedHashMap<>(requests);
            this.requestInputs = new LinkedHashMap<>(requestInputs);
        }

        public void clear() {
            this.memoryEpoch++;
            this.history.clear();
            this.requests.clear();
            this.requestInputs.clear();
        }
    }

    /**
     * Daily admission counts only; cumulative billed usage remains in world SavedData.
     */
    public static final class ModelQuota {
        public static final MapCodec<ModelQuota> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("usageDate", "").forGetter(v -> v.usageDate),
                Codec.INT.optionalFieldOf("usedModelCalls", 0).forGetter(v -> v.usedModelCalls)
        ).apply(i, ModelQuota::new));
        public String usageDate;
        public int usedModelCalls;

        public ModelQuota() {
            this("", 0);
        }

        private ModelQuota(String usageDate, int usedModelCalls) {
            this.usageDate = usageDate;
            this.usedModelCalls = usedModelCalls;
        }
    }

    public record Message(String requestId, String role, long time, String text) {
        public static final Codec<Message> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("requestId").forGetter(Message::requestId),
                Codec.STRING.fieldOf("role").forGetter(Message::role),
                Codec.LONG.fieldOf("time").forGetter(Message::time),
                Codec.STRING.fieldOf("text").forGetter(Message::text)
        ).apply(i, Message::new));
    }

    public record Reward(Identifier id, String label, List<ItemStackTemplate> items) {
        // Delivery reserves one main-inventory slot per template before inserting any items.
        public static final Codec<List<ItemStackTemplate>> ITEMS_CODEC = ItemStackTemplate.CODEC.listOf(1, Inventory.INVENTORY_SIZE);
        public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Reward::id),
                Codec.STRING.fieldOf("label").forGetter(Reward::label),
                ITEMS_CODEC.fieldOf("items").forGetter(Reward::items)
        ).apply(i, Reward::new));

        public Reward {
            items = List.copyOf(items);
        }
    }

    public static final class Delivery {
        public static final Codec<Delivery> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
                Reward.CODEC.optionalFieldOf("reward").forGetter(v -> Optional.ofNullable(v.reward)),
                Codec.STRING.optionalFieldOf("state", "PENDING").forGetter(v -> v.state),
                Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created)
        ).apply(i, Delivery::new));
        public String id;
        public Reward reward;
        public String state = "PENDING";
        public long created;

        public Delivery() {
        }

        private Delivery(Optional<String> id, Optional<Reward> reward, String state, long created) {
            this.id = id.orElse(null);
            this.reward = reward.orElse(null);
            this.state = state;
            this.created = created;
        }

        public Delivery(String id, Reward reward) {
            this.id = id;
            this.reward = reward;
            this.created = System.currentTimeMillis();
        }

        public Delivery copy() {
            Delivery copy = new Delivery();
            copy.id = this.id;
            copy.reward = this.reward;
            copy.state = this.state;
            copy.created = this.created;
            return copy;
        }
    }

    public static final class EventTask {
        public static final Codec<EventTask> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
                Codec.STRING.optionalFieldOf("key").forGetter(v -> Optional.ofNullable(v.key)),
                Identifier.CODEC.optionalFieldOf("owner").forGetter(v -> v.owner),
                Codec.INT.optionalFieldOf("moduleVersion", 0).forGetter(v -> v.moduleVersion),
                AgentAction.CODEC.fieldOf("action").forGetter(v -> v.call),
                Codec.STRING.optionalFieldOf("ruleId", "").forGetter(v -> v.ruleId),
                ExecutionPolicy.CODEC.optionalFieldOf("policy").forGetter(v -> Optional.ofNullable(v.policy)),
                Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created),
                Codec.LONG.optionalFieldOf("expires", 0L).forGetter(v -> v.expires),
                Codec.STRING.optionalFieldOf("state", "QUEUED").forGetter(v -> v.state),
                Codec.INT.optionalFieldOf("modelCalls", 0).forGetter(v -> v.modelCalls),
                Codec.INT.optionalFieldOf("toolCalls", 0).forGetter(v -> v.toolCalls),
                Codec.INT.optionalFieldOf("workflowSteps", 0).forGetter(v -> v.workflowSteps),
                Codec.INT.optionalFieldOf("writeSteps", 0).forGetter(v -> v.writeSteps),
                Codec.INT.optionalFieldOf("tokens", 0).forGetter(v -> v.tokens),
                Codec.lazyInitialized(() -> AgentResult.CODEC).optionalFieldOf("result").forGetter(v -> Optional.ofNullable(v.result))
        ).apply(i, EventTask::new));
        public String id, key, ruleId = "";
        public Optional<Identifier> owner = Optional.empty();
        public int moduleVersion;
        public AgentAction call;
        public ExecutionPolicy policy;
        public long created, expires;
        public String state = "QUEUED";
        public int modelCalls, toolCalls, workflowSteps, writeSteps, tokens;
        public AgentResult result;

        public EventTask() {
        }

        private EventTask(Optional<String> id, Optional<String> key, Optional<Identifier> owner, int moduleVersion, AgentAction call, String ruleId,
                          Optional<ExecutionPolicy> policy, long created, long expires, String state, int modelCalls,
                          int toolCalls, int workflowSteps, int writeSteps, int tokens, Optional<AgentResult> result) {
            this.id = id.orElse(null);
            this.key = key.orElse(null);
            this.owner = owner;
            this.moduleVersion = moduleVersion;
            this.call = call;
            this.ruleId = ruleId;
            this.policy = policy.orElse(null);
            this.created = created;
            this.expires = expires;
            this.state = state;
            this.modelCalls = modelCalls;
            this.toolCalls = toolCalls;
            this.workflowSteps = workflowSteps;
            this.writeSteps = writeSteps;
            this.tokens = tokens;
            this.result = result.orElse(null);
        }

        public boolean pending() {
            return Set.of("QUEUED", "RUNNING", "WAITING").contains(this.state);
        }

        public void complete(AgentResult result) {
            this.state = result.messageKey().equals("system.error.player_offline") ? "WAITING" : result.status().name();
            this.result = result;
        }

        public EventTask copy() {
            return new EventTask(Optional.ofNullable(this.id), Optional.ofNullable(this.key), this.owner, this.moduleVersion, this.call,
                    this.ruleId, Optional.ofNullable(this.policy), this.created, this.expires, this.state, this.modelCalls,
                    this.toolCalls, this.workflowSteps, this.writeSteps, this.tokens, Optional.ofNullable(this.result));
        }
    }

    public static final class Step {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("state", "RUNNING").forGetter(v -> v.state),
                Codec.STRING.optionalFieldOf("fingerprint").forGetter(v -> Optional.ofNullable(v.fingerprint)),
                Codec.PASSTHROUGH.optionalFieldOf("result").forGetter(v -> Optional.ofNullable(v.result))
        ).apply(i, Step::new));
        public String state = "RUNNING", fingerprint;
        public Dynamic<?> result;

        public Step() {
        }

        private Step(String state, Optional<String> fingerprint, Optional<Dynamic<?>> result) {
            this.state = state;
            this.fingerprint = fingerprint.orElse(null);
            this.result = result.orElse(null);
        }

        public Step copy() {
            Dynamic<?> resultCopy = this.result == null ? null :
                    new Dynamic<>(JsonOps.INSTANCE, this.result.convert(JsonOps.INSTANCE).getValue().deepCopy());
            return new Step(this.state, Optional.ofNullable(this.fingerprint), Optional.ofNullable(resultCopy));
        }
    }

    public static final class Purchase {
        public static final Codec<Purchase> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
                CommerceOrder.CODEC.fieldOf("order").forGetter(v -> v.order),
                Codec.STRING.optionalFieldOf("state", "PREPARED").forGetter(v -> v.state),
                Codec.STRING.optionalFieldOf("error", "").forGetter(v -> v.error),
                Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created)
        ).apply(i, Purchase::new));
        public String id;
        public CommerceOrder order;
        public String state = "PREPARED", error = "";
        public long created;

        public Purchase() {
        }

        private Purchase(Optional<String> id, CommerceOrder order, String state, String error, long created) {
            this.id = id.orElse(null);
            this.order = order;
            this.state = state;
            this.error = error;
            this.created = created;
        }

        public Purchase copy() {
            return new Purchase(Optional.ofNullable(this.id), this.order, this.state, this.error, this.created);
        }
    }
}

