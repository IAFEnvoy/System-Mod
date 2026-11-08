package com.iafenvoy.system.data.store;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public final class EventTask {
    public static final Codec<EventTask> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
            Codec.STRING.optionalFieldOf("key").forGetter(v -> Optional.ofNullable(v.key)),
            ResourceLocation.CODEC.optionalFieldOf("owner").forGetter(v -> v.owner),
            Codec.INT.optionalFieldOf("moduleVersion", 0).forGetter(v -> v.moduleVersion),
            AgentAction.CODEC.fieldOf("action").forGetter(v -> v.call),
            Codec.STRING.optionalFieldOf("ruleId", "").forGetter(v -> v.ruleId),
            ExecutionPolicy.CODEC.optionalFieldOf("policy").forGetter(v -> Optional.ofNullable(v.policy)),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created),
            Codec.LONG.optionalFieldOf("expires", 0L).forGetter(v -> v.expires),
            State.CODEC.optionalFieldOf("state", State.QUEUED).forGetter(v -> v.state),
            Codec.INT.optionalFieldOf("modelCalls", 0).forGetter(v -> v.modelCalls),
            Codec.INT.optionalFieldOf("toolCalls", 0).forGetter(v -> v.toolCalls),
            Codec.INT.optionalFieldOf("workflowSteps", 0).forGetter(v -> v.workflowSteps),
            Codec.INT.optionalFieldOf("writeSteps", 0).forGetter(v -> v.writeSteps),
            Codec.INT.optionalFieldOf("tokens", 0).forGetter(v -> v.tokens),
            AgentResult.CODEC.optionalFieldOf("result").forGetter(v -> Optional.ofNullable(v.result))
    ).apply(i, EventTask::new));
    public String id, key, ruleId = "";
    public Optional<ResourceLocation> owner = Optional.empty();
    public int moduleVersion;
    public AgentAction call;
    public ExecutionPolicy policy;
    public long created, expires;
    public State state = State.QUEUED;
    public int modelCalls, toolCalls, workflowSteps, writeSteps, tokens;
    public AgentResult result;

    public EventTask() {
    }

    private EventTask(Optional<String> id, Optional<String> key, Optional<ResourceLocation> owner, int moduleVersion, AgentAction call, String ruleId,
                      Optional<ExecutionPolicy> policy, long created, long expires, State state, int modelCalls,
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
        return switch (this.state) {
            case QUEUED, RUNNING, WAITING -> true;
            default -> false;
        };
    }

    public void complete(AgentResult result) {
        this.state = result.messageKey().equals("system.error.player_offline") ? State.WAITING : switch (result.status()) {
            case SUCCEEDED -> State.SUCCEEDED;
            case FAILED -> State.FAILED;
            case PARTIALLY_SUCCEEDED -> State.PARTIALLY_SUCCEEDED;
        };
        this.result = result;
    }

    public EventTask copy() {
        return new EventTask(Optional.ofNullable(this.id), Optional.ofNullable(this.key), this.owner, this.moduleVersion, this.call,
                this.ruleId, Optional.ofNullable(this.policy), this.created, this.expires, this.state, this.modelCalls,
                this.toolCalls, this.workflowSteps, this.writeSteps, this.tokens, Optional.ofNullable(this.result));
    }
}
