package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.expression.builtin.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SaturnInformationExpressions {
    public static final DeferredRegister<MapCodec<? extends InformationExpression>> REGISTRY = DeferredRegister.create(SaturnRegistries.INFORMATION_EXPRESSION_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("constant", () -> ConstantExpression.CODEC);
        REGISTRY.register("getter", () -> GetterExpression.CODEC);
        REGISTRY.register("local", () -> LocalExpression.CODEC);
        REGISTRY.register("number", () -> NumberExpression.CODEC);
        REGISTRY.register("text", () -> TextExpression.CODEC);
        REGISTRY.register("boolean", () -> BooleanExpression.CODEC);
        REGISTRY.register("items", () -> ItemsExpression.CODEC);
        REGISTRY.register("meter", () -> MeterExpression.CODEC);
        REGISTRY.register("translate", () -> TranslateExpression.CODEC);
        REGISTRY.register("registry_name", () -> RegistryNameExpression.CODEC);
        REGISTRY.register("format", () -> FormatExpression.CODEC);
        REGISTRY.register("arithmetic", () -> ArithmeticExpression.CODEC);
        REGISTRY.register("compare", () -> CompareExpression.CODEC);
        REGISTRY.register("logic", () -> LogicExpression.CODEC);
        REGISTRY.register("is_known", () -> IsKnownExpression.CODEC);
        REGISTRY.register("if", () -> ConditionalExpression.CODEC);
        REGISTRY.register("coalesce", () -> CoalesceExpression.CODEC);
        REGISTRY.register("list", () -> ListExpression.CODEC);
        REGISTRY.register("table", () -> TableExpression.CODEC);
    }

    private SaturnInformationExpressions() {
    }
}
