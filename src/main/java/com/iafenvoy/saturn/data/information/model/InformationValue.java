package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Map;

public sealed interface InformationValue {
    Codec<InformationValue> CODEC = Codec.recursive("PlayerInformationValue", self -> Type.CODEC.dispatch("type", InformationValue::type, type -> codec(type, self)));

    Type type();

    enum Type {
        TEXT, NUMBER, BOOLEAN, METER, MISSING, ITEMS, LIST, TABLE;
        public static final Codec<Type> CODEC = ExtraEnumCodecs.enumCodec(Type.class);
    }

    private static MapCodec<? extends InformationValue> codec(Type type, Codec<InformationValue> self) {
        return switch (type) {
            case TEXT -> Text.CODEC;
            case NUMBER -> Number.CODEC;
            case BOOLEAN -> Flag.CODEC;
            case METER -> Meter.CODEC;
            case MISSING -> Missing.CODEC;
            case ITEMS -> Items.CODEC;
            case LIST -> RecordCodecBuilder.<Values>mapCodec(i -> i.group(
                    self.listOf().fieldOf("values").forGetter(Values::values)
            ).apply(i, Values::new));
            case TABLE -> RecordCodecBuilder.<Table>mapCodec(i -> i.group(
                    Column.CODEC.listOf().fieldOf("columns").forGetter(Table::columns),
                    Codec.STRING.listOf().fieldOf("row_ids").forGetter(Table::rowIds),
                    self.listOf().listOf().fieldOf("rows").forGetter(Table::rows)
            ).apply(i, Table::new));
        };
    }

    record Text(Component text) implements InformationValue {
        public static final MapCodec<Text> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Text::text)
        ).apply(i, Text::new));

        public Text {
            text = ViewComponents.copy(text);
        }

        @Override
        public Component text() {
            return ViewComponents.copy(this.text);
        }

        @Override
        public Type type() {
            return Type.TEXT;
        }
    }

    record Number(double value) implements InformationValue {
        public static final MapCodec<Number> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MiscCodecs.FINITE.fieldOf("value").forGetter(Number::value)
        ).apply(i, Number::new));

        public Number {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Information number must be finite");
        }

        @Override
        public Type type() {
            return Type.NUMBER;
        }
    }

    record Flag(boolean value) implements InformationValue {
        public static final MapCodec<Flag> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("value").forGetter(Flag::value)
        ).apply(i, Flag::new));

        @Override
        public Type type() {
            return Type.BOOLEAN;
        }
    }

    record Meter(double value, double minimum, double maximum) implements InformationValue {
        public static final MapCodec<Meter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MiscCodecs.FINITE.fieldOf("value").forGetter(Meter::value),
                MiscCodecs.FINITE.fieldOf("min").forGetter(Meter::minimum),
                MiscCodecs.FINITE.fieldOf("max").forGetter(Meter::maximum)
        ).apply(i, Meter::new));

        public Meter {
            if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum)
                throw new IllegalArgumentException("Invalid information meter");
        }

        public double fraction() {
            return this.maximum == this.minimum ? 1 : Math.clamp((this.value - this.minimum) / (this.maximum - this.minimum), 0, 1);
        }

        @Override
        public Type type() {
            return Type.METER;
        }
    }

    record Missing(MissingReason reason) implements InformationValue {
        public static final MapCodec<Missing> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MissingReason.CODEC.fieldOf("reason").forGetter(Missing::reason)
        ).apply(i, Missing::new));

        @Override
        public Type type() {
            return Type.MISSING;
        }
    }

    record Items(Map<String, PlayerViewItem> items) implements InformationValue {
        public static final MapCodec<Items> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.unboundedMap(Codec.STRING, PlayerViewItem.CODEC).fieldOf("items").forGetter(Items::items)
        ).apply(i, Items::new));

        public Items {
            items = Map.copyOf(items);
        }

        @Override
        public Type type() {
            return Type.ITEMS;
        }
    }

    record Values(List<InformationValue> values) implements InformationValue {
        public Values {
            values = List.copyOf(values);
        }

        @Override
        public Type type() {
            return Type.LIST;
        }
    }

    record Column(String id, Component label) {
        public static final Codec<Column> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Column::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Column::label)
        ).apply(i, Column::new));

        public Column {
            label = ViewComponents.copy(label);
        }

        @Override
        public Component label() {
            return ViewComponents.copy(this.label);
        }
    }

    record Table(List<Column> columns, List<String> rowIds,
                 List<List<InformationValue>> rows) implements InformationValue {
        public Table {
            columns = List.copyOf(columns);
            rowIds = List.copyOf(rowIds);
            rows = rows.stream().map(List::copyOf).toList();
            int width = columns.size();
            if (rowIds.size() != rows.size() || rowIds.stream().distinct().count() != rowIds.size()
                    || rows.stream().anyMatch(row -> row.size() != width))
                throw new IllegalArgumentException("Invalid information table shape");
        }

        @Override
        public Type type() {
            return Type.TABLE;
        }
    }
}
