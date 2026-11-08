package com.iafenvoy.saturn.data.information;

import com.iafenvoy.saturn.data.information.model.InformationEntry;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared text projection for commands, simple cards and the default screen; numeric values remain in the model.
 */
public final class InformationText {
    private InformationText() {
    }

    public static List<Component> lines(InformationEntry row) {
        List<Component> values = lines(row.value());
        List<Component> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++)
            result.add(i == 0
                    ? Component.translatable("info.saturn.row", row.label(), values.get(i)) : values.get(i));
        return List.copyOf(result);
    }

    public static List<Component> lines(InformationValue value) {
        return switch (value) {
            case InformationValue.Text text -> List.of(text.text());
            case InformationValue.Number number -> List.of(Component.literal(number(number.value())));
            case InformationValue.Flag flag -> List.of(Component.translatable(flag.value() ? "gui.yes" : "gui.no"));
            case InformationValue.Meter meter ->
                    List.of(Component.translatable("info.saturn.meter", number(meter.value()), number(meter.maximum())));
            case InformationValue.Missing missing -> List.of(missing.reason().text());
            case InformationValue.Items items ->
                    items.items().isEmpty() ? empty() : items.items().entrySet().stream().sorted(Map.Entry.comparingByKey()).<Component>map(entry -> Component.translatable("info.saturn.item_entry", entry.getKey(), entry.getValue().name(), entry.getValue().count())).toList();
            case InformationValue.Values values ->
                    values.values().isEmpty() ? empty() : values.values().stream().flatMap(child -> lines(child).stream()).toList();
            case InformationValue.Table table -> {
                if (table.rows().isEmpty()) yield empty();
                List<Component> result = new ArrayList<>();
                for (List<InformationValue> cells : table.rows()) {
                    MutableComponent line = Component.empty();
                    for (int column = 0; column < cells.size(); column++) {
                        if (column > 0) line.append(Component.translatable("info.saturn.separator"));
                        line.append(Component.translatable("info.saturn.row", table.columns().get(column).label(), joined(lines(cells.get(column)))));
                    }
                    result.add(line);
                }
                yield List.copyOf(result);
            }
        };
    }

    private static Component joined(List<Component> lines) {
        MutableComponent result = Component.empty();
        for (Component line : lines) {
            if (!result.getSiblings().isEmpty()) result.append(Component.translatable("info.saturn.separator"));
            result.append(line);
        }
        return result;
    }

    private static List<Component> empty() {
        return List.of(Component.translatable("info.saturn.empty"));
    }

    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0E15 ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.2f", value);
    }
}
