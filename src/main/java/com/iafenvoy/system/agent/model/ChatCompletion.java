package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.util.ExtraCodecsCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
                          Optional<Type> thinking, Optional<String> reasoningEffort) {
        public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("model").forGetter(Request::model),
                ModelClient.Message.CODEC.listOf().fieldOf("messages").forGetter(Request::messages),
                Codec.BOOL.fieldOf("stream").forGetter(Request::stream),
                Codec.INT.optionalFieldOf("max_tokens").forGetter(Request::maxTokens),
                Codec.INT.optionalFieldOf("max_completion_tokens").forGetter(Request::maxCompletionTokens),
                Type.CODEC.optionalFieldOf("response_format").forGetter(Request::responseFormat),
                Type.CODEC.optionalFieldOf("thinking").forGetter(Request::thinking),
                Codec.STRING.optionalFieldOf("reasoning_effort").forGetter(Request::reasoningEffort)
        ).apply(i, Request::new));

        public static Request of(List<ModelClient.Message> messages, int maxOutputTokens) {
            boolean deepSeek = ModelConfig.INSTANCE.get().isDeepSeek();
            return new Request(ModelConfig.INSTANCE.get().model(), List.copyOf(messages), false,
                    ModelConfig.INSTANCE.get().outputTokenParameter().equals("max_tokens") ? Optional.of(maxOutputTokens) : Optional.empty(),
                    ModelConfig.INSTANCE.get().outputTokenParameter().equals("max_completion_tokens") ? Optional.of(maxOutputTokens) : Optional.empty(),
                    ModelConfig.INSTANCE.get().jsonMode() ? Optional.of(new Type("json_object")) : Optional.empty(),
                    deepSeek ? Optional.of(new Type(ModelConfig.INSTANCE.get().thinkingEnabled() ? "enabled" : "disabled")) : Optional.empty(),
                    deepSeek && ModelConfig.INSTANCE.get().thinkingEnabled() && !ModelConfig.INSTANCE.get().reasoningEffort().isEmpty()
                            ? Optional.of(ModelConfig.INSTANCE.get().reasoningEffort()) : Optional.empty());
        }
    }

    public record TokenDetails(Optional<Long> cachedTokens) {
        public static final Codec<TokenDetails> CODEC = ExtraCodecsCompat.NON_NEGATIVE_LONG.optionalFieldOf("cached_tokens")
                .xmap(TokenDetails::new, TokenDetails::cachedTokens).codec();
    }

    public record Usage(long prompt, long completion, long total, Optional<TokenDetails> details,
                        Optional<Long> cacheHit) {
        public static final Codec<Usage> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecsCompat.NON_NEGATIVE_LONG.fieldOf("prompt_tokens").forGetter(Usage::prompt),
                ExtraCodecsCompat.NON_NEGATIVE_LONG.fieldOf("completion_tokens").forGetter(Usage::completion),
                ExtraCodecsCompat.NON_NEGATIVE_LONG.fieldOf("total_tokens").forGetter(Usage::total),
                TokenDetails.CODEC.optionalFieldOf("prompt_tokens_details").forGetter(Usage::details),
                ExtraCodecsCompat.NON_NEGATIVE_LONG.optionalFieldOf("prompt_cache_hit_tokens").forGetter(Usage::cacheHit)
        ).apply(i, Usage::new));
        public static final Codec<Optional<Usage>> ENVELOPE = CODEC.optionalFieldOf("usage").codec();

        public ModelClient.Usage counted() {
            return new ModelClient.Usage(true, this.prompt, this.completion, this.total,
                    this.details.flatMap(TokenDetails::cachedTokens).or(() -> this.cacheHit).orElse(0L));
        }
    }

    public record Content(String content) {
        public static final Codec<Content> CODEC = Codec.STRING.fieldOf("content").xmap(Content::new, Content::content).codec();
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
