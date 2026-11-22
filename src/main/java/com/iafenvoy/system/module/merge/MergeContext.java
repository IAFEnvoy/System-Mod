package com.iafenvoy.system.module.merge;

import com.iafenvoy.assistant.registry.ModuleScope;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.module.ModuleContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable membership, with permissions always read from the manager's current ready-module snapshot.
 */
public final class MergeContext<C> {
    private final ModuleContext services;
    private final List<Member<C>> members;
    private final ModuleScope.Group scope;

    public MergeContext(ModuleContext services, MergeType<C> type, List<Member<C>> members) {
        this.services = services;
        this.members = List.copyOf(members);
        for (Member<C> member : members) type.contributionType().cast(member.contribution());
        this.scope = new ModuleScope.Group(type.id(), members.stream().map(member -> HolderHelper.id(member.definition())).toList());
        if (this.scope.members().size() != members.size()) throw new IllegalArgumentException("Duplicate merge member");
        if (members.stream().flatMap(member -> member.definition().value().client().stream()).distinct().count() > 1)
            throw new IllegalArgumentException("Conflicting client implementations for merge group " + type.id());
    }

    public ModuleContext services() {
        return this.services;
    }

    public ModuleScope.Group scope() {
        return this.scope;
    }

    public List<Member<C>> enabled(UUID player) {
        return this.members.stream().filter(member -> this.services.engine().moduleEnabled(player, member.definition())).toList();
    }

    public Member<C> require(UUID player, Identifier definition) {
        return this.enabled(player).stream().filter(member -> HolderHelper.id(member.definition()).equals(definition)).findFirst()
                .orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
    }

    public record Member<C>(Holder<SystemModule> definition, C contribution) {
        public Member {
            Objects.requireNonNull(definition);
            Objects.requireNonNull(contribution);
        }
    }
}
