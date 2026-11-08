package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

public final class PlayerViewData {
    private PlayerViewData() {
    }

    public record Profile(String name, Component displayName, Map<String, String> properties) {
        public static final Codec<Profile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Profile::name),
                ComponentSerialization.CODEC.fieldOf("display_name").forGetter(Profile::displayName),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(Profile::properties)
        ).apply(i, Profile::new));

        public Profile {
            displayName = ViewComponents.copy(displayName);
            properties = Map.copyOf(properties);
        }

        @Override
        public Component displayName() {
            return ViewComponents.copy(this.displayName);
        }
    }

    public record Experience(int level, int total, double progress) {
        public static final Codec<Experience> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("level").forGetter(Experience::level),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("total").forGetter(Experience::total),
                Codec.doubleRange(0, 1).fieldOf("progress").forGetter(Experience::progress)
        ).apply(i, Experience::new));
    }

    public record Food(int level, double saturation, double exhaustion) {
        public static final Codec<Food> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(Food::level),
                MiscCodecs.FINITE.fieldOf("saturation").forGetter(Food::saturation),
                MiscCodecs.FINITE.fieldOf("exhaustion").forGetter(Food::exhaustion)
        ).apply(i, Food::new));
    }

    public record Location(ResourceLocation dimension, double x, double y, double z, double yaw, double pitch) {
        public static final Codec<Location> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(Location::dimension),
                MiscCodecs.FINITE.fieldOf("x").forGetter(Location::x),
                MiscCodecs.FINITE.fieldOf("y").forGetter(Location::y),
                MiscCodecs.FINITE.fieldOf("z").forGetter(Location::z),
                MiscCodecs.FINITE.fieldOf("yaw").forGetter(Location::yaw),
                MiscCodecs.FINITE.fieldOf("pitch").forGetter(Location::pitch)
        ).apply(i, Location::new));
    }

    public record Resource(ResourceLocation id, double value, Optional<Double> minimum, Optional<Double> maximum,
                           long changedAt) {
        public static final Codec<Resource> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Resource::id),
                MiscCodecs.FINITE.fieldOf("value").forGetter(Resource::value),
                MiscCodecs.FINITE.optionalFieldOf("min").forGetter(Resource::minimum),
                MiscCodecs.FINITE.optionalFieldOf("max").forGetter(Resource::maximum),
                Codec.LONG.optionalFieldOf("changed_at", -1L).forGetter(Resource::changedAt)
        ).apply(i, Resource::new));
    }

    public record Effect(ResourceLocation id, int amplifier, int duration, boolean ambient, boolean visible) {
        public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Effect::id),
                Codec.INT.fieldOf("amplifier").forGetter(Effect::amplifier),
                Codec.INT.fieldOf("duration").forGetter(Effect::duration),
                Codec.BOOL.fieldOf("ambient").forGetter(Effect::ambient),
                Codec.BOOL.fieldOf("visible").forGetter(Effect::visible)
        ).apply(i, Effect::new));
    }

    public record Attribute(ResourceLocation id, double base, Optional<Double> effective) {
        public static final Codec<Attribute> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Attribute::id),
                MiscCodecs.FINITE.fieldOf("base").forGetter(Attribute::base),
                MiscCodecs.FINITE.optionalFieldOf("effective").forGetter(Attribute::effective)
        ).apply(i, Attribute::new));
    }
}
