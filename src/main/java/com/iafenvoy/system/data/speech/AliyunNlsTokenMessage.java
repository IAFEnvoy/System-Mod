package com.iafenvoy.system.data.speech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * NLS CreateToken wire data; ExpireTime is an absolute Unix timestamp in seconds.
 */
public final class AliyunNlsTokenMessage {
    private AliyunNlsTokenMessage() {
    }

    public record Token(String id, long expireTime) {
        public static final Codec<Token> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("Id").forGetter(Token::id),
                Codec.LONG.fieldOf("ExpireTime").forGetter(Token::expireTime)
        ).apply(i, Token::new));

        @Override
        public @NotNull String toString() {
            return "NlsToken[expireTime=" + this.expireTime + "]";
        }
    }

    public record Response(Optional<Token> token, String code) {
        public static final Codec<Response> CODEC = RecordCodecBuilder.create(i -> i.group(
                Token.CODEC.optionalFieldOf("Token").forGetter(Response::token),
                Codec.STRING.optionalFieldOf("Code", "").forGetter(Response::code)
        ).apply(i, Response::new));
    }
}
