package com.iafenvoy.system.data.speech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * Typed NLS headers and payloads; credentials remain confined to the outgoing header.
 */
public final class AliyunAsrMessage {
    private AliyunAsrMessage() {
    }

    public record Header(String appKey, String namespace, String name, String taskId, String messageId) {
        public static final Codec<Header> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("appkey").forGetter(Header::appKey),
                Codec.STRING.fieldOf("namespace").forGetter(Header::namespace),
                Codec.STRING.fieldOf("name").forGetter(Header::name),
                Codec.STRING.fieldOf("task_id").forGetter(Header::taskId),
                Codec.STRING.fieldOf("message_id").forGetter(Header::messageId)
        ).apply(i, Header::new));
    }

    public record Parameters(String format, int sampleRate, boolean intermediate, boolean punctuation,
                             boolean normalization) {
        public static final Parameters DEFAULT = new Parameters("pcm", 16000, true, true, true);
        public static final Codec<Parameters> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("format").forGetter(Parameters::format),
                Codec.INT.fieldOf("sample_rate").forGetter(Parameters::sampleRate),
                Codec.BOOL.fieldOf("enable_intermediate_result").forGetter(Parameters::intermediate),
                Codec.BOOL.fieldOf("enable_punctuation_prediction").forGetter(Parameters::punctuation),
                Codec.BOOL.fieldOf("enable_inverse_text_normalization").forGetter(Parameters::normalization)
        ).apply(i, Parameters::new));
    }

    public record Command(Header header, Optional<Parameters> payload) {
        public static final Codec<Command> CODEC = RecordCodecBuilder.create(i -> i.group(
                Header.CODEC.fieldOf("header").forGetter(Command::header),
                Parameters.CODEC.optionalFieldOf("payload").forGetter(Command::payload)
        ).apply(i, Command::new));
    }

    public record ResultHeader(int status, String namespace, String name, String taskId) {
        public static final Codec<ResultHeader> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("status").forGetter(ResultHeader::status),
                Codec.STRING.fieldOf("namespace").forGetter(ResultHeader::namespace),
                Codec.STRING.fieldOf("name").forGetter(ResultHeader::name),
                Codec.STRING.fieldOf("task_id").forGetter(ResultHeader::taskId)
        ).apply(i, ResultHeader::new));
    }

    public record Text(String result) {
        public static final Codec<Text> CODEC = Codec.string(0, 2000).optionalFieldOf("result", "").xmap(Text::new, Text::result).codec();
    }

    public record Response(ResultHeader header, Optional<Text> payload) {
        public static final Codec<Response> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResultHeader.CODEC.fieldOf("header").forGetter(Response::header),
                Text.CODEC.optionalFieldOf("payload").forGetter(Response::payload)
        ).apply(i, Response::new));
    }
}
