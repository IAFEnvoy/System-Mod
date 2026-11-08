package com.iafenvoy.saturn.data.information.expression.builtin;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.ViewComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.*;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;
import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.entries;

public record TableExpression(InformationExpression source, String as, Optional<InformationExpression> rowId,
                              List<TableColumn> columns) implements InformationExpression {
    public static final MapCodec<TableExpression> CODEC = RecordCodecBuilder.<TableExpression>mapCodec(i -> i.group(
            InformationExpression.CODEC.fieldOf("source").forGetter(TableExpression::source),
            Codec.STRING.optionalFieldOf("as", "item").forGetter(TableExpression::as),
            InformationExpression.CODEC.optionalFieldOf("row_id").forGetter(TableExpression::rowId),
            TableColumn.CODEC.listOf().fieldOf("columns").forGetter(TableExpression::columns)
    ).apply(i, TableExpression::new)).validate(table -> table.columns.stream().map(TableColumn::id).distinct().count() == table.columns.size()
            ? DataResult.success(table) : DataResult.error(() -> "Duplicate table column id"));

    public TableExpression {
        columns = List.copyOf(columns);
    }

    @Override
    public MapCodec<TableExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        List<String> ids = new ArrayList<>();
        Set<String> uniqueIds = new HashSet<>();
        List<List<InformationValue>> rows = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : entries(context.evaluate(this.source))) {
            InformationEvaluation child = context.withLocal(this.as, entry.getValue(), entry.getKey());
            String id = this.rowId.map(value -> child.evaluate(value).decode(Codec.STRING)).orElse(entry.getKey());
            if (!uniqueIds.add(id)) return new ExpressionValue.Missing(MissingReason.INVALID);
            ids.add(id);
            rows.add(this.columns.stream().map(column -> child.evaluate(column.value()).information()).toList());
        }
        return new ExpressionValue.Display(new InformationValue.Table(this.columns.stream()
                .map(column -> new InformationValue.Column(column.id(), column.label())).toList(), ids, rows));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        List<InformationExpression> all = new ArrayList<>(this.columns.stream().map(TableColumn::value).toList());
        all.add(this.source);
        this.rowId.ifPresent(all::add);
        return collectGetters(all);
    }

    public record TableColumn(String id, Component label, InformationExpression value) {
        public static final Codec<TableColumn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(TableColumn::id),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(TableColumn::label),
                InformationExpression.CODEC.fieldOf("value").forGetter(TableColumn::value)
        ).apply(i, TableColumn::new));

        public TableColumn {
            label = ViewComponents.copy(label);
        }

        @Override
        public Component label() {
            return ViewComponents.copy(this.label);
        }
    }
}
