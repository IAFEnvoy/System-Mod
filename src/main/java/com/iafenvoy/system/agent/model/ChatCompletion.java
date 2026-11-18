package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.config.file.ModelConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Optional;

/**
 * Provider wire DTOs stay separate from agent decisions and workflow objects.
 */
public final class ChatCompletion {
    private ChatCompletion() {
    }

    public record Type(String type) {
        public static final Codec<Type> CODEC = Codec.STRING.fieldOf("type").xmap(Type::new, Type::type).codec();
    }

    public record Request(String model, List<ModelClient.Message> messages, boolean stream, Optional<Integer> maxTokens,
                          Optional<Integer> maxCompletionTokens, Optional<Type> responseFormat,
                          Optional<Type> thinking, Optional<String> reasoningEffort, List<ToolDefinition> tools,
                          Optional<String> toolChoice, Optional<Boolean> parallelToolCalls) {
        public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("model").forGetter(Request::model),
                ModelClient.Message.CODEC.listOf().fieldOf("messages").forGetter(Request::messages),
                Codec.BOOL.fieldOf("stream").forGetter(Request::stream),
                Codec.INT.optionalFieldOf("max_tokens").forGetter(Request::maxTokens),
                Codec.INT.optionalFieldOf("max_completion_tokens").forGetter(Request::maxCompletionTokens),
                Type.CODEC.optionalFieldOf("response_format").forGetter(Request::responseFormat),
                Type.CODEC.optionalFieldOf("thinking").forGetter(Request::thinking),
                Codec.STRING.optionalFieldOf("reasoning_effort").forGetter(Request::reasoningEffort),
                ToolDefinition.CODEC.listOf().optionalFieldOf("tools", List.of()).forGetter(Request::tools),
                Codec.STRING.optionalFieldOf("tool_choice").forGetter(Request::toolChoice),
                Codec.BOOL.optionalFieldOf("parallel_tool_calls").forGetter(Request::parallelToolCalls)
        ).apply(i, Request::new));

        public static Request of(ModelConfig config, List<ModelClient.Message> messages, List<ModelClient.Function> functions, int maxOutputTokens) {
            return of(config, messages, functions, ModelClient.Options.DEFAULT, maxOutputTokens);
        }

        public static Request of(ModelConfig config, List<ModelClient.Message> messages, List<ModelClient.Function> functions,
                                 ModelClient.Options options, int maxOutputTokens) {
            boolean deepSeek = config.isDeepSeek();
            if (options.webSearch() && (!deepSeek || !functions.isEmpty()))
                throw new IllegalArgumentException("DeepSeek server search must be isolated from structured function calls");
            return new Request(config.model(), List.copyOf(messages), false,
                    config.outputTokenParameter().equals("max_tokens") ? Optional.of(maxOutputTokens) : Optional.empty(),
                    config.outputTokenParameter().equals("max_completion_tokens") ? Optional.of(maxOutputTokens) : Optional.empty(),
                    config.jsonMode() && functions.isEmpty() && !options.webSearch() ? Optional.of(new Type("json_object")) : Optional.empty(),
                    deepSeek ? Optional.of(new Type(config.thinkingEnabled() ? "enabled" : "disabled")) : Optional.empty(),
                    deepSeek && config.thinkingEnabled() && !config.reasoningEffort().isEmpty()
                            ? Optional.of(config.reasoningEffort()) : Optional.empty(),
                    options.webSearch() ? List.of(new ToolDefinition("deepseek_web_search", Optional.empty()),
                            new ToolDefinition("deepseek_read_url", Optional.empty()))
                            : functions.stream().map(function -> new ToolDefinition("function", Optional.of(function))).toList(),
                    functions.isEmpty() ? Optional.empty() : Optional.of("required"),
                    functions.isEmpty() ? Optional.empty() : Optional.of(false));
        }
    }

    public record ToolDefinition(String type, Optional<ModelClient.Function> function) {
        public static final Codec<ToolDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(ToolDefinition::type),
                ModelClient.Function.CODEC.optionalFieldOf("function").forGetter(ToolDefinition::function)
        ).apply(i, ToolDefinition::new));
    }

    public record TokenDetails(Optional<Long> cachedTokens) {
        public static final Codec<TokenDetails> CODEC = ExtraCodecs.NON_NEGATIVE_LONG.optionalFieldOf("cached_tokens")
                .xmap(TokenDetails::new, TokenDetails::cachedTokens).codec();
    }

    public record Usage(long prompt, long completion, long total, Optional<TokenDetails> details,
                        Optional<Long> cacheHit) {
        public static final Codec<Usage> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("prompt_tokens").forGetter(Usage::prompt),
                ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("completion_tokens").forGetter(Usage::completion),
                ExtraCodecs.NON_NEGATIVE_LONG.fieldOf("total_tokens").forGetter(Usage::total),
                TokenDetails.CODEC.optionalFieldOf("prompt_tokens_details").forGetter(Usage::details),
                ExtraCodecs.NON_NEGATIVE_LONG.optionalFieldOf("prompt_cache_hit_tokens").forGetter(Usage::cacheHit)
        ).apply(i, Usage::new));
        public static final Codec<Optional<Usage>> ENVELOPE = CODEC.optionalFieldOf("usage").codec();

        public ModelClient.Usage counted() {
            return new ModelClient.Usage(true, this.prompt, this.completion, this.total,
                    this.details.flatMap(TokenDetails::cachedTokens).or(() -> this.cacheHit).orElse(0L));
        }
    }

    public record FunctionCall(String name, String arguments) {
        public static final Codec<FunctionCall> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(FunctionCall::name),
                Codec.STRING.fieldOf("arguments").forGetter(FunctionCall::arguments)
        ).apply(i, FunctionCall::new));
    }

    public record ToolCall(String id, String type, FunctionCall function) {
        public static final Codec<ToolCall> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(ToolCall::id),
                Codec.STRING.fieldOf("type").forGetter(ToolCall::type),
                FunctionCall.CODEC.fieldOf("function").forGetter(ToolCall::function)
        ).apply(i, ToolCall::new));
    }

    public record Content(Optional<String> content, List<ToolCall> toolCalls, DeepSeekWebSearch.Metadata search) {
        public static final Codec<Content> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("content").forGetter(Content::content),
                ToolCall.CODEC.listOf().optionalFieldOf("tool_calls", List.of()).forGetter(Content::toolCalls),
                DeepSeekWebSearch.Metadata.CODEC.forGetter(Content::search)
        ).apply(i, Content::new));
    }

    public record Choice(String finishReason, Content message) {
        public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("finish_reason").forGetter(Choice::finishReason),
                Content.CODEC.fieldOf("message").forGetter(Choice::message)
        ).apply(i, Choice::new));
    }

    public record Response(List<Choice> choices) {
        public static final Codec<Response> CODEC = Choice.CODEC.listOf().fieldOf("choices").xmap(Response::new, Response::choices).codec();
    }
}
