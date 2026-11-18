package com.iafenvoy.system.agent.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Authority is either global, a real definition, or an explicitly declared set of merge members.
 */
public sealed interface ModuleScope {
    Codec<ModuleScope> CODEC = Codec.withAlternative(Codec.STRING.validate(type -> switch (type) {
        case "global", "definition", "group" -> DataResult.success(type);
        default -> DataResult.error(() -> "Unknown module scope " + type);
    }).dispatch("type", scope -> switch (scope) {
        case Global ignored -> "global";
        case Definition ignored -> "definition";
        case Group ignored -> "group";
    }, type -> switch (type) {
        case "global" -> MapCodec.unit(Global.INSTANCE);
        case "definition" -> Definition.CODEC;
        case "group" -> Group.CODEC;
        default -> throw new IllegalArgumentException("Unknown module scope " + type);
    }), Identifier.CODEC.xmap(Definition::new, Definition::id));

    boolean available(Predicate<Identifier> enabled);

    static ModuleScope of(Identifier owner) {
        return owner == null ? Global.INSTANCE : new Definition(owner);
    }

    enum Global implements ModuleScope {
        INSTANCE;

        @Override
        public boolean available(Predicate<Identifier> enabled) {
            return true;
        }
    }

    record Definition(Identifier id) implements ModuleScope {
        public static final MapCodec<Definition> CODEC = Identifier.CODEC.fieldOf("id").xmap(Definition::new, Definition::id);

        public Definition {
            Objects.requireNonNull(id);
        }

        @Override
        public boolean available(Predicate<Identifier> enabled) {
            return enabled.test(this.id);
        }
    }

    record Group(Identifier id, List<Identifier> members) implements ModuleScope {
        public static final MapCodec<Group> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Group::id),
                Identifier.CODEC.listOf().fieldOf("members").forGetter(Group::members)
        ).apply(i, Group::new));

        public Group {
            Objects.requireNonNull(id);
            members = members.stream().distinct().sorted().toList();
            if (members.isEmpty()) throw new IllegalArgumentException("Empty module merge group " + id);
        }

        @Override
        public boolean available(Predicate<Identifier> enabled) {
            return this.members.stream().anyMatch(enabled);
        }
    }
}
