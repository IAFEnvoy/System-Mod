package com.iafenvoy.system.data.speech;

import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.AgentCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** DashScope wire DTOs; optional fields preserve Qwen3 and Qwen-Audio request differences. */
public final class AliyunTtsMessage {
    private static final Map<String, String> HINTS = Map.of("Chinese", "zh", "English", "en", "German", "de",
            "Italian", "it", "Portuguese", "pt", "Spanish", "es", "Japanese", "ja", "Korean", "ko", "French", "fr", "Russian", "ru");

    private AliyunTtsMessage() {
    }

    public record Input(String text, String voice, Optional<String> format, Optional<Integer> sampleRate,
                        Optional<String> languageType, Optional<List<String>> languageHints) {
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Input::text),
                Codec.STRING.fieldOf("voice").forGetter(Input::voice),
                Codec.STRING.optionalFieldOf("format").forGetter(Input::format),
                Codec.INT.optionalFieldOf("sample_rate").forGetter(Input::sampleRate),
                Codec.STRING.optionalFieldOf("language_type").forGetter(Input::languageType),
                Codec.STRING.listOf().optionalFieldOf("language_hints").forGetter(Input::languageHints)
        ).apply(i, Input::new));
    }

    public record Request(String model, Input input) {
        public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("model").forGetter(Request::model),
                Input.CODEC.fieldOf("input").forGetter(Request::input)
        ).apply(i, Request::new));

        public static Request of(TtsProvider.Request request, String format) {
            boolean custom = request.model().startsWith("qwen-audio-");
            return new Request(request.model(), new Input(request.text(), request.voice(),
                    custom ? Optional.of(format) : Optional.empty(), custom ? Optional.of(24000) : Optional.empty(),
                    custom ? Optional.empty() : Optional.of(request.language()),
                    custom ? Optional.ofNullable(HINTS.get(request.language())).map(List::of) : Optional.empty()));
        }
    }

    public record Audio(String url, String data) {
        public static final Codec<Audio> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("url", "").forGetter(Audio::url),
                Codec.STRING.optionalFieldOf("data", "").forGetter(Audio::data)
        ).apply(i, Audio::new));
    }

    public record Output(Optional<Audio> audio, String finishReason) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                Audio.CODEC.optionalFieldOf("audio").forGetter(Output::audio),
                Codec.STRING.optionalFieldOf("finish_reason", "").forGetter(Output::finishReason)
        ).apply(i, Output::new));
    }

    public record Response(String code, Optional<Integer> statusCode, Optional<Output> output) {
        public static final Codec<Response> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("code", "").forGetter(Response::code),
                Codec.INT.optionalFieldOf("status_code").forGetter(Response::statusCode),
                Output.CODEC.optionalFieldOf("output").forGetter(Response::output)
        ).apply(i, Response::new));
    }

    public record Billing(Optional<Long> characters, Optional<Long> input, Optional<Long> output, Optional<Long> total) {
        private static final Codec<Long> COUNT = AgentCodecs.NON_NEGATIVE_LONG.validate(v -> v <= 1_000_000
                ? DataResult.success(v) : DataResult.error(() -> "Oversized billing count"));
        public static final Codec<Billing> CODEC = RecordCodecBuilder.create(i -> i.group(
                COUNT.optionalFieldOf("characters").forGetter(Billing::characters),
                COUNT.optionalFieldOf("input_tokens").forGetter(Billing::input),
                COUNT.optionalFieldOf("output_tokens").forGetter(Billing::output),
                COUNT.optionalFieldOf("total_tokens").forGetter(Billing::total)
        ).apply(i, Billing::new));
        public static final Codec<Optional<Billing>> ENVELOPE = CODEC.optionalFieldOf("usage").codec();

        public TtsProvider.Usage counted(String model) {
            if (model.equals("qwen-audio-3.1-tts-flash")) {
                long input = this.input.orElseThrow(), output = this.output.orElseThrow();
                long total = this.total.orElseGet(() -> Math.addExact(input, output));
                return total > 1_000_000 ? TtsProvider.Usage.UNKNOWN : new TtsProvider.Usage(TtsProvider.Unit.TOKENS, true, 0, input, output, total);
            }
            long characters = this.characters.orElseThrow();
            return characters > 2400 ? TtsProvider.Usage.UNKNOWN : new TtsProvider.Usage(TtsProvider.Unit.CHARACTERS, true, characters, 0, 0, 0);
        }
    }
}
